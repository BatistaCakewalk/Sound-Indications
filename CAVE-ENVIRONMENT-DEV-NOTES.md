# Cave Environment Audio — Developer Notes

## Scope
- Client-side cave ambience implementation, developed on `Features/cave-environment-audio`.
- Keep the feature branch separate until it is intentionally promoted to a snapshot.
- Cave audio is independent of stomach/digestion systems.

## Cave detection
The first-pass heuristic requires the Overworld, a living non-spectator player, Y below sea level + 8, no sky visibility at the feet, and sky brightness at the feet <= 2. This is not a cave-biome detector; skylight shafts can cause false negatives and enclosed structures can cause false positives.

## Audio behavior
- Quiet/loud looping ambience beds are randomly selected when cave ambience starts; fade in/out.
- General events every 45–120 seconds: short ambience 58%, tribal 21%, horror 21%; at most one general event at once.
- Water scan: 24-block spherical radius every 5 seconds, using `FluidTags.WATER`.
- If water is nearby, one droplet event is scheduled every 3–12 seconds; suppressed when no water is nearby.
- Droplet event chooses randomly from 15 OGG clips: five source droplets, each with original, higher-pitched and lower-pitched variants.
- Cave beds/events are listener-centred stereo sounds, not positional 3D audio.
- Sound category is AMBIENT.

## Music and filtering
- Vanilla MUSIC category is ducked smoothly to 25% while cave ambience is enabled and cave detection is active; it fades back on exit.
- Ducking multiplies the player's configured Music slider value and re-reads it during playback; world exit restores the configured value.
- Low-health anxiety retains priority and suppresses vanilla MUSIC; it is not itself ducked by the cave music logic.
- Cave ambience, general events and droplets are included in the existing OpenAL low-pass filtering.
- Underwater and low-health muffling choose the stronger (lower cutoff) effect rather than multiplying their strengths.
- Existing config options govern underwater and low-health muffling.

## Configuration
- Cave ambience enabled by default.
- Cave random events enabled by default.
- Default ambience volume: 0.16.
- Default event volume: 0.22.

## Assets and licensing
- Original cave recordings and converted OGG assets are under `src/client/resources/assets/a-z/sounds/enviroment/caves/`.
- Fifteen individual droplet OGGs are under `ogg/water_drops/`.
- Full-length converted `water_drops.ogg` is retained as a reference; runtime uses the individual droplet clips.
- User confirms all six original cave recordings are CC0. Source links and creator details were lost and are recorded as unavailable in `SOUND-ATTRIBUTION.csv`; do not invent source details.

## Validation
- Parse `sounds.json`; verify every referenced resource exists.
- Verify all 15 droplet files are Vorbis, 44.1 kHz, stereo.
- Build with Java 25: `gradlew.bat --offline build`.
- In-game checks: cave entry/exit fades; adjust Music slider while ducked; underwater; low health; both together; cave exit while underwater; world exit while music is ducked; no lingering sounds after leaving cave/world.
