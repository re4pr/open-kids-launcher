# Open Kids Launcher

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: Android 9+](https://img.shields.io/badge/Android-9.0%2B%20(API%2028)-3DDC84.svg)](app/build.gradle.kts)
[![Kotlin + Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-7F52FF.svg)](app/src/main/java/io/github/re4pr/kidslauncher)

**Open Kids Launcher** is a privacy-first, open-source, dynamic Android home launcher built for kids' tablets and phones—designed from the ground up for **parental customization, native Home Assistant control, Google Family Link compatibility, pluggable media shelves, and extensible themes**.

Unlike closed commercial kids launchers, Open Kids Launcher contains **zero ads, zero cloud tracking, zero hardcoded services, and zero subscriptions**. Everything is configured by the parent behind a hidden **Master Passcode**.

---

## Key Features

### 1. Kid-First Dynamic Menus & Animated World
- **Home Menu:** Displays the child's animated custom avatar, active mode & screen-time badge, plus automatic quick-resume shelves for **Last Played Games**, **Last Played Music & Stories**, and **Continue Watching — Shows & Movies**.
- **Games Menu:** Bouncy, spring-animated cards for parent-approved games and learning apps.
- **Videos Menu:** Approved video apps (e.g., Netflix, Nuvio, YouTube Kids, Jellyfin) alongside rich poster shelves from enabled Continue Watching plugins.
- **Books Menu:** Welcoming empty bookshelf out of the box, backed by a pluggable `BookServiceProvider` / JSON book connector ready whenever you add reader apps or book services.
- **Audio Menu:** Dedicated space for kid-safe music and story players (such as offline audiobook/Yoto players or music apps) plus optional audio shelf feeds.

### 2. Google Kids Space–Inspired Avatar & World Studio
- Kids tap their animated buddy in the top banner to customize:
  - **Skin tone**, **hairstyle & hair color**, **eye expressions**, **outfits**, **crowns/horns/hats**, **companion pets**, and **theme world**.
- **Mode-Reactive Expressions:** Your child's avatar automatically puts on reading glasses during **School Time**, waves sparkles during **Tidy-Up Time**, and wears a starry nightcap with peaceful sleeping eyes at **Bedtime**!

### 3. Mode-Reactive Timings & Restrictions
- **`Playtime (FREE_PLAY)`:** Vibrant theme sky, drifting particles, all approved menus active.
- **`School & Study Time (SCHOOL_TIME)`:** Calm study atmosphere; Games & Videos hide while Books & approved learning apps stay accessible.
- **`Tidy-Up Time (TIDY_UP)`:** Energetic Tidy-Up countdown banner that pauses games & shows until tidy-up finishes.
- **`Sweet Dreams (BEDTIME)`:** Starry night sky; Games & Videos tuck in for the night while optional parent-approved Bedtime Audio remains available.
- **`Screen Break (LOCKED)`:** Friendly pause screen when the daily screen-time budget is reached or a parent locks the device.

### 4. Extensible Skin Engine (4 Built-In Skins + Custom JSON Packs)
Ships with four animated built-in themes in [`app/src/main/assets/skins/`](app/src/main/assets/skins):
- **`frost_kingdom`** (*Frost Kingdom* — icy crystal blues, aurora gradient & drifting snowflakes)
- **`rainbow_unicorn`** (*Rainbow Unicorn* — pastel clouds, cotton-candy pink & twinkling stars)
- **`royal_princess`** (*Royal Princess* — fairytale magenta, rose-gold & floating crowns)
- **`ocean_mermaid`** (*Ocean Mermaid* — coral lagoon turquoise & rising sea bubbles)

Parents can rename any theme's local display title on-device (for example, naming `frost_kingdom` *"Frozen"* or `rainbow_unicorn` *"Unicorn"* for their child) or paste/import brand-new JSON skin packs directly in Parent Settings.

### 5. Native Home Assistant + Family Link + Pluggable Family Providers
- **Native Home Assistant Client:** Built-in OkHttp WebSocket (`ws://` / `wss://`) + REST integration subscribes to your own entities (`input_select` mode, `input_boolean` lock/tidy-up switches, `input_number` daily minutes) in sub-second real time and fires `kids_launcher_event` updates back to Home Assistant.
- **Google Family Link / Android Supervision Bridge:** Automatically detects OS-level `PackageManager.isPackageSuspended()` app pauses enforced by Family Link and provides a shortcut to Android's system parental controls.
- **Extensible `ParentalControlProvider` SPI:** Clean Kotlin interface allowing contributors to add NextDNS, MQTT, Bark, or custom REST family providers.

### 6. Customizable Media & Book Shelf Plugins
- Nothing is hardcoded: add generic **JSON HTTP Feeds** (ideal for Nuvio Continue Watching, Jellyfin, Plex, or custom LAN bridges) or **Home Assistant Entity Attribute Shelves** (`sensor.continue_watching`) straight from the Parent Control Center.

---

## First-Time Setup & Hidden Parent Gate

1. Install the APK (or build from source below) and launch **Open Kids Launcher**.
2. **Open the Hidden Parent Gate:** Press and **hold (long-press)** the subtle lock icon in the top-right corner of the kid banner. *(Single taps only show a brief hint toast so young children don't stumble into password prompts.)*
3. **Create Your Master Code:** On first use, enter and confirm a Parent Master Code (4+ characters). Passcodes are hashed locally with salted `PBKDF2WithHmacSHA256` and never stored in plaintext.
4. Inside **Parent Control Center**:
   - **Apps & Menus:** Toggle **Allowed** on the apps you want visible and assign each to `Games`, `Videos`, `Books`, `Audio`, or `Learning`.
   - **Timings & Modes:** Configure Bedtime hours, School Time hours, and Daily Screen Time budget, and tap **Choose Default Home App** to set Open Kids Launcher as the device's Home screen.
   - **Skins & Themes:** Pick a default skin, customize local display names, or import JSON skin packs.
   - **Home Assistant & Family:** Optionally connect your Home Assistant URL & Long-Lived Access Token.
   - **Plugins & Books:** Optionally add Continue Watching JSON feeds, HA entity shelves, or book connectors.

---

## Documentation

- [Home Assistant Setup & Example YAML](docs/HOME_ASSISTANT.md)
- [Plugins, Continue Watching Feeds & Book Services](docs/PLUGINS_AND_BOOKS.md)
- [Creating Custom Skins & Family Providers](docs/CUSTOM_SKINS_AND_PROVIDERS.md)

---

## Building from Source

### Prerequisites
- **JDK 21**
- **Android SDK** (`compileSdk = 36`, `minSdk = 28`)

### Build & Test
```bash
cp local.properties.example local.properties
# Edit local.properties so sdk.dir points to your Android SDK

./gradlew testDebugUnitTest assembleDebug assembleRelease
```

The built APK is written to `app/build/outputs/apk/release/app-release.apk`.

---

## Privacy & Security

- **Zero Telemetry / Analytics:** No third-party analytics, crash trackers, or ad SDKs.
- **Local-First Storage:** Settings, salted PBKDF2 PIN hashes, and optional Home Assistant tokens are stored strictly in Android `MODE_PRIVATE` app storage (`allowBackup="false"`).
- **Network Scope:** Network access is idle unless the parent explicitly configures a Home Assistant server or external shelf plugin URL.

## License

Released under the [MIT License](LICENSE).
