# Organiser

A visual wishlist organizer for Android. Create boards (Fashion, Gadgets, Skincare…), add photos of items, and the
background is removed on-device so only the cut-out item is shown on a dark board — just like the mockup.

- Kotlin + Jetpack Compose, minSdk 28
- Background removal: ML Kit Subject Segmentation (on-device, needs Google Play services)
- Long-press a board or item to delete it

## Get the APK
1. Push this repo to GitHub (branch `main`).
2. Open **Actions → Build APK → Run workflow** (it also runs on every push).
3. Download `Organiser-apk` from the finished run, unzip, and install `Organiser.apk`.
4. To publish a Release: `git tag v1.0 && git push --tags` — the APK is attached automatically.

## Build locally
Open in Android Studio and run, or `gradle assembleRelease` (Gradle 8.9, JDK 17).
