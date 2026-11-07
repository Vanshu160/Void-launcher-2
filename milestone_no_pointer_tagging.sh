#!/usr/bin/env bash
# Void Launcher 0.8.4 - turn off Android native pointer tagging for the app.
# The JVM prints "Pointer tag ... was truncated" and the app crashes. Android 11+ checks
# pointer tags in apps targeting SDK 30 or higher, and this app targets 36.
# This is a test workaround: it switches off that check, and the JVM's pointers are not changed.
# Run from the ROOT of your Void-launcher-2 repository.
set -e

if [ ! -d .git ] || [ ! -f app/build.gradle.kts ]; then
  echo "ERROR: run this inside the Void-launcher-2 repository folder."
  exit 1
fi

# ---------------------------------------------------------------------------
cat > app/src/main/AndroidManifest.xml << 'XML_EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".VoidApplication"
        android:allowBackup="false"
        android:allowNativeHeapPointerTagging="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.VoidLauncher">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="sensorLandscape"
            android:theme="@style/Theme.VoidLauncher">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
XML_EOF

# ---------------------------------------------------------------------------
echo
echo "Done. Manifest updated for milestone 0.8.4 (pointer tagging off, test workaround)."
echo "Next:  git add . && git commit -m \"Milestone 0.8.4: disable native pointer tagging (test)\" && git push"
