![IndicationSounds Banner](src/client/resources/assets/a-z/is-banner.png)

# IndicationSounds

**An immersive audio enhancement mod for Minecraft.**

IndicationSounds adds subtle audio and visual feedback to Minecraft, making player conditions and environmental effects feel more noticeable without overwhelming the vanilla experience.

## Features

- **Hunger Sounds** — Audio feedback when your hunger level changes.
- **Digestion Sounds** — Ambient digestive audio for added immersion.
- **Low Health Audio** — Sound indications when your health becomes dangerously low.
- **Anxiety Music** — Optional atmospheric music that responds to dangerous situations, with smooth music fading.
- **Underwater Audio** — Low-pass audio filtering while submerged.
- **Low Health Audio Filtering** — Additional muffling to reinforce the feeling of being critically injured.
- **Visual Feedback** — Low-health vignette and damage flash effects.

## Configuration

IndicationSounds includes an in-game configuration screen through [Mod Menu](https://modrinth.com/mod/modmenu), powered by [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl).

You can customize supported sound and visual effects without manually editing configuration files.

## Requirements

- Minecraft **26.2**
- Fabric Loader
- Fabric API
- Mod Menu (for the configuration screen)
- YetAnotherConfigLib (YACL)

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

## License

See the repository's license information for usage and redistribution terms.
