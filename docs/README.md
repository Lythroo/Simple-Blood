# Simple Blood for mod developers

Simple Blood gives every mob blood. If your mod adds mobs, you can choose what they bleed:
the colour, what comes out (blood, bone dust, embers, sparks...), how much, and whether it
sticks to blocks. A few lines of code in your client setup are enough.

```java
SimpleBloodAPI.registerEntityBlood("yourmod:cave_jelly", new SimpleBloodAPI.BloodSettings()
        .setColor(0x3FA9F5)   // blue blood
        .setGlows(true));     // that glows in the dark
```

## Start here

| Page | What's in it |
|---|---|
| [1. Getting started](1-getting-started.md) | Add Simple Blood to your project and give your first mob blood, step by step. |
| [2. Blood settings](2-blood-settings.md) | Every option you can set, with its default. |
| [3. Blood kinds](3-blood-kinds.md) | Blood, bone, metal, wood, embers, sparks... which one fits your mob. |
| [4. Effects on demand](4-effects.md) | Play a hit, a death burst or paint blood from your own code. |
| [5. Examples](5-examples.md) | Ready-to-copy setups for common kinds of mobs. |
| [6. FAQ and troubleshooting](6-faq.md) | Nothing happens? Upgrading from `bloodmod`? Start here. |

## Good to know

- **Client side only.** Simple Blood runs on the player's computer. There is nothing to do
  on the server, and your mod does not need Simple Blood on the server either.
- **Optional.** Players without Simple Blood can still play your mod. You check whether it
  is installed before you call it ([how](1-getting-started.md#3-only-call-it-when-simple-blood-is-installed)).
- **Same code everywhere.** The API is `com.simpleblood.SimpleBloodAPI` on Fabric and
  NeoForge, Minecraft 1.21.1 to 26.3.
- **You don't have to.** Mobs you don't register still bleed: plain red blood, or the
  settings of the vanilla mob with the same name (`yourmod:zombie` bleeds like a zombie).
- **No code?** Modpack makers can set the same basics in the config file instead
  ([how](6-faq.md#can-i-do-this-without-code)).
