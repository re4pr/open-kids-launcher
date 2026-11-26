# Shelf Plugins & Pluggable Book Services

Open Kids Launcher keeps app integrations modular so you can connect any media center (such as **Nuvio**, **Jellyfin**, **Plex**, or a **Home Assistant** sensor) and future **e-book services** without modifying or recompiling the app.

---

## 1. JSON Feed Shelf Plugin (`JSON_FEED`)

Add a JSON Feed plugin in **Parent Control Center → Plugins & Books**. The plugin fetches your configured endpoint and maps items onto the **Home** shelf and target category menu (`Videos`, `Audio`, `Books`, or `Games`).

### Standard JSON Response Schema

```json
{
  "items": [
    {
      "id": "show-101",
      "title": "Bluey",
      "subtitle": "S3 • E12 — Sleepytime",
      "imageUrl": "https://media.example.org/posters/bluey.jpg",
      "progress": 0.45,
      "launchUri": "intent://detail/show-101#Intent;scheme=nuvio;package=com.nuvio.tv;i.profile_id=2;end",
      "packageName": "com.nuvio.tv"
    }
  ]
}
```

### Deep-Link & Intent Support
- **`intent://...` URIs:** Parsed via `Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)`, supporting custom extras (such as profile selection `i.profile_id=2` in Nuvio) and explicit component/package routing.
- **Custom URI Schemes & HTTPS:** Standard `ACTION_VIEW` intents (e.g. `netflix://...`, `https://...`, or custom media player deep links).
- **Package Fallback:** If `launchUri` is omitted or fails, the launcher falls back to launching `fallbackPackageName`.

---

## 2. Home Assistant Entity Shelf Plugin (`HOME_ASSISTANT_ENTITY`)

Already aggregating "Continue Watching" or kid playlists inside Home Assistant? Choose **+ Add HA Entity Shelf** and enter an entity ID (e.g. `sensor.kids_continue_watching`) whose `attributes.items` contains a list of media objects matching the fields above. Because it reuses the live WebSocket state cache, shelf cards update automatically whenever the sensor updates in Home Assistant!

---

## 3. Connecting Book Services (`BOOK_OPDS_OR_JSON` & `BookServiceProvider`)

By default, the **Books** tab displays a friendly *"Your Magical Bookshelf"* screen until you either:
1. Approve an Android reader app (such as Kindle, Kobo, Libby, or an offline EPUB reader) under **Apps & Menus** with category **`Books`**, **or**
2. Add a **Book Service Connector** under **Plugins & Books** pointing to a JSON/OPDS-bridge endpoint returning a `books` array (`title`, `subtitle`, `imageUrl`, `launchUri`).

Developers building native catalog integrations (such as direct OPDS XML, Kavita, Komga, or Audiobookshelf clients) can implement [`BookServiceProvider`](../app/src/main/java/io/github/re4pr/kidslauncher/plugin/PluginEngine.kt) in Kotlin.
