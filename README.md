<div align="center">

<a href="https://modrinth.com/mod/simple-blood">
  <img src="https://img.shields.io/badge/Available_on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white">
</a>

<br>
<br>

<div align="center">

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

<table>
<tr>
<td width="50%">
<div style="padding: 15px; background-color: #ffe6e6; border-radius: 8px; text-align: center;">
<a href="https://github.com/Lythroo/Simple-Blood/issues"><img src="https://img.shields.io/badge/Report-Issue-e74c3c?style=for-the-badge&logo=github" alt="Report Issue"></a>
</div>
</td>
<td width="50%">
<div style="padding: 15px; background-color: #e6f2ff; border-radius: 8px; text-align: center;">
<a href="https://github.com/Lythroo/Simple-Blood/issues"><img src="https://img.shields.io/badge/Suggest-Feature-3498db?style=for-the-badge&logo=github" alt="Suggest Feature"></a>
</div>
</td>
</tr>
</table>

</div>

<br>

<div align="center">

# Features

</div>


<div align="center">


## Hit Burst + Death Burst + Bloodstains

<img src="https://cdn.modrinth.com/data/cached_images/18a16cdf909aeaae4a1fb32cb7f8f13348393c78.jpeg">

</div>

<br>

<div align="center">

## Blood in different colors

<img src="https://cdn.modrinth.com/data/cached_images/f382b83d8f1519a3a21035fbe1cccbf661bc7c3c.jpeg" alt="Blood in different colors">

</div>


<div align="center">

<br>

## Iron Golem Debris Particles

<img src="https://cdn.modrinth.com/data/cached_images/d0ee53f95871a68dd71d8cf4401afa4c78b8aef3.jpeg"
alt="Iron Golem Debris Particles">

</div>

<br>

<div align="center">

## Underwater Blood Fog

<img src="https://cdn.modrinth.com/data/cached_images/05f1a86df455742e12eca492555aec44b716477e_0.webp" alt="Underwater Blood Fog">

</div>

<br>

<div align="center">
  
## Everything's configurable

<img src="https://cdn.modrinth.com/data/cached_images/3639cffeb21d96f24c3a5d6706cd9b1dabe973f6.jpeg" alt="Everything's Configurable">

<br>

<br>


```
...and many more features!
```


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
