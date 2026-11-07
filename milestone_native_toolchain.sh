#!/usr/bin/env bash
# Void Launcher 0.7.0a - native (NDK + CMake) toolchain check.
# Adds a tiny native library so CI proves the NDK build works. Nothing calls it yet.
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

mkdir -p app/src/main/cpp

# ---------------------------------------------------------------------------
cat > app/build.gradle.kts << 'KTS_EOF'
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.voidlauncher.app"
    compileSdk = 37
    // Same NDK the PojavLauncher engine is built with.
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.voidlauncher.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Java runtime archives are tar.xz files.
    implementation(libs.commons.compress)
    implementation(libs.xz)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
KTS_EOF

# ---------------------------------------------------------------------------
cat > app/src/main/cpp/CMakeLists.txt << 'CMAKE_EOF'
cmake_minimum_required(VERSION 3.22.1)
project("voidnative")

add_library(voidnative SHARED voidnative.c)

find_library(log-lib log)
target_link_libraries(voidnative ${log-lib})
CMAKE_EOF

# ---------------------------------------------------------------------------
cat > app/src/main/cpp/voidnative.c << 'C_EOF'
#include <jni.h>

// Placeholder native entry point. Later milestones replace this with the engine bridge.
JNIEXPORT jstring JNICALL
Java_com_voidlauncher_app_NativeBridge_nativeVersion(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, "voidnative 0.7.0a");
}
C_EOF

# ---------------------------------------------------------------------------
cat > CHECKLIST.md << 'MD_EOF'
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
- [ ] unpack200 step and libawt dummy (needed before the engine can run)

## Milestone 0.7.0 - Native engine (in progress)
- [x] 0.7.0a: NDK 29 and CMake 3.22.1 build a native library in CI
- [ ] 0.7.0b: mojoexec and launcher hooks (mojort)
- [ ] 0.7.0c: GLFW and MojoSDL

## Later milestones
- [ ] Settings saved between launches
- [ ] Microsoft login
- [ ] Engine API contracts
- [ ] Pojav adapter (first game launch)
- [ ] Touch controls
- [ ] Logs and diagnostics
MD_EOF

echo
echo "Done. Files written for milestone 0.7.0a."
echo "Next:  git add . && git commit -m \"Milestone 0.7.0a: native toolchain check\" && git push"
