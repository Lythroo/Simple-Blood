# 1. Getting started

[Docs home](README.md) · **Getting started** · [Settings](2-blood-settings.md) · [Kinds](3-blood-kinds.md) · [Effects](4-effects.md) · [Examples](5-examples.md) · [FAQ](6-faq.md)

By the end of this page, one of your mobs bleeds the colour you picked. It takes four steps.

## 1. Add Simple Blood to your build

Simple Blood is on the Modrinth Maven. Add the repository to `build.gradle`:

```groovy
repositories {
    maven { url = "https://api.modrinth.com/maven" }
}
```

Then add the dependency. Use the **version ID** for your loader and Minecraft version from the
table below. Version IDs are needed because the Fabric and NeoForge files share version
numbers.

```groovy
dependencies {
    // NeoForge (any version) or Fabric on 26.x:
    compileOnly "maven.modrinth:simple-blood:<version-id>"
}
```

```groovy
dependencies {
    // Fabric on 1.21.1 or 1.21.11 (Loom remaps it):
    modCompileOnly "maven.modrinth:simple-blood:<version-id>"
}
```

For example, Fabric on 26.3 is `compileOnly "maven.modrinth:simple-blood:HTlG80ot"`.

Version IDs for Simple Blood 1.0.2:

| Minecraft | Fabric | NeoForge |
|---|---|---|
| 26.3 | `HTlG80ot` | `Fm91OjUM` |
| 26.2 | `UtJLgyfv` | `KRTDlEC8` |
| 26.1.2 | `28RYWSBD` | `Gib4oEAB` |
| 26.1.1 | `rFDGUsjT` | `iKPCIzt1` |
| 26.1 | `icy24nYI` | `wcCyIuvq` |
| 1.21.11 | `Pclyid0i` | `CLIUrbZZ` |
| 1.21.1 | `SBz2rpdV` | `ZZyb2dUQ` |

For newer releases, open the file on the
[Modrinth versions page](https://modrinth.com/mod/simple-blood/versions) and copy the ID
from the "Metadata" section.

`compileOnly` means your mod can use the API without bundling it, and players without
Simple Blood can still play. To see the blood in your dev client, also add the same line
with `localRuntime` (NeoForge) or `modLocalRuntime` (Fabric).

## 2. Mark it as optional

Tell the loader that Simple Blood is optional, so your mod loads with or without it.

**Fabric** (`fabric.mod.json`):

```json
"suggests": {
  "simpleblood": "*"
}
```

**NeoForge** (`neoforge.mods.toml`):

```toml
[[dependencies.yourmod]]
modId = "simpleblood"
type = "optional"
versionRange = "[1.0.2,)"
ordering = "NONE"
side = "CLIENT"
```

## 3. Only call it when Simple Blood is installed

Put every Simple Blood call in **a class of its own**. Your code only loads that class after
checking the mod is there. If Simple Blood is missing, the class never loads and nothing
crashes.

```java
// MyBlood.java - the only class that touches Simple Blood
import com.simpleblood.SimpleBloodAPI;

public final class MyBlood {
    public static void register() {
        SimpleBloodAPI.registerEntityBlood("yourmod:cave_jelly", new SimpleBloodAPI.BloodSettings()
                .setColor(0x3FA9F5));
    }
}
```

## 4. Call it from your client setup

Simple Blood is client side, so register from your **client** initializer.

**Fabric**: your `ClientModInitializer` (the `"client"` entrypoint):

```java
public class YourModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("simpleblood")) {
            MyBlood.register();
        }
    }
}
```

**NeoForge**: a class that only loads on the client:

```java
@Mod(value = "yourmod", dist = Dist.CLIENT)
public class YourModClient {
    public YourModClient(IEventBus modBus) {
        if (ModList.get().isLoaded("simpleblood")) {
            MyBlood.register();
        }
    }
}
```

That's it. Start the game, hit your mob, and it bleeds blue.

## Ways to name your mob

All three do the same thing. Use whichever is easiest in your code.

```java
SimpleBloodAPI.registerEntityBlood("yourmod:cave_jelly", settings);              // a string
SimpleBloodAPI.registerEntityBlood(YourEntities.CAVE_JELLY, settings);           // an EntityType
SimpleBloodAPI.registerEntityBlood(Identifier.parse("yourmod:cave_jelly"), settings); // an Identifier
```

On Minecraft 1.21.1, the last one takes a `ResourceLocation` instead of an `Identifier`. The
string version works the same everywhere.

## Next

- See everything you can change: [2. Blood settings](2-blood-settings.md)
- Pick what your mob is made of: [3. Blood kinds](3-blood-kinds.md)
