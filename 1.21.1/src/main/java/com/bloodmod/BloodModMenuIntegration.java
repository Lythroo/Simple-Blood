package com.bloodmod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("removal")
public class BloodModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return this::createConfigScreen;
    }

    private Screen createConfigScreen(Screen parent) {
        BloodModConfig config = BloodModClient.getConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("Simple Blood Config"))
                .setSavingRunnable(() -> BloodModConfig.save(config))
                .setTransparentBackground(true);

        ConfigEntryBuilder entry = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));
        builder.setFallbackCategory(general);

        general.addEntry(entry.startBooleanToggle(Text.literal("Enable Mod"), config.general.modEnabled)
                .setDefaultValue(true).setTooltip(Text.literal("Enable or disable the entire mod"))
                .setSaveConsumer(val -> config.general.modEnabled = val).build());

        general.addEntry(entry.startBooleanToggle(Text.literal("Player Bleeding"), config.general.playerBleed)
                .setDefaultValue(true).setTooltip(Text.literal("Allow players to bleed"))
                .setSaveConsumer(val -> config.general.playerBleed = val).build());

        ConfigCategory effects = builder.getOrCreateCategory(Text.literal("Particle Effects"));

        effects.addEntry(entry.startBooleanToggle(Text.literal("Hit Burst"), config.particles.hitBurst)
                .setDefaultValue(true).setTooltip(Text.literal("Spawn blood particles when entities are hit"))
                .setSaveConsumer(val -> config.particles.hitBurst = val).build());

        effects.addEntry(entry.startBooleanToggle(Text.literal("Death Burst"), config.particles.deathBurst)
                .setDefaultValue(true).setTooltip(Text.literal("Spawn blood particles when entities die"))
                .setSaveConsumer(val -> config.particles.deathBurst = val).build());

        effects.addEntry(entry.startBooleanToggle(Text.literal("Low Health Dripping"), config.particles.lowHealthDrip)
                .setDefaultValue(true).setTooltip(Text.literal("Entities drip blood when health is low"))
                .setSaveConsumer(val -> config.particles.lowHealthDrip = val).build());

        effects.addEntry(entry.startIntSlider(Text.literal("Particle Size (%)"), config.particles.particleSize, 50, 300)
                .setDefaultValue(210).setTooltip(Text.literal("Scale blood particle size"))
                .setSaveConsumer(val -> config.particles.particleSize = val).build());

        effects.addEntry(entry.startIntSlider(Text.literal("Particle Lifetime (%)"), config.particles.particleLifetime, 10, 200)
                .setDefaultValue(100).setTooltip(Text.literal("How long particles last"))
                .setSaveConsumer(val -> config.particles.particleLifetime = val).build());

        effects.addEntry(entry.startIntSlider(Text.literal("Particle Gravity (%)"), config.particles.particleGravity, 50, 200)
                .setDefaultValue(100).setTooltip(Text.literal("How fast particles fall"))
                .setSaveConsumer(val -> config.particles.particleGravity = val).build());

        effects.addEntry(entry.startIntSlider(Text.literal("Particle Drag (%)"), config.particles.particleDrag, 0, 200)
                .setDefaultValue(100).setTooltip(Text.literal("Air resistance on particles"))
                .setSaveConsumer(val -> config.particles.particleDrag = val).build());

        ConfigCategory stains = builder.getOrCreateCategory(Text.literal("Blood Stains"));

        stains.addEntry(entry.startBooleanToggle(Text.literal("Enable Blood Stains"), config.bloodStains.enabled)
                .setDefaultValue(true).setTooltip(Text.literal("Blood particles leave stains on the ground"))
                .setSaveConsumer(val -> config.bloodStains.enabled = val).build());

        stains.addEntry(entry.startIntSlider(Text.literal("Stain Size (%)"), config.bloodStains.stainSize, 30, 200)
                .setDefaultValue(80).setTooltip(Text.literal("Size multiplier for blood stains"))
                .setSaveConsumer(val -> config.bloodStains.stainSize = val).build());

        stains.addEntry(entry.startIntSlider(Text.literal("Stain Duration (seconds)"), config.bloodStains.stainDurationSeconds, 1, 60)
                .setDefaultValue(5).setTooltip(Text.literal("How long blood stains last"))
                .setSaveConsumer(val -> config.bloodStains.stainDurationSeconds = val).build());

        ConfigCategory hitBurst = builder.getOrCreateCategory(Text.literal("Hit Burst"));

        hitBurst.addEntry(entry.startIntSlider(Text.literal("Burst Intensity (%)"), config.hitBurst.burstIntensity, 20, 300)
                .setDefaultValue(100).setTooltip(Text.literal("Number of particles spawned on hit"))
                .setSaveConsumer(val -> config.hitBurst.burstIntensity = val).build());

        hitBurst.addEntry(entry.startIntSlider(Text.literal("Burst Duration (%)"), config.hitBurst.burstDuration, 30, 200)
                .setDefaultValue(100).setTooltip(Text.literal("How long the burst effect lasts"))
                .setSaveConsumer(val -> config.hitBurst.burstDuration = val).build());

        hitBurst.addEntry(entry.startIntSlider(Text.literal("Burst Spread (%)"), config.hitBurst.burstSpread, 30, 200)
                .setDefaultValue(100).setTooltip(Text.literal("How far particles spread"))
                .setSaveConsumer(val -> config.hitBurst.burstSpread = val).build());

        hitBurst.addEntry(entry.startIntSlider(Text.literal("Damage Cooldown (ms)"), config.hitBurst.damageCooldown, 0, 500)
                .setDefaultValue(100).setTooltip(Text.literal("Minimum time between bursts"))
                .setSaveConsumer(val -> config.hitBurst.damageCooldown = val).build());

        ConfigCategory deathBurst = builder.getOrCreateCategory(Text.literal("Death Burst"));

        deathBurst.addEntry(entry.startIntSlider(Text.literal("Death Intensity (%)"), config.deathBurst.deathIntensity, 20, 300)
                .setDefaultValue(100).setTooltip(Text.literal("Number of particles spawned on death"))
                .setSaveConsumer(val -> config.deathBurst.deathIntensity = val).build());

        deathBurst.addEntry(entry.startIntSlider(Text.literal("Death Spread (%)"), config.deathBurst.deathSpread, 30, 200)
                .setDefaultValue(100).setTooltip(Text.literal("How far death particles spread"))
                .setSaveConsumer(val -> config.deathBurst.deathSpread = val).build());

        ConfigCategory lowHealth = builder.getOrCreateCategory(Text.literal("Low Health Drip"));

        lowHealth.addEntry(entry.startIntSlider(Text.literal("Health Threshold (%)"), config.lowHealth.threshold, 10, 90)
                .setDefaultValue(50).setTooltip(Text.literal("Health % below which entities drip blood"))
                .setSaveConsumer(val -> config.lowHealth.threshold = val).build());

        lowHealth.addEntry(entry.startIntSlider(Text.literal("Drip Frequency (%)"), config.lowHealth.dripFrequency, 20, 500)
                .setDefaultValue(100).setTooltip(Text.literal("How often drips occur"))
                .setSaveConsumer(val -> config.lowHealth.dripFrequency = val).build());

        lowHealth.addEntry(entry.startIntSlider(Text.literal("Drip Intensity (%)"), config.lowHealth.dripIntensity, 20, 300)
                .setDefaultValue(100).setTooltip(Text.literal("Number of particles per drip"))
                .setSaveConsumer(val -> config.lowHealth.dripIntensity = val).build());

        ConfigCategory audio = builder.getOrCreateCategory(Text.literal("Audio"));

        audio.addEntry(entry.startBooleanToggle(Text.literal("Enable Sounds"), config.audio.soundEnabled)
                .setDefaultValue(true).setTooltip(Text.literal("Play blood drip sounds"))
                .setSaveConsumer(val -> config.audio.soundEnabled = val).build());

        audio.addEntry(entry.startIntSlider(Text.literal("Sound Volume (%)"), config.audio.soundVolume, 0, 200)
                .setDefaultValue(100).setTooltip(Text.literal("Volume of blood sounds"))
                .setSaveConsumer(val -> config.audio.soundVolume = val).build());

        audio.addEntry(entry.startIntSlider(Text.literal("Sound Pitch (%)"), config.audio.soundPitch, 50, 150)
                .setDefaultValue(100).setTooltip(Text.literal("Pitch of blood sounds"))
                .setSaveConsumer(val -> config.audio.soundPitch = val).build());

        ConfigCategory underwater = builder.getOrCreateCategory(Text.literal("Underwater"));

        underwater.addEntry(entry.startBooleanToggle(Text.literal("Transform to Fog"), config.underwater.transformToFog)
                .setDefaultValue(true).setTooltip(Text.literal("Blood transforms into fog underwater"))
                .setSaveConsumer(val -> config.underwater.transformToFog = val).build());

        underwater.addEntry(entry.startIntSlider(Text.literal("Fog Size (%)"), config.underwater.fogSize, 30, 300)
                .setDefaultValue(100).setTooltip(Text.literal("Size of underwater fog clouds"))
                .setSaveConsumer(val -> config.underwater.fogSize = val).build());

        underwater.addEntry(entry.startIntSlider(Text.literal("Fog Lifetime (%)"), config.underwater.fogLifetime, 30, 200)
                .setDefaultValue(100).setTooltip(Text.literal("How long fog clouds last"))
                .setSaveConsumer(val -> config.underwater.fogLifetime = val).build());

        underwater.addEntry(entry.startIntSlider(Text.literal("Fog Opacity (%)"), config.underwater.fogOpacity, 30, 200)
                .setDefaultValue(100).setTooltip(Text.literal("Transparency of fog clouds"))
                .setSaveConsumer(val -> config.underwater.fogOpacity = val).build());

        ConfigCategory bloodColors = builder.getOrCreateCategory(Text.literal("Blood Colors"));

        bloodColors.addEntry(entry.startBooleanToggle(Text.literal("Enable Custom Colors"), config.bloodColors.enableCustomColors)
                .setDefaultValue(false).setTooltip(Text.literal("Use custom blood colors instead of defaults"))
                .setSaveConsumer(val -> config.bloodColors.enableCustomColors = val).build());

        bloodColors.addEntry(entry.startTextDescription(Text.literal("§7Visit §9htmlcolorcodes.com§7 or §9colorpicker.me§7 to find hex color codes.§r")).build());
        bloodColors.addEntry(entry.startTextDescription(Text.literal("§7Enter the 6-digit hex code in the field (e.g., §cFF0000§7 for red).§r")).build());
        bloodColors.addEntry(entry.startTextDescription(Text.literal(" ")).build());

        addSimpleColorField(bloodColors, entry, "Default Blood (Most Creatures)",
                () -> config.bloodColors.defaultBlood, v -> config.bloodColors.defaultBlood = v, 0x660303);

        addSimpleColorField(bloodColors, entry, "Your Player Blood",
                () -> config.bloodColors.playerBlood, v -> config.bloodColors.playerBlood = v, 0x660303);

        addSimpleColorField(bloodColors, entry, "Other Players Blood",
                () -> config.bloodColors.otherPlayersBlood, v -> config.bloodColors.otherPlayersBlood = v, 0x660303);

        addSimpleColorField(bloodColors, entry, "Zombie Blood (Coagulated)",
                () -> config.bloodColors.zombieBlood, v -> config.bloodColors.zombieBlood = v, 0x400202);

        addSimpleColorField(bloodColors, entry, "Skeleton Blood (Bone Dust)",
                () -> config.bloodColors.skeletonBlood, v -> config.bloodColors.skeletonBlood = v, 0xBFB89D);

        addSimpleColorField(bloodColors, entry, "End Creatures (Enderman, Shulker)",
                () -> config.bloodColors.endBlood, v -> config.bloodColors.endBlood = v, 0x73197A);

        addSimpleColorField(bloodColors, entry, "Spider Blood",
                () -> config.bloodColors.spiderBlood, v -> config.bloodColors.spiderBlood = v, 0x267319);

        addSimpleColorField(bloodColors, entry, "Slime Blood",
                () -> config.bloodColors.slimeBlood, v -> config.bloodColors.slimeBlood = v, 0x4CB833);

        addSimpleColorField(bloodColors, entry, "Aquatic Creatures (Squid, Guardian)",
                () -> config.bloodColors.aquaticBlood, v -> config.bloodColors.aquaticBlood = v, 0x19337F);

        addSimpleColorField(bloodColors, entry, "Nether Creatures (Piglin, Hoglin)",
                () -> config.bloodColors.netherBlood, v -> config.bloodColors.netherBlood = v, 0xD94D0D);

        addSimpleColorField(bloodColors, entry, "Blaze Blood",
                () -> config.bloodColors.blazeBlood, v -> config.bloodColors.blazeBlood = v, 0xE67F19);

        addSimpleColorField(bloodColors, entry, "Bee Blood (Hemolymph)",
                () -> config.bloodColors.beeBlood, v -> config.bloodColors.beeBlood = v, 0xB39919);

        addSimpleColorField(bloodColors, entry, "Creeper Blood",
                () -> config.bloodColors.creeperBlood, v -> config.bloodColors.creeperBlood = v, 0x1E5C14);

        addSimpleColorField(bloodColors, entry, "Golem Blood (Iron, Snow)",
                () -> config.bloodColors.golemBlood, v -> config.bloodColors.golemBlood = v, 0x666666);

        ConfigCategory entities = builder.getOrCreateCategory(Text.literal("Entity Overrides"));

        entities.addEntry(entry.startBooleanToggle(Text.literal("Zombie"), config.entities.zombie)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.zombie = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Zombie Villager"), config.entities.zombieVillager)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.zombieVillager = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Husk"), config.entities.husk)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.husk = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Drowned"), config.entities.drowned)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.drowned = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Zombie Horse"), config.entities.zombieHorse)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.zombieHorse = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Skeleton"), config.entities.skeleton)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.skeleton = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Stray"), config.entities.stray)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.stray = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Wither Skeleton"), config.entities.witherSkeleton)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.witherSkeleton = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Bogged"), config.entities.bogged)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.bogged = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Zombified Piglin"), config.entities.zombifiedPiglin)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.zombifiedPiglin = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Phantom"), config.entities.phantom)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.phantom = v).build());

        entities.addEntry(entry.startBooleanToggle(Text.literal("Warden"), config.entities.warden)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.warden = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Ender Dragon"), config.entities.enderDragon)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.enderDragon = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Wither"), config.entities.wither)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.wither = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Blaze"), config.entities.blaze)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.blaze = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Ghast"), config.entities.ghast)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.ghast = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Magma Cube"), config.entities.magmaCube)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.magmaCube = v).build());

        entities.addEntry(entry.startBooleanToggle(Text.literal("Enderman"), config.entities.enderman)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.enderman = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Endermite"), config.entities.endermite)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.endermite = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Shulker"), config.entities.shulker)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.shulker = v).build());

        entities.addEntry(entry.startBooleanToggle(Text.literal("Creeper"), config.entities.creeper)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.creeper = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Silverfish"), config.entities.silverfish)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.silverfish = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Iron Golem"), config.entities.ironGolem)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.ironGolem = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Snow Golem"), config.entities.snowGolem)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.snowGolem = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Slime"), config.entities.slime)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.slime = v).build());

        entities.addEntry(entry.startBooleanToggle(Text.literal("Cow"), config.entities.cow)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.cow = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Mooshroom"), config.entities.mooshroom)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.mooshroom = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Chicken"), config.entities.chicken)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.chicken = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Pig"), config.entities.pig)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.pig = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Sheep"), config.entities.sheep)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.sheep = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Horse"), config.entities.horse)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.horse = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Rabbit"), config.entities.rabbit)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.rabbit = v).build());

        entities.addEntry(entry.startBooleanToggle(Text.literal("Wolf"), config.entities.wolf)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.wolf = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Cat"), config.entities.cat)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.cat = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Fox"), config.entities.fox)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.fox = v).build());

        entities.addEntry(entry.startBooleanToggle(Text.literal("Dolphin"), config.entities.dolphin)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.dolphin = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Squid"), config.entities.squid)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.squid = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Guardian"), config.entities.guardian)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.guardian = v).build());

        entities.addEntry(entry.startBooleanToggle(Text.literal("Spider"), config.entities.spider)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.spider = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Cave Spider"), config.entities.caveSpider)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.caveSpider = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Bee"), config.entities.bee)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.bee = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Polar Bear"), config.entities.polarBear)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.polarBear = v).build());

        entities.addEntry(entry.startBooleanToggle(Text.literal("Villager"), config.entities.villager)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.villager = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Witch"), config.entities.witch)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.witch = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Pillager"), config.entities.pillager)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.pillager = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Vindicator"), config.entities.vindicator)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.vindicator = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Evoker"), config.entities.evoker)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.evoker = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Ravager"), config.entities.ravager)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.ravager = v).build());

        entities.addEntry(entry.startBooleanToggle(Text.literal("Piglin"), config.entities.piglin)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.piglin = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Piglin Brute"), config.entities.piglinBrute)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.piglinBrute = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Hoglin"), config.entities.hoglin)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.hoglin = v).build());
        entities.addEntry(entry.startBooleanToggle(Text.literal("Zoglin"), config.entities.zoglin)
                .setDefaultValue(true).setSaveConsumer(v -> config.entities.zoglin = v).build());

        ConfigCategory moddedEntities = builder.getOrCreateCategory(Text.literal("Modded Entities"));

        moddedEntities.addEntry(entry.startTextDescription(
                Text.literal("§lModded Entity Blood Settings§r\n" +
                        "§7Format: §emodname:entity_name§7 (e.g., §ealexsmobs:grizzly_bear§7)\n" +
                        "§7Use §eF3+H§7 in-game to see entity IDs on spawn eggs.§r")
        ).build());

        moddedEntities.addEntry(entry.startTextDescription(Text.literal("")).build());

        moddedEntities.addEntry(entry.startTextDescription(
                Text.literal("§6Add New Entity§r")
        ).build());

        final String[] newEntityId = {""};

        moddedEntities.addEntry(entry.startStrField(Text.literal("Entity ID"), "")
                .setDefaultValue("")
                .setTooltip(Text.literal("Enter entity ID (e.g., alexsmobs:grizzly_bear)"))
                .setSaveConsumer(id -> newEntityId[0] = id)
                .build());

        moddedEntities.addEntry(entry.startTextDescription(
                Text.literal("§7Enter ID above, then click 'Save' to add with default settings.§r")
        ).build());

        moddedEntities.addEntry(entry.startTextDescription(Text.literal("")).build());

        builder.setSavingRunnable(() -> {

            if (newEntityId[0] != null && !newEntityId[0].trim().isEmpty() && newEntityId[0].contains(":")) {
                String id = newEntityId[0].trim();
                if (!config.moddedEntities.customEntities.containsKey(id)) {
                    config.moddedEntities.customEntities.put(id,
                            new BloodModConfig.ModdedEntities.ModdedEntitySettings(
                                    true,      

                                    true,      

                                    true,      

                                    0x660303   

                            ));
                    BloodMod.LOGGER.info("Added new modded entity via config UI: {}", id);
                }
            }
            BloodModConfig.save(config);
        });

        moddedEntities.addEntry(entry.startTextDescription(
                Text.literal("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━§r")
        ).build());
        moddedEntities.addEntry(entry.startTextDescription(Text.literal("")).build());

        if (!config.moddedEntities.customEntities.isEmpty()) {
            moddedEntities.addEntry(entry.startTextDescription(
                    Text.literal("§6Configured Entities§r")
            ).build());

            moddedEntities.addEntry(entry.startTextDescription(Text.literal("")).build());

            java.util.List<String> toRemove = new java.util.ArrayList<>();

            for (java.util.Map.Entry<String, BloodModConfig.ModdedEntities.ModdedEntitySettings> entityEntry :
                    config.moddedEntities.customEntities.entrySet()) {
                String entityId = entityEntry.getKey();
                BloodModConfig.ModdedEntities.ModdedEntitySettings settings = entityEntry.getValue();

                moddedEntities.addEntry(entry.startTextDescription(
                        Text.literal("§e" + entityId + "§r")
                ).build());

                moddedEntities.addEntry(entry.startBooleanToggle(Text.literal("  §c§lRemove§r"), false)
                        .setDefaultValue(false)
                        .setTooltip(Text.literal("§cEnable and click 'Save' to delete this entity§r"))
                        .setSaveConsumer(remove -> {
                            if (remove) {
                                toRemove.add(entityId);
                            }
                        })
                        .build());

                moddedEntities.addEntry(entry.startBooleanToggle(Text.literal("  Enabled"), settings.enabled)
                        .setDefaultValue(true)
                        .setTooltip(Text.literal("Can this entity bleed?"))
                        .setSaveConsumer(v -> settings.enabled = v)
                        .build());

                moddedEntities.addEntry(entry.startBooleanToggle(Text.literal("  Drip at Low Health"), settings.canDripAtLowHealth)
                        .setDefaultValue(true)
                        .setTooltip(Text.literal("Continuously drip when wounded (false for constructs/golems)"))
                        .setSaveConsumer(v -> settings.canDripAtLowHealth = v)
                        .build());

                moddedEntities.addEntry(entry.startBooleanToggle(Text.literal("  Transform to Stains/Fog"), settings.transformToStains)
                        .setDefaultValue(true)
                        .setTooltip(Text.literal("Particles turn into bloodstains on ground and fog clouds in water"))
                        .setSaveConsumer(v -> settings.transformToStains = v)
                        .build());

                moddedEntities.addEntry(entry.startColorField(Text.literal("  Blood Color"), settings.bloodColor)
                        .setDefaultValue(0x660303)
                        .setTooltip(Text.literal("RGB color hex (e.g., FF0000 for red)"))
                        .setSaveConsumer(v -> settings.bloodColor = v)
                        .build());

                moddedEntities.addEntry(entry.startTextDescription(Text.literal("")).build());
            }

            Runnable originalSave = builder.getSavingRunnable();
            builder.setSavingRunnable(() -> {

                for (String id : toRemove) {
                    config.moddedEntities.customEntities.remove(id);
                    BloodMod.LOGGER.info("Removed modded entity via config UI: {}", id);
                }

                if (newEntityId[0] != null && !newEntityId[0].trim().isEmpty() && newEntityId[0].contains(":")) {
                    String id = newEntityId[0].trim();
                    if (!config.moddedEntities.customEntities.containsKey(id)) {
                        config.moddedEntities.customEntities.put(id,
                                new BloodModConfig.ModdedEntities.ModdedEntitySettings(
                                        true,      

                                        true,      

                                        true,      

                                        0x660303   

                                ));
                        BloodMod.LOGGER.info("Added new modded entity via config UI: {}", id);
                    }
                }

                BloodModConfig.save(config);
            });

        } else {
            moddedEntities.addEntry(entry.startTextDescription(
                    Text.literal("§7No entities configured. Use 'Add New Entity' above.§r")
            ).build());
        }

        moddedEntities.addEntry(entry.startTextDescription(Text.literal("")).build());
        moddedEntities.addEntry(entry.startTextDescription(
                Text.literal("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━§r")
        ).build());

        moddedEntities.addEntry(entry.startTextDescription(
                Text.literal("§6Color Reference (hex):§r\n" +
                        "§7Red: FF0000 | Green: 00FF00 | Blue: 0000FF\n" +
                        "§7Purple: 9933FF | Orange: FF6600 | Yellow: FFFF00§r")
        ).build());

        return builder.build();
    }

    private void addSimpleColorField(ConfigCategory category, ConfigEntryBuilder entry, String label,
                                     Supplier<Integer> colorGetter, Consumer<Integer> colorSetter, int defaultColor) {

        int currentColor = colorGetter.get();

        category.addEntry(entry.startTextDescription(
                Text.literal("§l" + label + "§r")
        ).build());

        category.addEntry(entry.startColorField(Text.literal("  Color"), currentColor)
                .setDefaultValue(defaultColor)
                .setTooltip(Text.literal("Enter hex color code (e.g., FF0000)"))
                .setSaveConsumer(colorSetter)
                .build());

        category.addEntry(entry.startTextDescription(Text.literal("")).build());
    }
}