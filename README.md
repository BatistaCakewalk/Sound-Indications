![IndicationSounds Banner](src/client/resources/assets/a-z/is-banner.png)

# IndicationSounds

**An immersive audio enhancement mod for Minecraft.**

IndicationSounds adds subtle audio and visual feedback to Minecraft, making player conditions and environmental effects feel more noticeable without overwhelming the vanilla experience.

## Features

- **Hunger Sounds** â€” Audio feedback when your hunger level changes.
- **Digestion Sounds** â€” Ambient digestive audio for added immersion. *Why the fuck did I add it? No fucking idea-*
- **Low Health Audio** â€” Sound indications when your health becomes dangerously low.
- **Anxiety Music** â€” Optional atmospheric music that responds to dangerous situations, with smooth music fading.
- **Underwater Audio** â€” Low-pass audio filtering while submerged.
- **Cave Ambience** — Quiet/loud ambience beds, randomized cave events and water droplets, vanilla music ducking, and underwater/low-health muffling.
- **Low Health Audio Filtering** â€” Additional muffling to reinforce the feeling of being critically injured.
- **Visual Feedback** â€” Low-health vignette and damage flash effects.

## Configuration

IndicationSounds includes an in-game configuration screen through [Mod Menu](https://modrinth.com/mod/modmenu), powered by [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl).

You can customize supported sound and visual effects without manually editing configuration files.

## Requirements

- Minecraft **26.2**
- Fabric Loader
- Fabric API
- Mod Menu (for the configuration screen)
- YetAnotherConfigLib (YACL)

## Recommended Mods

- **Sound Physics Remastered** — recommended* for enhanced cave acoustics; adds realistic sound attenuation, reverberation and absorption.

*Optional recommendation only. Sound Physics Remastered is not required; IndicationSounds works without it.

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Install the required dependencies.
3. Download the IndicationSounds `.jar` file.
4. Place it inside your Minecraft `mods` folder.
5. Launch Minecraft using your Fabric installation.

## Compatibility Notes

IndicationSounds is currently a **client-side mod**.

Some multiplayer information, such as other players' hunger and digestion state, is not synchronized to clients by vanilla Minecraft. Consequently, those particular indications are limited to information available locally.

## Development

IndicationSounds is developed for Fabric and uses Gradle for building.

To build from source:

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The resulting mod JAR can be found in:

```text
build/libs/
```

## Credits

- **Low-health sound and music assets** â€” The PokÃ©mon Company / Nintendo.
- **Digestion audio** â€” Garuda1982 on Freesound: https://freesound.org/people/Garuda1982/sounds/523824/
- **Hunger sounds** â€” Robinhood76 on Freesound: https://freesound.org/people/Robinhood76/sounds/565164/

Third-party asset attribution does not imply endorsement or grant redistribution rights. Check each asset's license and permissions before redistributing it.

## Additional Audio Credits

The expanded stomach sound library and per-file source/licence records are documented in [SOUND-CREDITS.md](SOUND-CREDITS.md) and [SOUND-ATTRIBUTION.csv](SOUND-ATTRIBUTION.csv).

## License

See the repository's license information for usage and redistribution terms.
