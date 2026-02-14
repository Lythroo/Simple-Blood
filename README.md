<a href="https://modrinth.com/mod/simple-blood">
  <img src="https://img.shields.io/badge/Available_on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white">
</a>

# Simple Blood

**Simple Blood** adds a bunch of particle effects to Minecraft. When entities take damage, they bleed proportionally to the damage dealt - weak hits produce small, brief splashes while heavy hits create dramatic blood bursts. Entities also drip blood when at low health, and larger death bursts occur when they're killed.

---

Fully configurable through [Mod Menu](https://modrinth.com/mod/modmenu)

---

## Blood Burst
![Blood Burst](https://cdn.modrinth.com/data/cached_images/f606522c9f886ca6ec566d95c46b2bc0fd6017e1.gif)

---

## Blood Drips
![Blood Drips](https://cdn.modrinth.com/data/cached_images/720c7e2f12125fca2cf18e166325236776a82ae6.gif)

---

## Underwater Blood Clouds
![Underwater Blood Clouds](https://cdn.modrinth.com/data/cached_images/d00651f88689ca7888662f4d68c9474edb35e124.gif)

---

## For Mod Devs

Simple Blood provides an API to customize blood behavior for your mod's entities:

```java
import com.bloodmod.BloodModAPI;
import net.minecraft.util.Identifier;

// In your mod's initialization:
BloodModAPI.registerEntityBlood(
    Identifier.of("yourmod", "custom_mob"),
    new BloodModAPI.BloodSettings()
        .setColor(0xFF0000)              // Custom blood color
        .setCanBleed(true)               // Enable/disable bleeding
        .setCanDripAtLowHealth(true)     // Low health dripping
        .setTransformToStains(true)      // Stains/fog transformation
);
```

**Examples:**
- Custom blood colors for different creature types
- Disable dripping for constructs/golems (they bleed on hit, but don't continuously drip)
- Disable bleeding entirely for ghosts/spectral entities
- Keep particles as debris instead of blood stains for robots/machines

---

## Requirements
- Minecraft **1.21.11**
- [Fabric API](https://modrinth.com/mod/fabric-api)
