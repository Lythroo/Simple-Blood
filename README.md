<div align="center">

<a href="https://modrinth.com/mod/simple-blood">
  <img src="https://img.shields.io/badge/Available_on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white">
</a>

<br>
<br>

![# Simple Blood](https://cdn.modrinth.com/data/cached_images/b6e93d6ab422e767e10e6a3e2207b244d603cd1e.png)

<br>

<img src="https://img.shields.io/badge/Mod_Loader-Fabric-dbd0b4?style=for-the-badge" alt="Fabric">
<img src="https://img.shields.io/badge/Environment-Client-9b59b6?style=for-the-badge" alt="Client">
<a href="https://modrinth.com/mod/modmenu"><img src="https://img.shields.io/badge/Config-Mod_Menu-1bd96a?style=for-the-badge" alt="Mod Menu"></a>
<img src="https://img.shields.io/github/issues/Lythroo/Simple-Blood?style=for-the-badge&logo=github" alt="Issues">

<br>
<br>
<br>

*Adds simple blood particles.*

<br>

When entities take damage, they bleed proportionally to the amount dealt. Weak hits create small, short splashes, while heavy hits result in dramatic blood bursts. Entities at low health slowly drip blood, and killing blows trigger larger death bursts.

<br>

[Report an Issue](https://github.com/Lythroo/Simple-Blood/issues) • [Suggest a Feature](https://github.com/Lythroo/Simple-Blood/issues)

</div>

<br>

<div align="center">

## Features

</div>


<div align="center">

```
The gallery GIFs below are currently outdated. Updated previews will be added soon.
```

### Blood Burst
Damage-scaled particle effects that respond to hit intensity.

<img src="https://cdn.modrinth.com/data/cached_images/f606522c9f886ca6ec566d95c46b2bc0fd6017e1.gif" alt="Blood Burst">

</div>

<br>

<div align="center">

### Blood Drips
Low-health entities drip blood as a visual indicator of their condition.

<img src="https://cdn.modrinth.com/data/cached_images/720c7e2f12125fca2cf18e166325236776a82ae6.gif" alt="Blood Drips">

</div>

<br>

<div align="center">

### Underwater Blood Clouds
Blood disperses naturally in water environments.

<img src="https://cdn.modrinth.com/data/cached_images/d00651f88689ca7888662f4d68c9474edb35e124.gif" alt="Underwater Blood Clouds">

</div>

<br>
<br>

## Configuration

> All effects are fully customizable through [Mod Menu](https://modrinth.com/mod/modmenu).


<br>

## For Mod Developers

> Simple Blood provides an API to customize blood behavior for your mod's entities.
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
