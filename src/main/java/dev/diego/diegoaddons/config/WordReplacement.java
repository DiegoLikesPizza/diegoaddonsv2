package dev.diego.diegoaddons.config;

/** One find/replace pair for chat and item text. Plain Gson data object. */
public class WordReplacement {
    public String from = "";
    public String to = "";
    public boolean enabled = true;

    public WordReplacement() {
    }

    public WordReplacement(String from, String to) {
        this.from = from;
        this.to = to;
    }
}
