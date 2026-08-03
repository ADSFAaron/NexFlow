# NexFlow `.flow` Format Specification

> Schema Version: 1  
> File Extension: `.flow`  
> Encoding: UTF-8 JSON

---

## Overview

A `.flow` file is a UTF-8 encoded JSON document describing a single automation flow. The root object contains metadata, trigger definitions, optional conditions, and an ordered action list.

---

## Root Object

| Field | Type | Required | Description |
|---|---|---|---|
| `schema_version` | `integer` | ✅ | Schema revision (currently `1`). Increment when breaking changes occur. |
| `id` | `string (UUID v4)` | ✅ | Globally unique identifier for this flow. Must conform to UUID v4 format: `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`. |
| `name` | `string` | ✅ | Human-readable display name. Max 100 characters. |
| `description` | `string` | ✅ | Detailed description of what this flow does. May be empty string. Max 2000 characters. |
| `author` | `string \| null` | ❌ | Display name of the flow author. `null` if anonymous. Max 50 characters. |
| `tags` | `string[]` | ✅ | Array of lowercase tag strings for search/filter. May be empty array `[]`. Each tag max 30 chars. |
| `enabled` | `boolean` | ✅ | Whether this flow is active. Disabled flows are stored but not monitored. |
| `created_at` | `string (ISO 8601)` | ✅ | Creation timestamp in UTC. Format: `2026-06-03T12:00:00Z`. |
| `updated_at` | `string (ISO 8601)` | ✅ | Last modification timestamp in UTC. Must be ≥ `created_at`. |
| `triggers` | `Trigger[]` | ✅ | One or more trigger definitions. Must contain at least 1 item. |
| `trigger_logic` | `"ANY" \| "ALL"` | ✅ | `ANY`: the flow runs as soon as any trigger fires. `ALL`: every trigger must fire within 5 minutes of the others before the flow runs — see below. |
| `conditions` | `Condition[]` | ✅ | Zero or more conditions evaluated before actions run. May be empty array `[]`. |
| `actions` | `Action[]` | ✅ | Ordered list of actions to execute. Must contain at least 1 item. |
| `variables` | `Variable[]` | ✅ | Flow-scoped variables. May be empty array `[]`. |
| `global_variables` | `GlobalVariable[]` | ❌ | Declarations of the global variables this flow references as `{{g:name}}`. Omitted when the flow uses none, and absent from files written before globals existed. |

---

## Trigger Object

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `string (UUID v4)` | ✅ | Unique identifier within this flow. |
| `type` | `TriggerType` | ✅ | Trigger category. See **TriggerType** table below. |
| `config` | `object` | ✅ | Type-specific configuration. Schema depends on `type`. May be `{}` for parameterless triggers. |

### TriggerType Values

| Value | Description | Example `config` |
|---|---|---|
| `TIME` | Time-based trigger (one-shot or recurring) | `{"hour": 7, "minute": 30, "repeat": "DAILY"}` |
| `BATTERY` | Battery level crosses threshold | `{"threshold": 20, "direction": "BELOW"}` |
| `BLUETOOTH` | Bluetooth device connected/disconnected | `{"device_name": "My Headphones", "event": "CONNECTED"}` |
| `WIFI` | Wi-Fi network connected/disconnected | `{"ssid": "HomeWifi", "event": "CONNECTED"}` |
| `SCREEN` | Screen turned on or off | `{"event": "ON"}` |
| `APP_LAUNCH` | Specific app moved to foreground | `{"package": "com.example.app"}` |
| `INCOMING_CALL` | Incoming phone call | `{"contact_filter": null}` |
| `SMS_RECEIVED` | SMS received | `{"sender": "0912", "body_keyword": "code", "match_mode": "CONTAINS"}` |
| `NOTIFICATION_RECEIVED` | Notification posted by an app | `{"package_name": "com.example.app", "keyword": "urgent", "match_field": "ANY", "match_mode": "CONTAINS"}` |
| `DEVICE_BOOT` | Device finished booting | `{}` |
| `HEADSET_PLUG` | Headset plugged or unplugged | `{"event": "PLUGGED"}` |
| `NFC_TAG` | NFC tag scanned | `{"tag_id": null}` |
| `GEOFENCE` | Device enters/exits geographic area | `{"lat": 25.033, "lng": 121.565, "radius_m": 200, "event": "ENTER"}` |
| `MANUAL` | User-triggered (Widget / Quick Tile) | `{}` |

