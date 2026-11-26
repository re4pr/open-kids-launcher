# Custom Skins & Family Control Providers

## 1. Creating & Sharing Skins (`SkinDefinition` JSON)

Themes in Open Kids Launcher are 100% data-driven JSON files. Built-in themes live in [`app/src/main/assets/skins/`](../app/src/main/assets/skins), and users can paste new JSON skins directly into **Parent Control Center → Skins & Themes** without rebuilding the APK.

### `SkinDefinition` Example

```json
{
  "id": "dino_jungle",
  "name": "Dino Jungle",
  "tagline": "Lush ferns, sunshine & friendly dinosaurs",
  "sceneAssetPath": "",
  "customSceneUri": "https://example.org/themes/dino_jungle.gif",
  "skyTopHex": "#1B4332",
  "skyMidHex": "#2D6A4F",
  "skyBottomHex": "#B7E4C7",
  "surfaceCardHex": "#F4FFF8",
  "accentPrimaryHex": "#2D6A4F",
  "accentSecondaryHex": "#FFB703",
  "badgeHighlightHex": "#40916C",
  "textPrimaryHex": "#081C15",
  "textOnSkyHex": "#FFFFFF",
  "particleStyle": "RAINBOW_SPARKLES",
  "cardCornerDp": 28
}
```

### Available `particleStyle` Values
- `SNOWFLAKES` — 6-point crystal snowflakes drifting across the sky
- `RAINBOW_SPARKLES` — Twinkling 4-point magic stars & sparkles
- `CROWNS_AND_HEARTS` — Floating golden mini-crowns & pastel stars
- `OCEAN_BUBBLES` — Translucent rising lagoon bubbles
- `STARLIGHT` — Nighttime crescent moon & glowing starlight (also activated automatically during `BEDTIME`)

---

## 2. Adding a New Family Control Provider (`ParentalControlProvider`)

Open Kids Launcher ships with three providers out of the box:
1. **`LocalScheduleProvider`** — On-device Bedtime, School Time, Tidy-Up timer, and Daily Screen Time budget.
2. **`HomeAssistantControlProvider`** — Live WebSocket + REST bidirectional Home Assistant control.
3. **`FamilyLinkBridgeProvider`** — Listens to Android OS `ACTION_PACKAGES_SUSPENDED` & `UserManager` restrictions enforced by Google Family Link.

### Implementing a New Provider (e.g., MQTT, NextDNS, Custom MDM)

Implement [`ParentalControlProvider`](../app/src/main/java/io/github/re4pr/kidslauncher/provider/ParentalControlProvider.kt):

```kotlin
class CustomFamilyProvider : ParentalControlProvider {
    override val id: String = "custom_family_mdm"
    override val displayName: String = "Custom Family Service"
    override val directive: StateFlow<ProviderDirective> = ...

    override fun start() { /* connect / poll */ }
    override fun stop() { /* clean up */ }
}
```

Include its `directive` flow inside `LauncherViewModel`'s `effectivePolicy` combiner; `PolicyResolver` automatically merges mode priorities and updates the theme, avatar, and allowed tabs in real time.
