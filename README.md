# Organiser

Visual wishlist organizer for Android (Kotlin + Jetpack Compose, minSdk 28).

## Features
- Boards grid with 2×2 cut-out previews, dashed "New" card, filter/sort (UI from the mockup)
- Floating bottom bar: Home (stats) · Discover · Boards, plus a separate search button
- Profile avatar (top-left) with name, currency, photo, backup export/import
- Bell (top-right): daily morning notification with a random wishlist item (time configurable, test button)
- On-device background removal (ML Kit Subject Segmentation) so only the item is shown
- Per item: price, link, note, purchased toggle, move between boards, share cut-out PNG
- Per board: totals (to buy / bought), filters (All / To buy / Bought), sorting
- Share any photo from gallery/browser to Organiser to add it
- Global search across all boards

## Get the APK
Push to GitHub → **Actions → Build APK** → download `Organiser-apk`.
Tag `v1.1` (`git tag v1.1 && git push --tags`) to attach the APK to a Release.
