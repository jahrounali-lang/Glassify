# Liquid Glass Clock

Native Android (Kotlin, Compose, AGSL) lock-screen clock with a real refraction shader.

## Feasibility
No public Android or Samsung One UI API lets a third-party app place its own clock on the lock screen.
The closest real, supported integration is a **live wallpaper** (`ClockWallpaperService`): the system renders it behind the
lock UI. It draws the wallpaper itself, so the glass refracts the real background. Authentication is never touched.
Limits: Samsung's own clock stays drawn above the wallpaper; AOD is system-owned; a privileged SystemUI plugin would need
platform signing/root and is not included.

## Build with GitHub Actions
1. Push this folder to a GitHub repo. The workflow `.github/workflows/android.yml` runs on every push.
2. Download the APKs from Actions > the run > Artifacts (`LiquidGlassClock-debug`, `LiquidGlassClock-release`).
3. Optional release signing secrets: `KEYSTORE_BASE64` (`base64 -w0 my.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
   Without them CI signs with a throwaway key (installable, not for Play Store).
4. Tag `v1.0.0` to publish the release APK on the GitHub Releases page.

## Local build
Open in Android Studio (it creates the Gradle wrapper), or run `gradle assembleDebug` / `gradle assembleRelease` with Gradle 8.10+ and JDK 17.

## Font
GooglePixelInflat is proprietary and not bundled. Put `GooglePixelInflat.ttf` in `app/src/main/assets/fonts/`, or import it in the
Type tab. Until then the UI shows a warning, not a silent substitution.

## Install and use
Install the APK, open the app, design the clock, then Apply > Apply as live wallpaper > Lock screen.
