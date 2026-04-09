# Contributing

Thanks for helping maintain this fork of Comfer.

## Local Setup

You will need:

- Java 17
- Android SDK Platform 36
- Android SDK Build-Tools 36.0.0
- Android SDK Platform-Tools

Recommended Windows setup:

- `scoop install temurin17-jdk`
- `scoop install android-studio`
- Install the Android SDK components above through Android Studio or `sdkmanager`

If your shell is not already using Java 17, set it before building:

```powershell
$env:JAVA_HOME='C:\Users\<you>\scoop\apps\temurin17-jdk\current'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

If the Android SDK is not auto-detected, point Gradle at it:

```powershell
$env:ANDROID_SDK_ROOT="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME=$env:ANDROID_SDK_ROOT
```

## Build Commands

Compile Kotlin only:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Build a debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Pull Requests

- Keep changes focused on one bug or feature.
- Explain the root cause and the user-visible impact.
- Include the validation you ran.
- If a change is not tested on-device, say so plainly.

## Releases

The GitHub Actions release workflow can publish builds from this fork.

- Tag pushes like `v33.1.0` create a GitHub release.
- Signed release APKs require these repository secrets:
  - `ANDROID_KEYSTORE_BASE64`
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
- Without those secrets, the workflow still builds artifacts for verification, but release APKs will remain unsigned.
