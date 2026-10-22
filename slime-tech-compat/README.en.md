# Slime Tech Compat 0.4.0-dev

[日本語 / full operational instructions](README.md)

Independent compatibility patch for **Slime Tech 0.1.44**, **Minecraft 26.1.2**, and NeoForge. It replaces the earlier compatibility patch, not Slime Tech itself.

> **Development build undergoing real-environment verification.** Genuine dependency compilation, Sponge Mixin application, dedicated-server tests and selected English singleplayer JEI checks have passed using the supplied mod JARs, NeoForge 26.1.2.94 and JEI 29.16.0.46. See [verification results](docs/VERIFICATION.md) for the tested scope and the next acceptance checks. Start with a backed-up world copy.

## Included

- The nine additive pickaxe mining tags from 0.2.0; retaining the original mining tiers.
- Display-only Casting Mold recipe: 1,000 mB Condensed Slime to one Gel Ingot.
- Mixed-fluid tank guards that stop transfers and unsafe aggregate reporting while preserving local contents.
- Bounded tank validation (512 members by default); unloaded neighboring chunks fail closed.
- Stable registry IDs in Pump save data. Numeric-only legacy dynamic IDs are quarantined with their data preserved because their fluid identity is ambiguous.
- Pump identity retention when sources disappear or change to a different fluid.
- Optional blocking of non-water world-source pumping, using the original source-preserving pumping method.
- Optional Grinder policy: zero starting charge and a full 120 FE required before processing; retain the existing completion cost and explicitly saved charge.
- Two Mixer JEI displays (the radioactive recipe requires both Ore Slimes ingredients), and Japanese/English information pages explaining bootstrap steps and placeholder controls.

## Install

Stop the server and back up the world, mods, and configuration. Keep the backup unchanged. If possible, start a test copy with the old mods and configuration, drain Pump buffers containing third-party dynamically numbered fluids, then shut it down normally before upgrading that copy. See the [migration procedure](docs/MIGRATION.md).

Remove old `slime-tech-compat-0.1.0.jar` / `0.2.0.jar` / `0.3.0.jar`; install `slime-tech-compat-0.4.0-dev.jar` on client and server alongside the unchanged `slime_tech-0.1.44.jar`. JEI is an optional client dependency.

Configuration is generated at `config/slime-tech-compat.properties`:

```properties
blockNonWaterWorldSources=false
fixGrinderEnergy=false
maxTankNetworkMembers=512
```

The first two policies are opt-in to avoid unexpectedly disabling existing systems. When `blockNonWaterWorldSources=true`, ALL non-water world sources, including Lava and mod fluids, are blocked; water remains allowed. The source block remains in place under the original pumping method. Restart after editing. Server settings determine multiplayer behavior. Misspelled/invalid settings fail with an explanatory error.

A blocked tank can display zero while its local contents remain intact. Do not break filled tanks without checking the original mod's content-preservation behavior. A quarantined Pump retains amount, energy, and available identity metadata but will not run/extract. Missing legacy identity cannot be recovered from an ambiguous number. Reverting the JAR alone is not a safe rollback: restore the pre-upgrade world, mods and configuration together.

## Build and tests

Use Java 25 and the bundled Gradle wrapper. Supply `../mods/slime_tech-0.1.44.jar` and `../mods/jei-26.1.2-neoforge-29.16.0.46.jar`. The patch JAR contains its own classes and resources; dependencies are supplied separately. The build checks the original mod hash.

```sh
./gradlew clean build policyTest
./gradlew runServer
./gradlew runClient
```

The output is `build/libs/slime-tech-compat-0.4.0-dev.jar`. JEI is on the client run classpath only. Actual test results and outstanding manual tests are recorded in [verification](docs/VERIFICATION.md). The old `tools/offline_verify.py` belongs to the 0.3.0 reference and is not a substitute for these real-dependency checks. Run it only in a separate extraction of the reference so it cannot overwrite current artifacts.

Run `./gradlew artifactHashes` to also generate the source ZIP and checksums. All release artifacts (JAR, source ZIP and `SHA256SUMS`) are written to `build/libs/`, matching the other mods. Verify them with `cd build/libs && sha256sum -c SHA256SUMS`.

## JEI and mining checks

The patch adds an additive pickaxe tag for nine machines without changing tool tiers. Their original loot tables each drop one machine item and contain no content-copy function. Recovering the block does not guarantee preservation of its contents.

Gel Ingot and Casting Mold information pages link the first-ingot route: smelt a Slime Block, put exactly one empty bucket in the furnace fuel slot while the furnace is still burning, and use the resulting Condensed Slime Bucket on the mold. Basic Fluid Pipe rejects hot fluids; Reinforced Fluid Pipe supports Condensed Slime and Lava. The casting process uses 1,000 mB, has a 200-tick cooldown followed by output on the next tick, and requires no FE.

Mixer clay needs exactly one Slime Ball in the center and exactly one Dust in any surrounding slot, all other slots empty, with at least 250 mB Water. Output is one Clay block. Radioactive Slime uses Slimy Coal in the top/bottom rows and Slimy Copper in the middle row, and exactly 1,000 mB Condensed Slime. Those item stacks may exceed one; one item per slot is consumed. Both processes take 100 ticks at 12 FE/t. The radioactive JEI display is omitted if either Ore Slimes item is absent. The JEI displays reflect these existing processing conditions.

Real client checks passed for Gel Ingot R navigation to Casting, its 1,000 mB tooltip, and Mixer U navigation without Ore Slimes, in English singleplayer. The next client checks cover Japanese rendering, all R/U searches and information-page layouts, the complete first-ingot survival route, survival mining, and dedicated-server join/rejoin. These checks currently have NOT_RUN status in the [client verification results](docs/VERIFICATION.md).

The original JAR hash is `f8ea80558f7f229314fa026311c194aa956f773bc235a76d7598b0a26d3030bb`.

For Furnace hidden-buffer cleanup, Slimy Sand movement, generic Item Tube interoperability and other original behaviors, consult the [issue status](docs/ISSUES.md) when planning tests. These areas retain the original mod behavior. The patch is MIT-licensed; the original mod's license remains separate. See [verification details](docs/VERIFICATION.md) and [implementation notes](docs/IMPLEMENTATION.md).

Runtime verification also checks the entire original JAR SHA-256 before world loading. NeoForge is pinned to 26.1.2.94. ST-022 converts only whole 1,000 mB items when storage has room. See [migration and rollback](docs/MIGRATION.md).

