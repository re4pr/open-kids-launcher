# Home Assistant Integration Guide

Open Kids Launcher connects natively to **Home Assistant** over WebSocket (`/api/websocket`) for instant, push-based mode updates and REST (`/api/events/kids_launcher_event`) for activity reporting.

Nothing is hardcoded: all server URLs, tokens, and entity IDs are configured at runtime inside **Parent Control Center → Home Assistant & Family**.

---

## 1. Recommended Home Assistant Helpers (`configuration.yaml`)

Add the following helpers in Home Assistant (via **Settings → Devices & Services → Helpers** or `configuration.yaml`):

```yaml
input_select:
  kids_tablet_mode:
    name: "Kids Tablet Mode"
    options:
      - free_play
      - school_time
      - tidy_up
      - bedtime
      - locked
    initial: free_play
    icon: mdi:tablet-dashboard

input_boolean:
  kids_tablet_lock:
    name: "Kids Tablet Instant Lock"
    icon: mdi:lock

  kids_tablet_tidy_up:
    name: "Kids Tablet Tidy-Up Time"
    icon: mdi:broom

input_number:
  kids_tablet_daily_minutes:
    name: "Kids Tablet Daily Screen Time (Minutes)"
    min: 0
    max: 360
    step: 15
    initial: 120
    unit_of_measurement: "min"
    icon: mdi:timer-outline
```

---

## 2. Connecting the App

1. In Home Assistant, open your user profile → **Security** → **Long-Lived Access Tokens** and create a token.
2. On the tablet, long-press the top-right lock icon and enter your **Parent Master Code**.
3. Open **Home Assistant & Family**, enable the toggle, and fill in:
   - **Home Assistant URL:** `http://homeassistant.local:8123` (or your HTTPS URL)
   - **Long-Lived Access Token:** *(paste token)*
   - **Mode Entity ID:** `input_select.kids_tablet_mode`
   - **Lock Switch Entity ID:** `input_boolean.kids_tablet_lock`
   - **Tidy-Up Switch Entity ID:** `input_boolean.kids_tablet_tidy_up`
   - **Daily Budget Minutes Entity ID:** `input_number.kids_tablet_daily_minutes`

Within a second, the status line will display **`Connected (WebSocket live)`**.

---

## 3. Supported Mode Values

`HomeAssistantControlProvider` maps any of the following state strings (case-insensitive, spaces or hyphens allowed):

| Launcher Mode | Accepted Home Assistant State Values |
| :--- | :--- |
| `FREE_PLAY` | `free_play`, `play`, `playtime`, `normal`, `unlocked`, `home` |
| `SCHOOL_TIME` | `school_time`, `school`, `study`, `homework`, `learning` |
| `TIDY_UP` | `tidy_up`, `tidy`, `clean_up`, `cleanup`, `chores` |
| `BEDTIME` | `bedtime`, `sleep`, `night`, `goodnight` |
| `LOCKED` | `locked`, `lock`, `paused`, `break`, `screen_off` |

---

## 4. Listening to Launcher Events in Home Assistant

When **Publish Events** is enabled, the launcher posts `kids_launcher_event` payloads whenever an app or shelf item is launched:

```yaml
automation:
  - alias: "Log Kids Tablet App Launch"
    triggers:
      - trigger: event
        event_type: kids_launcher_event
    actions:
      - action: logbook.log
        data:
          name: "Kids Launcher"
          message: "{{ trigger.event.data.device_id }} launched {{ trigger.event.data.label | default(trigger.event.data.title) }}"
```