### trigger_logic = ALL

Triggers are moments, not states, so "all of them" means "all of them recently". Each fire is remembered for 5 minutes; when the last missing trigger fires inside that window the flow runs once and the set resets. A trigger that fires again before the set completes simply refreshes its own timestamp.

- The `{{trigger.x}}` values of every fire are merged, with later fires winning — so `trigger.type` and `trigger.timestamp` describe the trigger that completed the set.
- A trigger removed from the flow stops counting immediately, so editing a flow can never leave it waiting forever.
- The partial state is in memory only: disabling the flow, editing it, or restarting the app starts the combination over.
- Manual runs (Run button, widget, tile, pinned shortcut) ignore `ALL` entirely — pressing Run means run.
- A flow with a single trigger behaves identically under `ANY` and `ALL`.

### TIME config fields

| Field | Type | Required | Description |
|---|---|---|---|
| `hour` | `integer (0–23)` | ✅ | Hour of day in 24h format |
| `minute` | `integer (0–59)` | ✅ | Minute |
| `repeat` | `"ONCE" \| "DAILY" \| "WEEKLY" \| "WEEKDAYS" \| "WEEKENDS"` | ✅ | Repetition pattern |
| `days_of_week` | `integer[] (0–6)` | ❌ | Required when `repeat = "WEEKLY"`. 0 = Sunday, 6 = Saturday. |

---

## Condition Object

Conditions are the flow's constraints: the triggers say *when* to run, the conditions say *only if*. Every condition must hold (AND) or the run is skipped — it is written to the execution log with status `SKIPPED` and the reason, never silently dropped.

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `string (UUID v4)` | ✅ | Unique identifier within this flow. |
| `type` | `ConditionType` | ✅ | Condition type identifier. See the table below. |
| `config` | `object` | ✅ | Type-specific parameters. May be `{}`. |
| `negate` | `boolean` | ✅ | If `true`, the condition's result is inverted (NOT). |

### ConditionType Values

| Value | Holds when | `config` |
|---|---|---|
| `TIME_RANGE` | The local clock is in `[start, end)`; a range whose end precedes its start wraps past midnight | `{"start": "22:00", "end": "07:00"}` |
| `DAY_OF_WEEK` | Today is one of `days`; an empty list is no restriction | `{"days": "MON,TUE,WED,THU,FRI"}` |
| `BATTERY_LEVEL` | Charge is at or above/below `level`; a missing `level` is no restriction | `{"direction": "ABOVE", "level": "30"}` |
| `CHARGING` | The device is (or is not) charging | `{"state": "CHARGING"}` |
| `WIFI_CONNECTED` | Wi-Fi is connected, optionally to `ssid` | `{"state": "CONNECTED", "ssid": "HomeWifi"}` |
| `BLUETOOTH_CONNECTED` | A Bluetooth **audio** device is connected, optionally matching `device_name` | `{"state": "CONNECTED", "device_name": "Car"}` |
| `SCREEN_STATE` | The screen is on or off | `{"state": "OFF"}` |
| `EXPRESSION` | The expression evaluates to true, same syntax as `IF_BLOCK` | `{"expression": "{{trigger.sender}} == 0912345678"}` |

**Unknown types fail closed.** A `type` this build doesn't recognise (a hand-edited file, a MacroDroid constraint) is a constraint that cannot be checked, so the flow is *not* run and the log says why. Importing such a flow raises a warning naming the type, and the editor marks the row as unsupported.

Matching an SSID needs location permission, and Bluetooth needs the Bluetooth permission — a condition that cannot read the state it guards does not hold. Both are listed by the flow's permission check like any trigger's.

---

## Action Object

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `string (UUID v4)` | ✅ | Unique identifier within this flow. |
| `type` | `ActionType` | ✅ | Action category. See **ActionType** table below. |
| `config` | `object` | ✅ | Type-specific parameters. |
| `order` | `integer (≥ 0)` | ✅ | Execution order index (ascending). Duplicate values are executed left-to-right as declared. |
| `enabled` | `boolean` | ✅ | If `false`, action is skipped at runtime. |

### ActionType Values

