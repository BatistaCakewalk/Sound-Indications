package dev.batista.aZ.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/** Owns cave ambience and sparse cave events for the local client player. */
final class CaveAmbienceController {

    private static final String MOD_ID = "a-z";
    private static final int MIN_EVENT_DELAY_TICKS = 45 * 20;
    private static final int MAX_EVENT_DELAY_TICKS = 120 * 20;
    private static final int WATER_SCAN_RADIUS = 24;
    private static final int WATER_SCAN_INTERVAL_TICKS = 5 * 20;
    private static final int MIN_WATER_DROP_DELAY_TICKS = 3 * 20;
    private static final int MAX_WATER_DROP_DELAY_TICKS = 12 * 20;

    private static final SoundEvent QUIET_AMBIENCE = event("cave_ambience_quiet");
    private static final SoundEvent LOUD_AMBIENCE = event("cave_ambience_loud");
    private static final SoundEvent SHORT_AMBIENCE = event("cave_short_ambience");
    private static final SoundEvent TRIBAL_AMBIENCE = event("cave_tribal");
    private static final SoundEvent HORROR_AMBIENCE = event("cave_horror");
    private static final SoundEvent WATER_DROPS = event("cave_water_drops");

    private final RandomSource random = RandomSource.create();
    private CaveAmbienceSoundInstance ambience;
    private CaveEventSoundInstance activeEvent;
    private CaveEventSoundInstance activeWaterDrop;
    private long ticks;
    private long nextEventAt;
    private long nextWaterCheckAt;
    private long nextWaterDropAt;
    private boolean waterNearby;
    private boolean musicDucked;

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MOD_ID, name));
    }

    void tick(SoundManager sounds, LocalPlayer player, AZConfig config) {
        ticks++;
        boolean cave = player != null && player.isAlive() && !player.isSpectator()
                && isCaveLike(player);
        musicDucked = cave && config.caveAmbienceEnabled;

        if (!cave) {
            fadeAmbience();
            stopEvent(sounds);
            stopWaterDrop(sounds);
            nextEventAt = 0;
            nextWaterCheckAt = 0;
            nextWaterDropAt = 0;
            waterNearby = false;
            return;
        }

        if (config.caveAmbienceEnabled) {
            maintainAmbience(sounds, config.caveAmbienceVolume);
        } else {
            fadeAmbience();
        }

        if (config.caveEventsEnabled) {
            maintainGeneralEvents(sounds, config.caveEventsVolume);
            maintainWaterDrops(sounds, player, config.caveEventsVolume);
        } else {
            stopEvent(sounds);
            stopWaterDrop(sounds);
            nextEventAt = 0;
            nextWaterCheckAt = 0;
            nextWaterDropAt = 0;
            waterNearby = false;
        }
    }

    boolean shouldDuckMusic() {
        return musicDucked;
    }

    private boolean isCaveLike(LocalPlayer player) {
        Level level = player.level();
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }

        BlockPos pos = player.blockPosition();
        return pos.getY() < level.getSeaLevel() + 8
                && !level.canSeeSky(pos)
                && level.getBrightness(LightLayer.SKY, pos) <= 2;
    }

    private void maintainAmbience(SoundManager sounds, float volume) {
        if (ambience != null && ambience.isStopped()) {
            ambience = null;
        }
        if (ambience != null) {
            ambience.cancelFadeOut();
            ambience.setBaseVolume(volume);
            return;
        }

        SoundEvent selected = random.nextInt(4) == 0 ? LOUD_AMBIENCE : QUIET_AMBIENCE;
        ambience = new CaveAmbienceSoundInstance(selected, volume);
        sounds.play(ambience);
    }

    private void fadeAmbience() {
        if (ambience != null) {
            ambience.fadeOut();
            if (ambience.isStopped()) {
                ambience = null;
            }
        }
    }

    private void maintainGeneralEvents(SoundManager sounds, float volume) {
        if (nextEventAt == 0) {
            nextEventAt = scheduleNextGeneralEvent();
        } else if (ticks >= nextEventAt) {
            if (activeEvent == null || activeEvent.isStopped()) {
                playRandomGeneralEvent(sounds, volume);
                nextEventAt = scheduleNextGeneralEvent();
            }
        }
    }

    private void maintainWaterDrops(SoundManager sounds, LocalPlayer player, float volume) {
        if (ticks >= nextWaterCheckAt) {
            waterNearby = hasNearbyWater(player);
            nextWaterCheckAt = ticks + WATER_SCAN_INTERVAL_TICKS;

            if (!waterNearby) {
                stopWaterDrop(sounds);
                nextWaterDropAt = 0;
            }
        }

        if (!waterNearby) {
            return;
        }

        if (nextWaterDropAt == 0) {
            nextWaterDropAt = scheduleNextWaterDrop();
        } else if (ticks >= nextWaterDropAt
                && (activeWaterDrop == null || activeWaterDrop.isStopped())) {
            playWaterDrop(sounds, volume);
            nextWaterDropAt = scheduleNextWaterDrop();
        }
    }

    private boolean hasNearbyWater(LocalPlayer player) {
        Level level = player.level();
        BlockPos center = player.blockPosition();
        BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
        int radiusSquared = WATER_SCAN_RADIUS * WATER_SCAN_RADIUS;

        for (int x = -WATER_SCAN_RADIUS; x <= WATER_SCAN_RADIUS; x++) {
            for (int y = -WATER_SCAN_RADIUS; y <= WATER_SCAN_RADIUS; y++) {
                for (int z = -WATER_SCAN_RADIUS; z <= WATER_SCAN_RADIUS; z++) {
                    if (x * x + y * y + z * z > radiusSquared) {
                        continue;
                    }

                    scanPos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (level.getFluidState(scanPos).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private long scheduleNextGeneralEvent() {
        return ticks + MIN_EVENT_DELAY_TICKS
                + random.nextInt(MAX_EVENT_DELAY_TICKS - MIN_EVENT_DELAY_TICKS + 1);
    }

    private long scheduleNextWaterDrop() {
        return ticks + MIN_WATER_DROP_DELAY_TICKS
                + random.nextInt(MAX_WATER_DROP_DELAY_TICKS - MIN_WATER_DROP_DELAY_TICKS + 1);
    }

    private void playRandomGeneralEvent(SoundManager sounds, float volume) {
        if (activeEvent != null && !activeEvent.isStopped()) {
            return;
        }

        int choice = random.nextInt(100);
        SoundEvent selected;
        if (choice < 58) {
            selected = SHORT_AMBIENCE;
        } else if (choice < 79) {
            selected = TRIBAL_AMBIENCE;
        } else {
            selected = HORROR_AMBIENCE;
        }
        float pitch = 0.96f + random.nextFloat() * 0.08f;
        activeEvent = new CaveEventSoundInstance(selected, volume, pitch);
        sounds.play(activeEvent);
    }

    private void playWaterDrop(SoundManager sounds, float volume) {
        if (activeWaterDrop != null && !activeWaterDrop.isStopped()) {
            return;
        }

        float pitch = 0.96f + random.nextFloat() * 0.08f;
        activeWaterDrop = new CaveEventSoundInstance(WATER_DROPS, volume, pitch);
        sounds.play(activeWaterDrop);
    }

    private void stopEvent(SoundManager sounds) {
        if (activeEvent != null) {
            sounds.stop(activeEvent);
            activeEvent = null;
        }
    }

    private void stopWaterDrop(SoundManager sounds) {
        if (activeWaterDrop != null) {
            sounds.stop(activeWaterDrop);
            activeWaterDrop = null;
        }
    }

    void reset(SoundManager sounds) {
        if (ambience != null) {
            ambience.stopImmediately();
            sounds.stop(ambience);
            ambience = null;
        }
        stopEvent(sounds);
        stopWaterDrop(sounds);
        activeEvent = null;
        activeWaterDrop = null;
        nextEventAt = 0;
        nextWaterCheckAt = 0;
        nextWaterDropAt = 0;
        waterNearby = false;
        musicDucked = false;
        ticks = 0;
    }
}
