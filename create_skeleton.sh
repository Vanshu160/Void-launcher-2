#!/data/data/com.termux/files/usr/bin/bash
# Void Launcher 0.1.0 - Step 1: project skeleton + CI build.
# Run this from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder (no .git found)."
  exit 1
fi

for f in settings.gradle.kts build.gradle.kts gradle.properties app .github .gitignore CHECKLIST.md; do
  if [ -e "$f" ]; then
    echo "ERROR: '$f' already exists. Nothing was changed."
    exit 1
  fi
done

mkdir -p gradle
mkdir -p .github/workflows
mkdir -p app/src/main/java/com/voidlauncher/app
mkdir -p app/src/main/res/values
mkdir -p app/src/main/res/drawable
mkdir -p app/src/main/res/mipmap-anydpi

# ---------------------------------------------------------------- .gitignore
cat > .gitignore << 'EOF'
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
build/
/captures
.externalNativeBuild
.cxx
*.apk
*.aab
*.jks
*.keystore
EOF

# ------------------------------------------------------- settings.gradle.kts
cat > settings.gradle.kts << 'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VoidLauncher"
include(":app")
EOF

# ----------------------------------------------------------- build.gradle.kts
cat > build.gradle.kts << 'EOF'
// Top-level build file. Plugins are declared here and applied in modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
EOF

# ---------------------------------------------------------- gradle.properties
cat > gradle.properties << 'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
EOF

# ------------------------------------------------------ libs.versions.toml
cat > gradle/libs.versions.toml << 'EOF'
[versions]
agp = "9.5.0"
kotlin = "2.4.21"
composeBom = "2026.09.00"
activityCompose = "1.13.0"

[libraries]
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
EOF

# --------------------------------------------------------- app/build.gradle.kts
cat > app/build.gradle.kts << 'EOF'
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.voidlauncher.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.voidlauncher.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
EOF

# ------------------------------------------------------------- AndroidManifest
cat > app/src/main/AndroidManifest.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".VoidApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.VoidLauncher">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.VoidLauncher">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
EOF

# ------------------------------------------------------------------ Kotlin
cat > app/src/main/java/com/voidlauncher/app/VoidApplication.kt << 'EOF'
package com.voidlauncher.app

import android.app.Application

class VoidApplication : Application()
EOF

cat > app/src/main/java/com/voidlauncher/app/MainActivity.kt << 'EOF'
package com.voidlauncher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A0A0F),
                    primary = Color(0xFF9D4DFF),
                ),
            ) {
                VoidPlaceholder()
            }
        }
    }
}

@Composable
fun VoidPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Void Launcher",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VoidPlaceholderPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        VoidPlaceholder()
    }
}
EOF

# --------------------------------------------------------------- resources
cat > app/src/main/res/values/strings.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Void Launcher</string>
</resources>
EOF

cat > app/src/main/res/values/colors.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="void_background">#0A0A0F</color>
    <color name="ic_launcher_background">#0A0A0F</color>
</resources>
EOF

cat > app/src/main/res/values/themes.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.VoidLauncher" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/void_background</item>
        <item name="android:colorBackground">@color/void_background</item>
    </style>
</resources>
EOF

cat > app/src/main/res/mipmap-anydpi/ic_launcher.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
EOF

cat > app/src/main/res/drawable/ic_launcher_foreground.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M54,54m-22,0a22,22 0,1 1,44 0a22,22 0,1 1,-44 0"
        android:strokeWidth="6"
        android:strokeColor="#9D4DFF" />
    <path
        android:pathData="M54,54m-8,0a8,8 0,1 1,16 0a8,8 0,1 1,-16 0"
        android:fillColor="#3B82F6" />
</vector>
EOF

# ------------------------------------------------------- GitHub Actions CI
cat > .github/workflows/build.yml << 'EOF'
name: Build APK

on:
  push:
    branches: [main]
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v6

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v6
        with:
          gradle-version: 9.6.0

      - name: Build debug APK
        run: gradle assembleDebug --stacktrace

      - uses: actions/upload-artifact@v7
        with:
          name: void-launcher-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
EOF

# -------------------------------------------------------------- CHECKLIST
cat > CHECKLIST.md << 'EOF'
# Void Launcher development checklist

## Milestone 0.1.0 - Foundation
- [x] GitHub repository created
- [x] First commit pushed
- [ ] Step 1: Gradle skeleton + bare Compose screen + CI build (files added, build not yet verified)
- [ ] CI build passes and APK installs on a phone
- [ ] Step 2: Theme (near-black, electric purple, blue)
- [ ] Step 3: Navigation with placeholder screens
- [ ] Step 4: Home screen with ViewModel
- [ ] Step 7: Third-party notices file

## Later milestones
- [ ] Version manager
- [ ] Downloads
- [ ] Settings (RAM and resolution)
- [ ] Microsoft login
- [ ] Java runtime management
- [ ] Engine API contracts
- [ ] Pojav adapter
- [ ] Touch controls
- [ ] Logs and diagnostics
EOF

echo
echo "Done. Files created:"
find . -path ./.git -prune -o -type f -print | sort