| Value | Description | Example `config` |
|---|---|---|
| `OPEN_APP` | Launch an application | `{"package": "com.example.app"}` |
| `SEND_SMS` | Send SMS message | `{"to": "{{phone}}", "body": "Hello"}` |
| `CALL_PHONE` | Initiate phone call | `{"to": "+886912345678"}` |
| `WIFI_TOGGLE` | Enable/disable Wi-Fi | `{"state": "ON"}` |
| `BLUETOOTH_TOGGLE` | Enable/disable Bluetooth | `{"state": "OFF"}` |
| `DND_TOGGLE` | Toggle Do Not Disturb | `{"state": "ON", "duration_min": 60}` |
| `VOLUME_ADJUST` | Set media/ring/alarm volume | `{"stream": "MEDIA", "level": 5}` |
| `NOTIFICATION` | Post a local notification, optionally with a tap target | `{"title": "NexFlow", "message": "{{message}}", "tap_action": "OPEN_APP", "tap_package": "com.example.app"}` |
| `TOAST` | Show a brief Toast message | `{"text": "Done!"}` |
| `HTTP_REQUEST` | Perform an HTTP call (Webhook) | `{"url": "https://...", "method": "POST", "body": "{{json}}"}` |
| `CLIPBOARD_COPY` | Copy text to clipboard | `{"text": "{{content}}"}` |
| `OPEN_URL` | Open URL in browser | `{"url": "https://..."}` |
| `MEDIA_PLAY_PAUSE` | Toggle media playback | `{}` |
| `TTS` | Text-to-speech | `{"text": "It is {{time}}", "locale": "en-US"}` |
| `BRIGHTNESS_ADJUST` | Set screen brightness | `{"level": 128}` |
| `AIRPLANE_TOGGLE` | Toggle airplane mode (requires root) | `{"state": "ON"}` |
| `DELAY` | Wait for a duration | `{"duration_ms": 2000}` |
| `IF_BLOCK` | Begin conditional block | `{"expression": "{{battery}} < 20"}` |
| `ELSE_BLOCK` | Begin else branch | `{}` |
| `END_IF` | Close conditional block | `{}` |
| `REPEAT_BLOCK` | Begin repeat loop | `{"count": 3}` |
| `END_REPEAT` | Close repeat loop | `{}` |
| `SET_VARIABLE` | Assign a value to a flow variable | `{"name": "counter", "value": "{{counter}} + 1"}` |
| `WRITE_FILE` | Write text to a file | `{"path": "/sdcard/log.txt", "content": "{{log}}", "append": true}` |
| `SHARE` | Share text or file | `{"type": "TEXT", "content": "{{text}}"}` |
| `SCREENSHOT` | Take a screenshot (requires Accessibility) | `{"save_path": "/sdcard/screenshots/"}` |

---

### NOTIFICATION tap target

`tap_action` decides where tapping the notification sends the user — the way a flow hands the next step back to the person, at whatever time they get to it.

| `tap_action` | Extra config | Tapping the notification |
|---|---|---|
| `NONE` (default) | — | Only dismisses it |
| `OPEN_APP` | `tap_package` | Launches that app |
| `OPEN_URL` | `tap_url` | Opens the link (a deep link lands on a specific screen) |
| `OPEN_SHORTCUT` | `tap_shortcut_uri`, `tap_shortcut_label`, `tap_shortcut_package` | Opens that app shortcut |

A target that no longer resolves (app uninstalled, blank URL) still posts the notification — the reminder is the point — but the run is logged as failed so the reason is visible.

---

## Variable Object

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | `string` | ✅ | Variable identifier. Used as `{{name}}` in action/trigger configs. Alphanumeric + underscore only. Max 30 chars. |
| `type` | `"STRING" \| "INTEGER" \| "BOOLEAN" \| "DECIMAL"` | ✅ | Runtime type of the variable. |
| `default_value` | `string \| number \| boolean` | ✅ | Initial value. Must be compatible with `type`. |

### Variable Interpolation

Variables are referenced using double-brace syntax in any string `config` value:

```
{{variable_name}}
```

The runtime substitutes the current value before executing the action. Nested or compound expressions (e.g. `{{count}} + 1`) are evaluated by the ActionExecutor interpreter.

---

## Trigger Variables

Whatever fired the flow reports what it knew about the event, readable in any condition or action `config` string as `{{trigger.<name>}}`. These names are supplied by the runtime — they are not declared in `variables` and are not exported.

Every run, including a manual one, carries:

