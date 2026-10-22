# Changelog

## 0.4.0-dev — 2026-10-22

- Real Java25 / Gradle9.6.1 / ModDev2.0.148 / NeoForge26.1.2.94 dependency build and dedicated-server tests. JEI29.16.0.46 isolated to clients.
- Runtime SHA-256 gate for the audited Slime Tech0.1.44 JAR.
- Preserve invalid/legacy Tank contents and unknown Pump schemas, and suspend charging while Pumps are quarantined.
- Tank/Pump external fluid capabilities respect transaction abort and nested rollback.
- Fix furnace fractional conversion while retaining the 1000mB/item ratio and normal extraction.
- Retain nine mining tags, clarify JEI entry points and exact Mixer conditions in Japanese/English.
- Add baseline, options, migration/reload, packaged-JAR verification, source archive, checksums and opt-in CI.
- Pre-release: remaining manual UI, cross-mod operations and performance limits are recorded in docs/VERIFICATION.md.

## Reference 0.3.0

Prototype supplied as reference/slime-tech-compat-0.3.0-source.zip. Its stub build and custom-weaver results are historical evidence only, not real-runtime certification. Original MIT attribution retained in LICENSE.
