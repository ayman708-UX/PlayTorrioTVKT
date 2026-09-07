# Contributing to PlayTorrio TV

Thanks for your interest in contributing to PlayTorrio TV! Whether you're fixing a bug, improving TV navigation, adding translations, or proposing new features, contributions from the community are always welcome.

## Getting Started

### Prerequisites
- **Android Studio** (Ladybug or newer recommended)
- **JDK 17+**
- **Android SDK** with API Level 36 and NDK 28

### Development Setup

1. **Fork and clone** the repository:
   ```bash
   git clone https://github.com/PlayTorrioMedia/PlayTorrioTV.git
   cd PlayTorrioTV
   ```

2. **Open in Android Studio** and let Gradle sync.

3. **Build and install** onto your connected Android TV or emulator:
   ```bash
   # Build debug APK
   ./gradlew assembleFullDebug

   # Install directly via ADB
   adb install -r app/build/outputs/apk/full/debug/app-full-arm64-v8a-debug.apk
   ```

---

## Reporting Bugs

If you find a bug or crash:
1. Check existing GitHub Issues to see if it has already been reported.
2. If not, open a new issue with:
   - **Device & Android Version**: e.g., Chromecast with Google TV, Android 12
   - **App Version**: Release tag or commit hash
   - **Steps to Reproduce**: Clear, step-by-step instructions
   - **Expected vs Actual Behavior**: What should have happened vs what actually happened
   - **Logs**: If the app crashed, please attach relevant `adb logcat` output:
     ```bash
     adb logcat -d | grep -i "playtorrio"
     ```

---

## Suggesting Features

Have an idea to make PlayTorrio TV better?
- Open an issue describing the feature, the problem it solves, and how you imagine it working.
- For large architectural changes or new core sections, discussing them in an issue first helps align on design before spending time writing code.

---

## Pull Request Guidelines

To keep the review process smooth:
- **Focus**: Keep each pull request focused on one specific bug fix or feature. Avoid bundling unrelated refactors.
- **TV-Friendly UI**: PlayTorrio TV is designed for television screens and remote controls. Ensure all UI changes:
  - Work seamlessly with D-pad navigation (Up, Down, Left, Right, Select, Back).
  - Provide visible focus states on all interactive elements.
  - Test well on standard 1080p and 4K displays.
- **Testing**: Test your changes on a physical Android TV device or an Android TV emulator before submitting.
- **Visuals**: For any UI or layout changes, please attach screenshots or a short recording to your pull request.
- **Code Quality**: Follow standard Kotlin coding conventions and Jetpack Compose best practices.

---

## Translations & Localization

Translations help make PlayTorrio accessible to everyone around the world.
- Translation files are located under `app/src/main/res/values-<lang>/strings.xml`.
- If you'd like to add a new language or improve existing translations, feel free to open a PR!

---

## Community & Questions

If you have questions about setup or need guidance on an issue, feel free to open a discussion or reach out via our GitHub Issues.
