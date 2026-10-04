# 6. FAQ and troubleshooting

[Docs home](README.md) · [Getting started](1-getting-started.md) · [Settings](2-blood-settings.md) · [Kinds](3-blood-kinds.md) · [Effects](4-effects.md) · [Examples](5-examples.md) · **FAQ**

## My settings do nothing

Check these in order:

1. **Is the id right?** It must match the registered entity id exactly, including your mod id:
   `"yourmod:cave_jelly"`, not `"cave_jelly"`. Passing the `EntityType` avoids typos.
2. **Did the call run?** Simple Blood writes a line to the log for every registration:
   `Registered custom blood settings for entity: yourmod:cave_jelly`. No line means your code
   never reached it.
3. **Is it the client side?** Register from your client initializer, not your common or
   server one.
4. **Is blood switched on?** Players can turn blood off, or turn single mobs off, in the
   Simple Blood settings (`/simpleblood`).

## In what order are settings used?

For each mob, Simple Blood uses the first one that applies:

1. What you register through `SimpleBloodAPI`.
2. The `moddedEntities` section of the player's config file.
3. The vanilla mob with the same name (`yourmod:zombie` uses the zombie's settings).
4. Plain red liquid blood.

The player's general settings (amount, size, puddles, sounds) still apply on top of all of these.

## Does my mod need Simple Blood?

No. Use `compileOnly`, mark it optional, and keep the calls in their own class
([how](1-getting-started.md)). Without Simple Blood, your mod works exactly as before.

## Does it need to be on the server?

No. Simple Blood runs only on the player's computer and works on any server, even one
without it. There is no server API.

## When should I register?

Once, when your mod starts on the client. Registrations last until the game closes.
Registering the same mob again replaces its old settings.

## Can my code crash the game through Simple Blood?

No. The API never throws into your code. If something inside Simple Blood fails, it is
logged, the player sees a chat message with a "Copy error" button, and the call returns
`false` or `null`. A full report goes to `logs/simpleblood-error.txt`. If a part keeps
failing, it switches itself off until the next world or `/simpleblood reset`.

A `setColorProvider` or `setBleedPredicate` that throws is caught too. Simple Blood then
uses the static colour, or its normal rules.

## I'm upgrading from `bloodmod` (before 1.0.2)

Simple Blood 1.0.2 renamed everything. The API itself works the same.

| Before | Now |
|---|---|
| Mod id `bloodmod` | `simpleblood` |
| `com.bloodmod.BloodModAPI` | `com.simpleblood.SimpleBloodAPI` |
| `BloodKind` (old package) | `com.simpleblood.BloodKind` |
| `/bloodmod` | `/simpleblood` |

Update your imports and the mod id in your `isModLoaded` / `isLoaded` check.

## Can I do this without code?

Yes, for the basics. Modpack makers can add mobs to `config/simpleblood.json`, under
`moddedEntities` → `customEntities`:

```json
"moddedEntities": {
  "customEntities": {
    "yourmod:cave_jelly": {
      "enabled": true,
      "canDripAtLowHealth": true,
      "transformToStains": true,
      "bloodColor": 4172277,
      "kind": "liquid",
      "glows": true
    }
  }
}
```

- `bloodColor` is a plain number, not hex. `0x3FA9F5` is `4172277`. Most calculators
  convert hex to decimal.
- `kind` is one of `liquid`, `bone`, `metal`, `wood`, `ember`, `spirit`, `wind`, `powder`,
  `debris`.

Close the game before editing the file. Anything registered through the API wins over the
config.

## Commands that help while testing

| Command | What it does |
|---|---|
| `/simpleblood` | Opens the settings |
| `/simpleblood clear` | Wipes all blood from blocks |
| `/simpleblood reset` | Turns parts back on that switched off after an error |

## Still stuck?

Open an issue on [GitHub](https://github.com/Lythroo/Simple-Blood/issues) with your code and
the log.
