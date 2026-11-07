#!/usr/bin/env bash
# Void Launcher 0.7.0e (Phase 2) - direct integration of the GLFW, MojoSDL and MojoExec Java bindings,
# plus an Engine Adapter that loads the engine's native libraries at app start (a test you can see in the log).
#  - Java bindings are copied from the pinned upstream commits into app/src/main/java/git/...
#  - Licence files are copied into third_party/licenses/. ReLinker (Apache-2.0) is a Gradle dependency.
#  - Not included on purpose: Twitch SDK (proprietary) and NoBlocklist.
# Run from the ROOT of your Void-launcher-2 repository. Needs git and network access.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi
REPO="$(pwd)"

# ---------------------------------------------------------------------------
# 1. Fetch the exact pinned upstream commits (outside the repo, in your home folder).
SRC="$HOME/void-engine-src"
rm -rf "$SRC"
mkdir -p "$SRC"
clone_pin() {  # name url sha
  git clone -q --no-checkout --filter=blob:none "$2" "$SRC/$1"
  git -C "$SRC/$1" checkout -q "$3"
  echo "$1 at $(git -C "$SRC/$1" rev-parse --short HEAD)"
}
clone_pin glfw    https://github.com/MojoLauncher/glfw.git    7900e19fd2dbcad5abb4affc3b9d56c27e164c59
clone_pin mojosdl https://github.com/MojoLauncher/MojoSDL.git b81efe99684acc5ec7e0ed94d941ac8e1c89bd42
clone_pin mojoexec https://github.com/MojoLauncher/mojoexec.git 27790c525c3f572d9611bfa05c532014be367ca0

# ---------------------------------------------------------------------------
# 2. Copy the Java bindings into the app (same package names, so the native libraries register correctly).
J=app/src/main/java
mkdir -p "$J/git/artdeell/mojoexec" "$J/git/artdeell/dnbootstrap" "$J/git/mojo/sdl" third_party/licenses app/src/main/java/com/voidlauncher/app/engine
cp "$SRC/mojoexec/jni_bindings/src/main/java/git/artdeell/mojoexec/MojoExec.java" "$J/git/artdeell/mojoexec/"
cp -r "$SRC/glfw/jni_bindings/src/main/java/git/artdeell/dnbootstrap/." "$J/git/artdeell/dnbootstrap/"
cp -r "$SRC/mojosdl/jni_bindings/src/main/java/git/mojo/sdl/." "$J/git/mojo/sdl/"

# ---------------------------------------------------------------------------
# 3. Licence files from the upstream projects.
cp "$SRC/glfw/LICENSE.md"       third_party/licenses/GLFW-LICENSE.md
cp "$SRC/mojosdl/LICENSE.txt"   third_party/licenses/MojoSDL-LICENSE.txt
cp "$SRC/mojoexec/LICENSE"      third_party/licenses/mojoexec-LICENSE.txt

cat > third_party/licenses/ReLinker-NOTICE.md << 'MD_EOF'
# ReLinker notice

