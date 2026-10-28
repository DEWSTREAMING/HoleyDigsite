# Holey Digsite

A Hydroneer-style mining skyblock mod for Minecraft 1.21.1 (NeoForge). Dig, pan and sell what you find, then put the money
into water-powered machines, new land, metal casting and smithing.

The mod is in alpha. Downloads and the full description are on CurseForge.

## Reporting issues
Bugs and ideas for the mod and for the Holey Digsite - First Up modpack both go in the
[issues](https://github.com/DEWSTREAMING/HoleyDigsite/issues/new/choose) here. Pick "Mod bug" or "Modpack bug" so it
gets the right label, and add a crash report or log link if the game crashed.

## Fair play
Everything in Holey Digsite is earned in game. Coins, crates, cosmetics and every other reward can only be obtained by
playing. Selling any of it for real money, whether crates, coins, items, cosmetics, ranks or any other advantage, directly or
indirectly (including on servers or in modpacks that use this mod), is not permitted.

## Building
Java 21 and NeoForge 21.1.x (ModDevGradle).

```
gradlew build
```

The jar ends up in `build/libs/`. `gradlew runClient` starts a dev client. GameTests live in `devtests/`; copy them into
`src/main/java/com/holeydigsite/devtest/` and run `gradlew runGameTestServer`.

Most content is data driven: tiers (`data/holeydigsite/tiers`), the shop catalogue, sell values, forge and anvil recipes, sites
and store layouts. Textures and generated models come from the scripts in `tools/`, and `tools/EconomySim.java` checks the
economy after you change prices or loot tables.

## License
All Rights Reserved. Please ask before reusing code or assets.
