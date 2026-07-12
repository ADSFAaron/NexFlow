<div align="center">

<img src="appicon.png" width="120" alt="NexFlow logo" />

# NexFlow

**把手機變聰明的開源自動化 App** — 用「**當…（WHEN）→ 就…（THEN）**」組合出屬於你的自動化流程。
類似 MacroDroid / Tasker，但完全開源、介面以 Material 3 重新打造。

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com)
![minSdk](https://img.shields.io/badge/minSdk-30%20(Android%2011)-blue)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-D22128)](#授權)

</div>

---

## 📸 畫面預覽

| 我的流程 | 流程編輯（WHEN → THEN） | 動作選擇 |
|:---:|:---:|:---:|
| <img src="docs/screenshots/home.png" width="230" alt="我的流程畫面" /> | <img src="docs/screenshots/builder.png" width="230" alt="流程編輯畫面" /> | <img src="docs/screenshots/picker.png" width="230" alt="動作選擇畫面" /> |
| 一覽所有流程，一鍵開關／執行 | 組合觸發條件與動作 | 16 種觸發 × 27 種動作，分類搜尋 |

| 執行記錄 | 權限引導 |
|:---:|:---:|
| <img src="docs/screenshots/logs.png" width="230" alt="執行記錄畫面" /> | <img src="docs/screenshots/settings.png" width="230" alt="設定與權限畫面" /> |
| 每次執行的成功／失敗與清楚的錯誤原因 | 各功能所需權限逐項引導開啟 |

## ✨ 它能做什麼？

- 🔋 **電量低於 20% 就自動開省電模式、關藍牙**
- 🏠 **回到家連上 Wi-Fi 就把音量調回、開勿擾**
- 🌙 **晚上 11 點自動截圖記帳通知、傳到你的伺服器**
- 📷 **NFC 標籤一刷就開特定 App + 朗讀今日行程**

只要想得到「**什麼情況下 → 做什麼事**」，幾乎都能組出來，還支援**變數、條件分支（If/Else）、迴圈（Repeat）**，以及匯入 MacroDroid 的 `.mdr` 檔。

> ✨ **不想手動組？** 內建 **Gemini AI 助手**：用文字或語音描述需求（例如「插上耳機時把音量調到 80% 並打開 Spotify」），AI 就會產生對應流程讓你檢視、儲存。需自備 Google AI Studio 的 Gemini API 金鑰，見 [AI 助手](#-ai-助手gemini)。

---

## 功能總覽

### 觸發條件（Trigger，16 種）

| 類別 | 觸發條件 |
|------|----------|
| 時間 | 指定時間（每天／平日／週末／自訂星期／單次） |
| 裝置狀態 | 電量高低（含充電狀態）、螢幕開關／解鎖、開機、耳機插拔、**搖晃**、**環境光線** |
| 連線 | Wi-Fi 連線／斷線（可指定 SSID）、藍牙裝置連線／斷線 |
| 通訊 | 來電（可指定聯絡人）、收到簡訊（可指定發信人）、收到通知（可指定 App） |
| 其他 | App 啟動、NFC 標籤掃描、地理圍欄（進入／離開）、手動執行 |

### 動作（Action，27 種）

| 類別 | 動作 |
|------|------|
| 通知與提示 | Toast、系統通知、TTS 朗讀 |
| 裝置控制 | Wi-Fi、藍牙、勿擾模式、飛航模式、音量、亮度、媒體播放控制、截圖、更換桌布、**模擬點擊**、**擴音** |
| 通訊 | 撥打電話、傳送簡訊 |
| 網路與資料 | HTTP 請求、開啟網址、剪貼簿、寫入檔案、分享 |
| App | 開啟 App |
| 流程控制 | 延遲、If／Else／End If、Repeat／End Repeat、設定變數、Show Menu |

> 🔋 **搖晃**觸發會在流程啟用期間持續監聽加速度感應器，耗電略增（環境光線觸發為事件式，幾乎不耗電）。
> 🖐️ **模擬點擊**需無障礙服務且僅在 `github` 版提供（依平台政策，`play` 版移除）。**擴音**為盡力而為，通話中的音訊路由最終由裝置廠商決定。

### 🤖 AI 助手（Gemini）

用自然語言（文字或語音）描述需求，Gemini 透過 function calling 產生完整流程草稿：

- **自備金鑰**：需在 Google AI Studio 取得免費 Gemini API 金鑰，於「設定 → AI」填入。金鑰只存本機、排除雲端備份，也不會寫入 log
- **對話式建立**：AI 會先追問釐清、呼叫「搜尋已安裝 App」工具解析套件名，再產生流程；產生前於畫面即時顯示執行進度
- **語音輸入**：可用系統語音辨識口述需求，聆聽時輸入框有 Gemini 風格漸層動效
- **可繼續修改**：同一對話中可要求微調，直接更新同一個流程；也可開新對話
- 🔒 AI 產生的流程一律**停用**加入，須經檢視、授權才能啟用（與匯入相同的安全閘門）；話題僅限自動化，無關內容會被婉拒

> 💡 **通訊類（簡訊／撥號）僅在 `github` 版提供**。Google Play 版（`play` flavor）依平台政策移除這些功能與權限，見[建置](#建置)。

### 變數與條件式

- Flow 可宣告變數（含預設值），在任何欄位用 `{{變數名}}` 引用
- `SET_VARIABLE` 動作可在執行中改變變數值
- `IF_BLOCK` 條件式支援 `==`、`!=`、`<`、`>`、`<=`、`>=`（兩側皆為數字時做數值比較，否則不分大小寫字串比較），例如 `{{battery}} < 20`

### 匯入／匯出

- 自有格式：`.flow`（JSON），規格見 [docs/FLOW_SCHEMA.md](docs/FLOW_SCHEMA.md)
- MacroDroid 相容：可解析並轉換 `.mdr` 匯出檔（`core/macrodroid-compat`）
- 支援檔案選擇器匯入、系統分享（Share）匯入、Flow 詳細頁直接匯出分享
- 🔒 匯入的流程一律以**停用**狀態加入，外部分享的檔案會先跳確認框，避免惡意檔案自動執行

---

## 技術棧

| 項目 | 版本 |
|------|------|
| Kotlin | 2.2.10（KSP，無 kapt） |
| AGP / Gradle | 9.2.1 / 9.5 |
| UI | Jetpack Compose（BOM 2026.02.01）+ Material 3（含 Expressive alpha） |
| DI | Hilt 2.59.2 |
| 資料庫 | Room 2.7.1 |
| 其他 | Navigation Compose、kotlinx.serialization、Ktor、WorkManager、Glance（桌面小工具）、play-services-location（地理圍欄） |
| SDK | minSdk 30（Android 11）／ targetSdk 37 |

## Module 結構

```
app/                        # Android App：UI、Room、Hilt、trigger/executor 實作、Service
core/automation/            # 純 Kotlin JVM：領域模型、FlowInterpreter（IF/REPEAT/變數）、repository 介面
core/flow-schema/           # 純 Kotlin JVM：.flow JSON 序列化與驗證
core/macrodroid-compat/     # 純 Kotlin JVM：MacroDroid .mdr 解析與轉換
```

### 架構重點

- **Trigger 系統**：`TriggerHandler` 介面 + Hilt multibinding（`@Binds @IntoSet`，註冊於 `app/.../di/ExecutionModule.kt`）。Android 元件（AccessibilityService、NotificationListener、BroadcastReceiver…）透過 singleton EventSource（`MutableSharedFlow`）橋接到 handler
- **Action 系統**：`ActionExecutor` 介面 + 同樣的 multibinding。控制流程動作（IF/REPEAT/SET_VARIABLE）由 `FlowInterpreter` 直接處理，不需要 executor
- **執行引擎**：`FlowEngine` 觀察啟用中的 Flow，把每個 (flow, trigger) 配對成事件串流，觸發時交給 `FlowInterpreter` 執行並寫入執行記錄
- **新增型別時**：在 `TriggerType`/`ActionType` 加 enum → 實作 handler/executor → `ExecutionModule` 加綁定 → `TriggerConfig`/`ActionConfig` 加 UI 欄位定義。少做任何一步都會被自動化測試抓到（見[測試](#測試)）

## 建置

```bash
git clone <repo>
cd StudioProject
```

專案有兩個 **product flavor**：

| Flavor | 用途 | 簡訊／撥號 |
|--------|------|:---:|
| `github` | sideload / GitHub Release，功能完整 | ✅ |
| `play` | 上架 Google Play（依政策移除簡訊／撥號權限與程式碼） | ❌ |

```bash
./gradlew :app:assembleGithubDebug    # 完整版 debug APK
./gradlew :app:assemblePlayDebug      # Play 版 debug APK
./gradlew :app:bundleGithubRelease    # 完整版 release AAB
./gradlew :app:bundlePlayRelease      # Play 版 release AAB（上架用）
```

需求：JDK 17+（Gradle toolchain 會自動處理 module 的 JVM 11 目標）、Android SDK Platform 37。

## 測試

### JVM 單元測試（不需裝置）

```bash
./gradlew :core:automation:test            # FlowInterpreter：條件式、IF/ELSE、REPEAT、變數
./gradlew :app:testGithubDebugUnitTest     # 設定 UI 的全型別覆蓋與 interpreter key 契約
```

> 因為有 flavor，單元測試任務需帶 flavor 名：`testGithubDebugUnitTest` 或 `testPlayDebugUnitTest`。

涵蓋內容：

- `FlowInterpreterTest` — 條件式求值（比較運算子、數值/字串）、IF/ELSE 分支、REPEAT 次數、SET_VARIABLE 新舊 key 相容
- `TriggerConfigTest` / `ActionConfigTest` — **每一種** TriggerType（14）與 ActionType（25）的 picker 資訊、欄位 key 唯一性、空設定與填滿設定的摘要產生，以及與 interpreter 的 key 契約（IF 的 `expression`、REPEAT 的 `count`、SET_VARIABLE 的 `variable_name`/`value`）

### 裝置 instrumented 測試（需要實機或模擬器）

**執行前必須先關閉系統動畫**，否則 Compose 文字輸入測試會因 Espresso 等不到 main looper idle 而逾時：

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

執行（同樣需帶 flavor）：

```bash
./gradlew :app:connectedGithubDebugAndroidTest
```

涵蓋內容：

- `ExecutionBindingsTest` — 從真實 Hilt graph 驗證每個 TriggerType 都有 TriggerHandler、每個可執行 ActionType 都有 ActionExecutor 且無重複綁定（flavor 隱藏的型別會自動排除），抓「忘了在 ExecutionModule 註冊」這類只會在執行期爆炸的錯
- `ConfigDialogRenderTest` — 在裝置上實際 render 全部 trigger/action 的設定對話框，驗證每個欄位元件都有顯示，並測試輸入值能正確存進 config

instrumented 測試使用 `com.nexflow.HiltTestRunner`（HiltTestApplication）；新增 Hilt 相關測試標上 `@HiltAndroidTest` 即可。

測完可還原動畫：

```bash
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings put global animator_duration_scale 1.0
```

## 權限說明

部分功能需要使用者手動授權（App 的 Settings 分頁有逐項引導）：

| 功能 | 需要的權限 |
|------|-----------|
| App 啟動觸發、截圖動作 | 無障礙服務（Accessibility Service） |
| 通知觸發 | 通知存取權限（Notification Listener） |
| 地理圍欄 | 精確定位 + 背景定位 |
| 亮度調整 | 修改系統設定（WRITE_SETTINGS） |
| 勿擾模式 | 勿擾存取權限 |
| Wi-Fi／飛航模式靜默切換 | `WRITE_SECURE_SETTINGS`（需透過 ADB 授權一次，App 內有指令可複製） |
| NFC 觸發 | 僅 App 在前景時有效 |

## 已知限制（規劃中）

- Flow 層級的 `conditions`（執行前置條件）已有資料模型與 .mdr 匯入，但引擎尚未評估、也沒有編輯 UI
- 全域變數（跨 Flow 共用）的資料表已預留，功能尚未實作

## 授權

Apache License 2.0
