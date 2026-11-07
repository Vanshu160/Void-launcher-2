# Third-party notices

Void Launcher is licensed under the GNU General Public License v3.0 (see `LICENSE`).

## Planned: PojavLauncher engine components
- **Project:** PojavLauncher (fork used as reference: https://github.com/Vanshu160/PojavLauncher)
- **License:** GNU Lesser General Public License v3.0 (LGPL-3.0), confirmed from the `LICENSE` file of that fork
- **Status:** No PojavLauncher code has been copied into Void Launcher yet.
- **Rule:** Any copied or adapted PojavLauncher file keeps its original LGPL-3.0 copyright notice, and the change is recorded here with the file name.

## Libraries (via Gradle)
These are downloaded at build time and are not copied into this repository.

| Library | Use | License |
|---|---|---|
| Kotlin and kotlinx | Language and coroutines | Apache-2.0 |
| AndroidX / Jetpack Compose (Material 3, icons, navigation, lifecycle, activity) | UI and architecture | Apache-2.0 |

Check each library's own license before adding it.

## Java runtime (downloaded on first use)
- **Source:** https://mojolauncher.github.io/jre-download/, the runtime host used by PojavLauncher.
- **Status: license of these runtimes is NOT yet verified.** Confirm it before any public release.
- **Signature certificate:** `app/src/main/assets/runtime-cert.pem`, copied from the MojoLauncher PojavLauncher fork (LGPL-3.0), file `app_pojavlauncher/src/main/assets/cert.pem`. It is used only to check runtime signatures.

## Libraries added in 0.6.0
| Library | Use | License |
|---|---|---|
| Apache Commons Compress 1.27.1 | Reads tar archives | Apache-2.0 |
| XZ for Java 1.10 | Decompresses .xz archives | Public domain (per project) |
