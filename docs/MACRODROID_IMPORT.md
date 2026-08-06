# MacroDroid 匯入（.mdr / .macro）

NexFlow 可以讀 MacroDroid 匯出的檔案，盡力轉成 NexFlow 的流程。這份文件記錄**格式本身**、
**對照表的依據**，以及**已知轉不過來的東西** —— 補對照表之前請先看完，MacroDroid 的類別命名
沒有規則可循，用猜的不會中。

程式碼位置：`core/macrodroid-compat/`

- `parser/MdrParser.kt` — 解析 + 判斷檔案是不是 MacroDroid 格式
- `model/MdrModels.kt` — 檔案結構
- `MdrToFlowConverter.kt` — class type → NexFlow 類型
- `MdrOptionMappers.kt` — 設定欄位（option key）對照
- `MdrOptions.kt` — 讀設定並記錄哪些沒被用到

## 檔案格式

兩種匯出都吃：

| 副檔名 | 來源 | 外層結構 |
|--------|------|----------|
| `.mdr` | 設定 → 匯出（整份備份） | `{"macroList": [ <macro>, … ], "geofenceData": …, …}` |
| `.macro` | 單一巨集分享 | `{"macro": <macro>, "macroExportVersion": 1}` |

一個 macro：

```json
{
  "m_GUID": -8430540620057371258,
  "m_name": "Morning routine",
  "m_enabled": true,
  "m_triggerList": [ … ], "m_actionList": [ … ], "m_constraintList": [ … ]
}
```

觸發／動作／條件三者結構相同，**設定欄位是平鋪在物件上的，沒有 `options` 子物件**：

```json
{
  "m_hour": 7, "m_minute": 30, "m_daysOfWeek": [true,true,true,true,true,false,false],
  "m_classType": "TimerTrigger", "m_SIGUID": -5381372544201902085,
  "m_isDisabled": false, "m_isOrCondition": false, "m_constraintList": []
}
```

`MdrItem` 會把 `m_classType` / `m_SIGUID` / `m_isDisabled` / `m_isOrCondition` /
`m_constraintList` / `m_comment` / `fakeIcon` 這些每個項目都有的欄位拿掉，剩下的才是使用者設定。

注意 `m_GUID` 與 `m_SIGUID` 是**帶正負號的 64 位元整數**，不是 UUID 字串。

## 對照表的依據

**不要憑印象加對照。** MacroDroid 的命名不一致（`MakeCallAction` 但 `SetPriorityMode`；
`WifiConnectionTrigger` 但 `WifiConstraint`；`GeofenceTrigger` 不是 `GeoFenceTrigger`），
看起來很合理的名字通常根本不存在，加下去只是永遠不會 match 的死碼。

現有對照是從這兩處查證的：

1. **MacroDroid 反編譯原始碼** —
   [`Mohsenabn78/macro-reverse-engineering`](https://github.com/Mohsenabn78/macro-reverse-engineering)
   - class 名稱：`com/arlosoft/macrodroid/{triggers,action,constraint}/*.java` 的檔名就是 `m_classType`
   - 設定欄位名稱：該類別的 `private` 欄位
   - 數字選項的意義：該類別的 `getOptions()` 字串陣列順序（例如藍牙 `m_btState`
     0=開啟、1=關閉、2=裝置已連線、3=裝置已中斷）
2. **真實匯出檔** — 用來確認 JSON 實際長相與欄位值
   - [`cpuuntery/MacroDroid-settings`](https://github.com/cpuuntery/MacroDroid-settings) 的 `.mdr` 備份
   - [`VineDuck/UltraDuck`](https://github.com/VineDuck/UltraDuck)、
     [`ganesh2shiv/macrodroid-macros`](https://github.com/ganesh2shiv/macrodroid-macros) 的 `.macro`

新增對照時，PR 請寫明是從哪個類別／哪個檔案看到的。

## 已知轉不過來的東西

這些**一定會出現在匯入警告裡**，不會靜默消失：

| 項目 | 為什麼 |
|------|--------|
| Geofence 的座標 | 巨集只存 zone id，座標在檔案的 `geofenceData` 區塊，不屬於巨集 |
| 選單（`OptionDialogAction`）每個選項的動作 | MacroDroid 把選項指到**另一個巨集／動作區塊**（`m_actionMacroGuids`），NexFlow 的 `MENU_CASE` 是內嵌的 |
| 捷徑（`LaunchShortcutAction`） | 存的是那台裝置的 intent，換裝置沒有意義 |
| 桌布圖片 | 舊裝置上的檔案路徑 |
| 音量（`SetVolumeAction`） | MacroDroid 用 stream 陣列一次設多個音道，NexFlow 一次一個 |
| 條件式迴圈、變數運算（increment/random/expression） | NexFlow 只有固定次數迴圈與指定值 |
| 「排除」條件（SMS／通知的 exclude） | NexFlow 只有比對，沒有排除 |
| 每個項目自己的 `m_constraintList` | NexFlow 的條件是整個流程層級的 |

`SIMULATE_TAP` / `SIMULATE_SWIPE` 會照常匯入（座標值得留下），但**只有 GitHub 版能執行** ——
Play 版依平台政策移除了執行器，所以匯入時一律附上警告，執行時會失敗並在執行記錄寫明原因。

## 加一個新對照要做什麼

1. 從上面兩個來源查出 class 名稱與欄位名稱
2. `MdrToFlowConverter.kt` 的 `TRIGGER_TYPE_MAP` / `ACTION_TYPE_MAP` / `CONDITION_TYPE_MAP` 加一筆
3. `MdrOptionMappers.kt` 的 `BY_CLASS` 加一筆設定對照（只加類型不加設定的話，
   匯入後欄位是空的，轉換器會在警告裡直說）
4. `MdrToFlowConverterTest` 加測試：型別正確、而且**設定欄位的 key 是 NexFlow 的 key、值也對**
5. `./gradlew :core:macrodroid-compat:test`