| Name | Description |
|---|---|
| `{{trigger.type}}` | The `TriggerType` that fired, e.g. `SMS_RECEIVED`. `MANUAL` for a run started by hand, the widget or the tile. |
| `{{trigger.timestamp}}` | Epoch milliseconds at which the trigger fired. |

Per trigger type:

| TriggerType | Additional names |
|---|---|
| `TIME` | `time` (the scheduled `HH:mm`) |
| `BATTERY` | `level` (0–100), `charging` (`true`/`false`) |
| `BLUETOOTH` | `device`, `event` |
| `WIFI` | `ssid`, `event` |
| `SCREEN` | `event` |
| `APP_LAUNCH` | `package` |
| `INCOMING_CALL` | `number` |
| `SMS_RECEIVED` | `sender`, `body` |
| `NOTIFICATION_RECEIVED` | `package`, `title`, `text` |
| `HEADSET_PLUG` | `event` |
| `NFC_TAG` | `tag_id` |
| `GEOFENCE` | `event` (`ENTER`/`EXIT`), `lat`, `lng` |
| `AMBIENT_LIGHT` | `lux` |
| `DEVICE_BOOT`, `SHAKE`, `MANUAL` | — (common names only) |

A value the platform withholds arrives as an empty string rather than being absent — e.g. `ssid` without location permission, or `device` without the Bluetooth permission.

---

## GlobalVariable Object

Global variables live outside any flow (Settings → Global variables) and are referenced as `{{g:name}}`. A flow exports the ones it uses so it stays runnable after import on another device.

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | `string` | ✅ | Name **without** the `g:` prefix. Referenced as `{{g:name}}`. |
| `type` | `"STRING" \| "INTEGER" \| "BOOLEAN" \| "DECIMAL"` | ✅ | Runtime type. Unknown values fall back to `STRING` on import. |
| `default_value` | `string` | ✅ | Initial value. The live value is **not** exported — an imported global starts at its default. |

Import rules:

- A declared global that doesn't exist yet is created; one that already exists is left untouched (another flow may depend on its current value).
- A `{{g:name}}` reference or `g:name` SET_VARIABLE target that no declaration or existing global covers produces an import warning.
- `SET_VARIABLE` never creates a global implicitly: writing to an undeclared `g:` name fails the run so a typo can't silently produce a zombie variable.

---

## Complete Example

```json
{
  "schema_version": 1,
  "id": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "name": "Morning Routine",
  "description": "At 7:30 AM on weekdays, disable DND and set media volume to 7.",
  "author": "nexflow_user",
  "tags": ["morning", "volume", "daily"],
  "enabled": true,
  "created_at": "2026-06-03T00:00:00Z",
  "updated_at": "2026-06-03T00:00:00Z",
  "triggers": [
    {
      "id": "t1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
      "type": "TIME",
      "config": {
        "hour": 7,
        "minute": 30,
        "repeat": "WEEKLY",
        "days_of_week": [1, 2, 3, 4, 5]
      }
    }
  ],
  "trigger_logic": "ANY",
  "conditions": [],
  "actions": [
    {
      "id": "ac1b2c3d-e4f5-4a6b-7c8d-9e0f1a2b3c4d",
      "type": "DND_TOGGLE",
      "config": { "state": "OFF" },
      "order": 0,
      "enabled": true
    },
    {
      "id": "ac2c3d4e-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
      "type": "VOLUME_ADJUST",
      "config": { "stream": "MEDIA", "level": 7 },
      "order": 1,
      "enabled": true
    }
  ],
  "variables": [],
  "trigger_logic": "ANY"
}
```

---

## Validation Rules

1. `id` must be a valid UUID v4 string.
2. `triggers` must contain at least one item.
3. `actions` must contain at least one item. If `IF_BLOCK` appears, a matching `END_IF` must exist at the same nesting level.
4. `REPEAT_BLOCK` must be closed by `END_REPEAT` at the same nesting depth.
5. `variable.name` must match `[a-zA-Z_][a-zA-Z0-9_]*`.
6. `variable.default_value` type must match `variable.type`.
7. `action.order` values are non-negative integers; ties are broken by declaration order.
8. `updated_at` must be greater than or equal to `created_at`.

---

## MacroDroid Compatibility

`.mdr` import is supported on a best-effort basis. See `docs/MDR_COMPAT.md`. Incompatible features are marked with a warning icon in the editor UI and do not cause import failure.
