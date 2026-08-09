package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ModFiles;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Plays sound files the user drops into {@code config/diegoaddons/sounds/}.
 *
 * <p>Not through the game's sound engine, which cannot be given a file at runtime: it plays OGG
 * Vorbis out of loaded resource packs and nothing else, so anything here would need a pack and a
 * resource reload before it could be heard. Everything is decoded to raw PCM instead and pushed
 * through Java's own audio output, which means a file works the moment it is dropped in.
 *
 * <p>Three formats, for three different reasons:
 * <ul>
 *   <li><b>MP3</b> - what people actually have. Java has no decoder for it, so JLayer is bundled.</li>
 *   <li><b>WAV / AIFF / AU</b> - {@code javax.sound} reads these itself.</li>
 *   <li><b>OGG</b> - decoded with STB Vorbis, which ships with the game's own LWJGL.</li>
 * </ul>
 *
 * <p>Volume follows the game's master slider, because a sound the mod plays should go quiet when you
 * turn the game down - it is coming out of a different mixer, and nothing else would connect the two.
 */
public final class CustomSounds {

    /** Extensions offered in the picker, in the order they are looked for. */
    private static final List<String> EXTENSIONS = List.of(".mp3", ".ogg", ".wav", ".aiff", ".au");

    /** How long a file may be. Long enough for any notification, short enough to hold decoded. */
    private static final int MAX_SECONDS = 30;

    /** Decoded once per file, keyed by file name. Cleared when the folder is re-listed. */
    private static final Map<String, Clip> CACHE = new ConcurrentHashMap<>();

    /** Raw 16-bit PCM and the format to play it at. */
    private record Clip(byte[] pcm, AudioFormat format) {
    }

    private CustomSounds() {
    }

