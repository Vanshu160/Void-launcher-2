# Void Launcher development checklist

## Milestone 0.1.0 - Foundation (complete)
- [x] Gradle skeleton, CI APK build, theme, navigation, Home screen
- [x] License: GPL-3.0 (`LICENSE`) and third-party notices (`THIRD_PARTY.md`)

## Milestone 0.2.0 - Version manager (complete)
- [x] Mojang version manifest, offline cache, release and snapshot filters

## Milestone 0.3.0 - Landscape UI overhaul (complete)
- [x] Landscape layout, animated backdrop, top tabs, Settings tabs

## Milestone 0.4.0 - Mods (Fabric via Modrinth)
- [x] Search Modrinth for Fabric mods matching the selected version
- [x] Install with SHA-1 check and required dependencies (one level)
- [x] Per-version mods folder, installed list, remove
- [x] Selected version shared between Versions, Home and Mods
- [ ] CI build passes
- [ ] Verified on a phone
- [ ] Fabric loader install (needs the engine milestone)
- [ ] Quilt, NeoForge, Forge
- [ ] OptiFine: import a jar you downloaded yourself

## Milestone 0.5.0 - Game files download (in progress)
- [x] Version metadata, client jar, libraries and assets downloaded with SHA-1 checks
- [x] Existing valid files are skipped, so an interrupted download can resume
- [x] Downloads screen with progress, cancel and error messages
- [ ] CI build passes
- [ ] Verified on a phone
- [ ] Native libraries extracted (engine milestone)

## Later milestones
- [ ] Java runtime management (Java major version is already read from metadata)
- [ ] Settings saved between launches
- [ ] Microsoft login
- [ ] Engine API contracts
- [ ] Pojav adapter (first game launch)
- [ ] Touch controls
- [ ] Logs and diagnostics
