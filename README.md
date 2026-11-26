# Open Kids Launcher

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: Android 9+](https://img.shields.io/badge/Android-9.0%2B%20(API%2028)-3DDC84.svg)](app/build.gradle.kts)
[![Kotlin + Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-7F52FF.svg)](app/src/main/java/io/github/re4pr/kidslauncher)

**Open Kids Launcher** is a privacy-first, open-source, dynamic Android home launcher built for kids' tablets and phones—designed from the ground up for **parental customization, native Home Assistant control, Google Family Link compatibility, pluggable media shelves, and extensible themes**.

Unlike closed commercial kids launchers, Open Kids Launcher contains **zero ads, zero cloud tracking, zero hardcoded services, and zero subscriptions**. Everything is configured by the parent behind a hidden **Master Passcode**.

---

## Key Features

### 1. Kid-First Dynamic Menus & Smart Zero-Config App Discovery
- **Automatic Kid App Discovery:** Out of the box, user-installed games, video apps (e.g., Netflix, Nuvio, YouTube, Jellyfin), and kid audio apps automatically appear in the right menus—while Android settings, browsers, app stores, VPNs, and utility helpers stay safely hidden unless a parent explicitly enables them.
- **Home Menu:** Features an interactive **Scenic 3D Hero Stage** (where the child's full-body 3D character & 3D pet stand right inside the living theme world), quick theme world pills, and frosted-glass shelves for **Games**, **Shows & Movies**, and **Music & Audio Stories**.
- **Games Menu:** Bouncy, spring-animated cards for approved games and learning apps.
- **Videos Menu:** Video apps alongside rich poster shelves from enabled Continue Watching plugins.
- **Books Menu:** Welcoming empty bookshelf out of the box, backed by a pluggable `BookServiceProvider` / JSON book connector ready whenever you add reader apps or book services.
- **Audio Menu:** Dedicated space for kid-safe music and story players plus optional audio shelf feeds.

### 2. Google Kids Space–Inspired 2.5D Storybook Puppet & 16 3D-Shaded Pets
- Uses a **2.5D airbrushed skeletal puppet rig** paired with high-resolution 3D-shaded props and companion pets—matching the 2.5D layered puppet approach used by Google Kids Space.
- Kids can **swipe horizontally for 2.5D parallax tilt**, **tap to jump & sparkle**, or tap **Dress Up** to customize across 7 categories:
  - **16 3D-Shaded Sidekick Pets:** Winged Baby Unicorn, Golden Puppy, Royal Poodle, Tabby Kitten, Fluffy Bunny, Emerald Dragon, Baby Arctic Seal, Lagoon Dolphin, Sea Turtle, Royal Swan, Pink Flamingo, Snowy Owl, Baby Panda, Cuddly Koala, Cute Hamster, and Star Companion Bot.
  - **Storybook Dresses & Outfits:** 12 illustrated silhouettes (Frost Crystal Gown, Princess Rose Ballgown, Unicorn Rainbow Tulle, Mermaid Pearl Fin Gown, Fairy Blossom Dress, Ballerina Tutu, Star Hoodie & Jeans, Overalls, School Blazer, Astronaut Suit, Pajamas) + 10 color dyes.
  - **Layered Hairstyles:** 12 multi-layered hairstyles (Flowing Waves, Thick Elsa Side Braid, Twin Braids, High Ponytail, Pigtails, Cloud Curls, Space Buns, Bob, Crown Braid) in 12 shades.
  - **Face, Eyes, Crowns & Animated Wings:** 8 skin tones, rosy blush/freckles/star cheeks, 6 Disney-lash eye styles in 8 iris colors, 3D crowns/unicorn horns/bows/headphones, flapping 3D feathered Pegasus & butterfly fairy wings, and 3D magic wands.

### 3. Seamless Live Video Wallpaper Worlds (`ExoPlayer` H.264 `.mp4`)
Ships with five enchanted 3D storybook worlds (`assets/videos/*.mp4` + `assets/scenes/*.jpg`) played as hardware-decoded, zero-CPU-overhead looping live video wallpapers (`Player.REPEAT_MODE_ONE`, automatically paused when backgrounded), plus custom `.mp4` / `.gif` / `.webp` skin support!
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

## License & Asset Credits

Released under the [MIT License](LICENSE). Selected 3D-shaded prop and animal companion sprites in `assets/puppet/` incorporate artwork from [Microsoft Fluent UI Emoji](https://github.com/microsoft/fluentui-emoji) (MIT License).