ReLinker (https://github.com/KeepSafe/ReLinker) is used through a Gradle dependency,
`com.getkeepsafe.relinker:relinker:1.2.3`, by the MojoSDL Java bindings.

- **Licence:** Apache License, Version 2.0 (full text: https://www.apache.org/licenses/LICENSE-2.0)
- **Copyright:** Keepsafe Software Inc.
MD_EOF

# ---------------------------------------------------------------------------
# 4. Gradle dependencies (annotations and ReLinker), added before the debug tooling line.
perl -0pi -e 's/\n    debugImplementation\(libs\.androidx\.compose\.ui\.tooling\)/\n    \/\/ Dependencies of the GLFW, MojoSDL and MojoExec Java bindings (see THIRD_PARTY.md).\n    implementation("androidx.annotation:annotation-jvm:1.10.0")\n    implementation("com.getkeepsafe.relinker:relinker:1.2.3")\n\n    debugImplementation(libs.androidx.compose.ui.tooling)/' app/build.gradle.kts
if ! grep -q 'relinker:1.2.3' app/build.gradle.kts; then
  echo "ERROR: could not add the dependencies to app/build.gradle.kts. Send me this message."
  exit 1
fi

# ---------------------------------------------------------------------------
# 5. Engine Adapter: the only place that loads the engine's native libraries.
cat > app/src/main/java/com/voidlauncher/app/engine/EngineAdapter.kt << 'KT_EOF'
package com.voidlauncher.app.engine

/**
 * Engine-independent contract. Screens and the launcher call this interface, never the
 * PojavLauncher-derived classes directly. Another engine can replace the implementation later.
 */
interface EngineAdapter {
    /** Loads each native library the engine needs. The result is keyed by library name. */
    fun loadNativeLibraries(): Map<String, Result<Unit>>
}
KT_EOF

cat > app/src/main/java/com/voidlauncher/app/engine/PojavEngineAdapter.kt << 'KT_EOF'
package com.voidlauncher.app.engine

/**
 * Engine adapter for the PojavLauncher-derived code: MojoExec, GLFW and MojoSDL.
 * The Java bindings are in app/src/main/java/git (licences in third_party/licenses).
 */
class PojavEngineAdapter : EngineAdapter {

    private val libraries = listOf("mojoexec", "glfw", "SDL3")

    override fun loadNativeLibraries(): Map<String, Result<Unit>> =
        libraries.associateWith { name -> runCatching { System.loadLibrary(name) } }
}
KT_EOF

# ---------------------------------------------------------------------------
# 6. VoidApplication: at start, record whether each engine library loads (the test for this phase).
cat > app/src/main/java/com/voidlauncher/app/VoidApplication.kt << 'KT_EOF'
package com.voidlauncher.app

import android.app.Application
import com.voidlauncher.app.data.log.AppLog
import com.voidlauncher.app.engine.EngineAdapter
import com.voidlauncher.app.engine.PojavEngineAdapter

class VoidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.write("App started")
        checkEngineLibraries(PojavEngineAdapter())
    }

    /** Phase 2 test: each engine library should load. The result goes to the launch log. */
    private fun checkEngineLibraries(engine: EngineAdapter) {
        engine.loadNativeLibraries().forEach { (name, result) ->
            val failure = result.exceptionOrNull()
            AppLog.write(
                if (failure == null) "Engine library $name: loaded"
                else "Engine library $name: ${failure.message ?: failure.javaClass.simpleName}"
            )
        }
    }
}
KT_EOF

# ---------------------------------------------------------------------------
# 7. Notices and checklist.
cat >> THIRD_PARTY.md << 'MD_EOF'

## Java bindings for GLFW, MojoSDL and MojoExec (copied into app/src/main/java/git)
- **Sources:** MojoLauncher GLFW `7900e19` (`jni_bindings`), MojoSDL `b81efe9` (`jni_bindings`), mojoexec `27790c5` (`jni_bindings`)
- **Licences:** GLFW zlib/libpng (`third_party/licenses/GLFW-LICENSE.md`), MojoSDL zlib (`third_party/licenses/MojoSDL-LICENSE.txt`), mojoexec MIT (`third_party/licenses/mojoexec-LICENSE.txt`)
- **Package names are unchanged** on purpose, because the native libraries register methods by these names.
- **Not included:** Twitch SDK (proprietary) and the NoBlocklist submodule.

## ReLinker 1.2.3 (Gradle dependency)
- **Licence:** Apache-2.0 (`third_party/licenses/ReLinker-NOTICE.md`)
MD_EOF

cat >> CHECKLIST.md << 'MD_EOF'

## Phase 2 - Java bindings and Engine Adapter (in progress)
- [x] GLFW, MojoSDL and MojoExec Java bindings integrated directly, with licence notices
- [x] ReLinker 1.2.3 dependency (Apache-2.0)
- [x] Engine Adapter (`engine/EngineAdapter.kt`, `PojavEngineAdapter.kt`) loads mojoexec, glfw and SDL3 at app start
- [ ] CI build passes
- [ ] On the phone, the launch log shows "Engine library ... loaded" for all three
MD_EOF

# ---------------------------------------------------------------------------
echo
echo "Done. Phase 2 files written (Java bindings, Engine Adapter, licences, Gradle dependencies)."
echo "Next:  git add . && git commit -m \"Phase 2: GLFW, MojoSDL and MojoExec Java bindings and Engine Adapter\" && git push"
echo "Test:  after the build passes, open the app once, then open Downloads and check the launch log for 'Engine library'."
