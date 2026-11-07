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
- [x] CI build passes
- [ ] Verified on a phone
- [ ] Fabric loader install (needs the engine milestone)

## Milestone 0.5.0 - Game files download (complete)
- [x] Version metadata, client jar, libraries and assets downloaded with SHA-1 checks
- [x] Existing valid files are skipped, so an interrupted download can resume
- [x] Downloads screen with progress, cancel and error messages
- [x] CI build passes
- [x] Verified on a phone (Minecraft 1.21.11 downloaded)

## Milestone 0.6.0 - Java runtime (complete)
- [x] Runtime chosen from the version's required Java major (8, 17, 21, 25)
- [x] Archives checked with RSA-4096 signatures before unpacking
- [x] Java 21 downloaded and ready on device
- [ ] Runtime license verified (blocks public release)
- [ ] unpack200 step and libawt dummy (needed before the game can run fully)

## Milestone 0.7.0 - Native engine (complete in code)
- [x] 0.7.0a: NDK 29 and CMake 3.22.1 build a native library in CI
- [x] 0.7.0b: mojoexec built from a pinned commit in CI
- [x] 0.7.0c: launcher hooks (mojort) built from the pinned PojavLauncher fork in CI
- [ ] 0.7.0d: GLFW (window)
- [ ] 0.7.0e: MojoSDL
- [ ] 0.7.0f: native LWJGL libraries extracted from the game's natives

## Milestone 0.8.0 - First launch attempt (in progress)
- [x] JNI bridge classes and a minimal launcher (GameLauncher) written
- [x] "Start game (first test)" button on the Downloads screen
- [ ] CI build passes
- [ ] First run on a phone: JVM starts (result to be recorded)
- [ ] Minecraft main menu appears (needs GLFW, natives and touch input)

## Later milestones
- [ ] Settings saved between launches
- [ ] Microsoft login
- [ ] Engine API contracts
- [ ] Touch controls
- [ ] Logs and diagnostics

## Milestone 0.7.0d - GLFW and MojoSDL build (in progress)
- [x] Pinned MojoLauncher GLFW (`7900e19`) and MojoSDL (`b81efe9`), with the fork's build options
- [x] mojoexec pin matched to the fork (`27790c5`)
- [ ] CI build passes (arm64 `libglfw.so` and `libSDL3.so` built, not linked yet)
- [ ] Java bridge classes (`git.artdeell.dnbootstrap.glfw`, `git.mojo.sdl`) added, next step
