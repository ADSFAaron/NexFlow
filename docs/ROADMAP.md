# NexFlow 遠期計畫

這份文件記錄**已經評估過、但要等外部條件成熟才做**的項目，以及評估後決定不做的項目。每一項都寫明「什麼時候該重新拿出來看」，避免下次又從頭研究一次。

---

## 等待外部條件

### 讓系統的 Gemini 助理執行 NexFlow 流程（AppFunctions）

- **評估日期**：2026-11-09
- **狀態**：⏸ 等待 Google 開放

**目標**：使用者對系統的 Gemini 說「幫我跑回家模式」，Gemini 直接執行對應的 NexFlow 流程。未來若 Gemini 支援跨裝置（手錶、網頁版透過手機做事），也一併受惠。

**為什麼現在不做**

- 官方管道是 [AppFunctions](https://developer.android.com/ai/appfunctions)（App 在裝置上扮演 MCP server，把函式當工具提供給 agent）。官方標示 **Experimental**，只支援 **Android 16 以上**。
- 官方原文：「As of May 2026, AppFunctions integration with Gemini is in a private preview with trusted testers」。NexFlow 可以先實作，但一般使用者的 Gemini 不會呼叫它，做了也沒有效果。
- 跨裝置部分：
  - Wear OS 的 Gemini 會借用配對手機的設定，但官方只列出 Google 自家服務，沒有提到第三方 AppFunctions。
  - 網頁版／電腦版 Gemini 操控手機，目前只有 App 拆解挖到的字串，Google 沒有正式公布。

**何時重新評估**（任一條件成立即可）

- AppFunctions 的 Gemini 整合脫離 private preview，或第三方 App 可以自行上架
- NexFlow 被 [Early Access Program](https://forms.gle/GN5ybjQFhzHRCguM7) 選上
- Google 正式推出跨裝置呼叫手機 App 的功能

**屆時的設計草案**

| 項目 | 內容 |
|------|------|
| 函式 | `listFlows()` 列出可執行的流程，`runFlow(name)` 執行一個流程。第一版**不收參數**，不把 Gemini 給的文字當變數帶進流程 |
| 開關 | 每個流程加「允許 Gemini 執行」，**預設關閉**；`listFlows()` 只回傳有開的流程 |
| 版本 | `@RequiresApi(36)`，Android 15 以下完全不受影響 |
| 安全 | AI 一句話就能觸發，風險等同外部呼叫。`ActionPolicy.LITERAL_ONLY_KEYS`、`SlidingWindowRateLimiter` 照常適用。日後若要開放參數，比照 `{{trigger.*}}` 的威脅模型處理 |
| 執行路徑 | 走既有的 `FlowExecutionService` + `ACTION_RUN_FLOW`，不另開一條執行路徑 |
| 測試 | `adb shell cmd app_function list-app-functions --package com.adsf.nexflow` 驗證註冊；用同一組指令驗證執行。開關邏輯用 Robolectric 測 |
| Play | 流程名稱會出現在 Gemini（Google）那一端，需評估 Data safety 表單是否要更新 |

**參考資料**

- [Overview of AppFunctions | Android Developers](https://developer.android.com/ai/appfunctions)
- [AppFunctions sample](https://github.com/android/appfunctions)、[AppFunctions agent skill](https://github.com/android/skills/tree/main/device-ai/appfunctions)
- [Use Gemini on your smartwatch – Google Help](https://support.google.com/gemini/answer/16401122?hl=en)

---

## 評估後不做

### 強制停止其他 App

- **評估日期**：2026-11-09
- **結論**：一般 App 做不到，不實作。

| 方式 | 為什麼不行 |
|------|-----------|
| `am force-stop` 的等效 API | 需要 `FORCE_STOP_PACKAGES`，只發給系統簽章或預裝 App |
| `ActivityManager.killBackgroundProcesses()` | 本來就只殺背景程序。官方文件寫明 Android 14 起「third party applications can only use this API to kill their own processes」 |

**可能的繞道方式（目前不打算做）**

- 用無障礙服務自動按「應用程式資訊 → 強制停止」：會因廠商與語系而失效；而且這是新的無障礙用途，Play 的無障礙使用聲明可能要重送。
- Shizuku／root 執行 `am force-stop`：使用者門檻太高。
- 如果需求其實是「重新開乾淨的畫面」，可以改用 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` 開啟目標 App。這不會停止它的程序或服務。
