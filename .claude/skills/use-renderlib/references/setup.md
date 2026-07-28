# Consumer setup

## Supported line

| Component | Version                  |
| --- |--------------------------|
| RenderLib | `2.1.4`                  |
| Minecraft | `26.1.2`                 |
| Java | `25`                     |
| Fabric Loader | `0.19.3` or newer        |
| Mappings | official Minecraft names |

RenderLib is client-only and targets this exact Minecraft line.

## Gradle

Add the repository with a content filter:

```groovy
repositories {
    maven {
        name = "RenderLib"
        url = "https://renderlib.ifallious.com/maven"
        content { includeGroup "com.render" }
    }
}

dependencies {
    implementation(include("com.render:render-lib:2.1.4"))
}
```

Declare the runtime requirement:

```json
{
  "depends": {
    "minecraft": "~26.1.2",
    "java": ">=25",
    "fabricloader": ">=0.19.3",
    "render-lib": ">=2.1.4"
  }
}
```

Gradle includes the dependency in development runs. Install RenderLib alongside the consuming mod for a normal client.

## Initialization

Register the consuming mod's screens, HUD layouts, render callbacks, or screen extensions from its client initializer. Do not call `RenderLibGui.bootstrap()`, `RenderLibHud.bootstrap()`, `RenderLibWorld.bootstrap()`, or other RenderLib bootstrap methods.

Keep returned `RenderRegistration` values when a feature may be disabled:

```java
public final class ExampleClient implements ClientModInitializer {
    private RenderRegistration hud;

    @Override
    public void onInitializeClient() {
        hud = RenderLibHud.register(ExampleHud.create());
    }
}
```

Call `close()` when the owning feature unloads.