    /**
     * The playable files in the folder, by name with extension ({@code chime.mp3}).
     *
     * <p>Read from disk on every call rather than cached: this is what the picker shows, and a file
     * dropped in while the game is running is the whole point.
     */
    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(ModFiles.sounds())) {
            files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(CustomSounds::playable)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(out::add);
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not list the sounds folder", e);
        }
        // A file that was removed should stop being held in memory, and one that was replaced
        // under the same name should be re-read rather than played from the old bytes.
        CACHE.keySet().removeIf(name -> !out.contains(name));
        return out;
    }

    private static boolean playable(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /** Whether a name still refers to a file that is there. */
    public static boolean exists(String name) {
        return name != null && !name.isBlank() && playable(name)
                && Files.isRegularFile(ModFiles.sounds().resolve(name));
    }

    /**
     * Plays one of the files, off the game thread.
     *
     * <p>Silently does nothing if the file has gone - a sound that cannot be found is not worth
     * interrupting whatever the player is doing for, and the module's caller has already decided the
     * moment is right for a sound rather than a message.
     *
     * @param name   file name inside the sounds folder
     * @param volume 0..1, multiplied by the game's master volume
     */
    public static void play(String name, float volume) {
        if (!exists(name)) {
            return;
        }
        float master = 1f;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        }
        float gain = Math.clamp(volume, 0f, 1f) * master;
        if (gain <= 0f) {
            return;
        }
        // Decoding reads a file and playing blocks for the length of the sound, neither of which
        // belongs on the render thread. Daemon, so a sound still playing can never hold the game open.
        Thread t = new Thread(() -> playBlocking(name, gain), "DiegoAddons Sound");
        t.setDaemon(true);
        t.start();
    }

    private static void playBlocking(String name, float gain) {
        try {
            Clip clip = CACHE.computeIfAbsent(name, CustomSounds::decode);
            if (clip == null || clip.pcm().length == 0) {
                return;
            }
            byte[] data = amplify(clip.pcm(), gain);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, clip.format());
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(clip.format());
                line.start();
                line.write(data, 0, data.length);
                line.drain();
            }
        } catch (Exception e) {
            // Includes the case of no audio device at all, which is a normal state for a machine
            // and not something to fail loudly over every time a reminder fires.
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not play {}: {}", name, e.toString());
        }
    }

    /**
     * Scales 16-bit samples by {@code gain}.
     *
     * <p>Done on the samples rather than through the line's {@code MASTER_GAIN} control, which is
     * optional - plenty of mixers do not offer it, and asking for one that is not there throws in the
     * middle of playing rather than simply being loud.
     */
    private static byte[] amplify(byte[] pcm, float gain) {
        if (gain >= 0.999f) {
            return pcm;
        }
        byte[] out = new byte[pcm.length];
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            short s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int scaled = Math.clamp(Math.round(s * gain), Short.MIN_VALUE, Short.MAX_VALUE);
            out[i] = (byte) (scaled & 0xFF);
            out[i + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
        return out;
    }

    /** Decodes a file to 16-bit PCM, or null if nothing here can read it. */
    private static Clip decode(String name) {
        Path file = ModFiles.sounds().resolve(name);
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".mp3")) {
                return decodeMp3(file);
            }
            if (lower.endsWith(".ogg")) {
                return decodeOgg(file);
            }
            return decodeJavaSound(file);
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not decode {}: {}", name, e.toString());
            return null;
        }
    }

    /** WAV, AIFF and AU, which {@code javax.sound} decodes itself. */
    private static Clip decodeJavaSound(Path file) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat source = in.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(), 16, source.getChannels(),
                    source.getChannels() * 2, source.getSampleRate(), false);
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm, in)) {
                return new Clip(read(converted, pcm), pcm);
            }
        }
    }

    private static byte[] read(AudioInputStream in, AudioFormat format) throws IOException {
        int cap = (int) (format.getSampleRate() * format.getFrameSize() * MAX_SECONDS);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() < cap) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * MP3, through JLayer.
     *
     * <p>Frame by frame: each decoded frame hands back 16-bit samples and its own header, and the
     * sample rate and channel count are taken from the first one - they cannot change mid-file in
     * anything a player would produce, and a file where they did would be unplayable here anyway.
     */
    private static Clip decodeMp3(Path file) throws Exception {
        try (var stream = Files.newInputStream(file)) {
            Bitstream bits = new Bitstream(stream);
            Decoder decoder = new Decoder();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int rate = 0;
            int channels = 0;
            int cap = Integer.MAX_VALUE;
            Header header;
            while ((header = bits.readFrame()) != null && out.size() < cap) {
                SampleBuffer buf = (SampleBuffer) decoder.decodeFrame(header, bits);
                if (rate == 0) {
                    rate = buf.getSampleFrequency();
                    channels = buf.getChannelCount();
                    cap = rate * channels * 2 * MAX_SECONDS;
                }
                short[] samples = buf.getBuffer();
                for (int i = 0; i < buf.getBufferLength(); i++) {
                    out.write(samples[i] & 0xFF);
                    out.write((samples[i] >> 8) & 0xFF);
                }
                bits.closeFrame();
            }
            if (rate == 0) {
                return null;
            }
            return new Clip(out.toByteArray(), format(rate, channels));
        }
    }

    /**
     * OGG Vorbis, through the STB decoder that comes with the game's LWJGL.
     *
     * <p>The whole file goes in and the whole waveform comes out, both in native memory, so both are
     * freed by hand - {@code stb_vorbis_decode_memory} allocates the result with STB's own allocator
     * and nothing else will ever release it.
     */
    private static Clip decodeOgg(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
        try {
            encoded.put(bytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer channels = stack.mallocInt(1);
                IntBuffer rate = stack.mallocInt(1);
                ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(encoded, channels, rate);
                if (pcm == null) {
                    return null;
                }
                try {
                    int max = rate.get(0) * channels.get(0) * MAX_SECONDS;
                    int count = Math.min(pcm.limit(), max);
                    ByteBuffer bb = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < count; i++) {
                        bb.putShort(pcm.get(i));
                    }
                    return new Clip(bb.array(), format(rate.get(0), channels.get(0)));
                } finally {
                    MemoryUtil.memFree(pcm);
                }
            }
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }

    private static AudioFormat format(int rate, int channels) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels,
                channels * 2, rate, false);
    }
}
