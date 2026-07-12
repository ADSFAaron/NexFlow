# Changelog

本檔記錄 NexFlow 的重要變更。格式依循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)，版本遵循 [語意化版本](https://semver.org/lang/zh-TW/)。

## [1.1.0] - 2026-07-12

新增 Gemini AI 助手、四個新的觸發／動作，以及多項執行透明度與安全性強化。

### 新增（Added）

- **Gemini AI 助手**：用文字或語音描述需求，AI 透過 function calling 產生完整流程草稿供檢視、儲存
  - 自備 Google AI Studio 的 Gemini API 金鑰（設定 → AI），可從線上模型清單挑選模型（自動過濾已淘汰與非對話模型），未選時預設 `gemini-3.5-flash`
  - 對話式建立：AI 會先追問釐清、呼叫「搜尋已安裝 App」工具解析套件名，再產生流程
  - 執行進度即時顯示（聯絡 Gemini／搜尋 App／產生流程／修正錯誤）
  - 系統語音辨識口述輸入，聆聽時輸入框有 Gemini 風格漸層動效
  - 同一對話可繼續要求微調並更新同一個流程，或開新對話；對話與草稿跨頁面保留
  - 訊息可長按複製／編輯，顯示時間戳
- **觸發條件**：搖晃（加速度計，可調靈敏度）、環境光線（lux 門檻，事件式）
- **動作**：模擬點擊（無障礙 `dispatchGesture`，僅 `github` 版）、擴音（通話音訊切至擴音器）
- **執行回饋**：流程執行時顯示 Toast「NexFlow ▶ 流程名稱」，背景觸發不再無聲執行（可於設定關閉）
- **缺少 App 偵測**：流程引用未安裝的 App（分享／匯入或事後解除安裝）時，權限精靈會提示並提供「安裝」直達 Play 商店

### 變更（Changed）

- 版本號 1.0.2 → 1.1.0（versionCode 3 → 4）
- 匯入路徑的 `FlowJson → 領域模型` 轉換抽為共用 mapper，供匯入與 AI 助手共用
- AI 對話介面全面套用 Material 3 Expressive（藥丸形輸入框、`LoadingIndicator`、動態進場、動效 token）

### 修正（Fixed）

- **APP_LAUNCH 觸發失效**：無障礙服務設定漏宣告 `accessibilityEventTypes`，導致從未收到視窗切換事件；補上 `typeWindowStateChanged` 後「App 啟動」觸發恢復運作
- AI 頁進場過場動畫太快幾乎不可見，改為較慢、從右上角 sparkle 展開的縮放
- 語音聽寫／編輯回填時輸入框游標不跟隨文字，改用 `TextFieldValue` 修正
- AI 輸入框與麥克風／送出按鈕高度不對齊

### 安全性（Security）

- **API 金鑰備份外洩（API 30）**：金鑰所在的 `nexflow_ai.xml` 先前僅在 API 31+ 的 `data_extraction_rules.xml` 排除備份；補上 `backup_rules.xml`（API 30 使用）的排除規則，Android 11 裝置的金鑰也不再上傳雲端備份
- Gemini 金鑰只透過 `x-goog-api-key` 標頭傳送、從不寫入 log；除錯用的完整請求／回應內容僅在 `BuildConfig.DEBUG` 下輸出
- AI 產生的流程一律以**停用**狀態加入，須經檢視與授權（走既有權限精靈）才能啟用；AI 只能組出使用者本就能手動設定的流程，無法越權
- Gemini 400 錯誤不再一律誤判為「金鑰無效」，改依錯誤內容分類並顯示伺服器原始訊息
- AI 話題邊界：僅協助自動化流程，無關內容會被婉拒

### Play 版差異（Play flavor）

- 模擬點擊（`SIMULATE_TAP`）僅 `github` 版提供——`dispatchGesture` 會擴大 Play 無障礙用途聲明範圍，故 `play` 版隱藏
- AI catalog 與流程驗證同步套用 flavor 過濾：`play` 版的 AI 不會產生被隱藏的觸發／動作（如簡訊、撥號）

## [1.0.2] - 2026 上半年

首個公開版本基礎。

### 新增

- 「當…→ 就…」流程建立器，14 種觸發 × 25 種動作，支援變數、If/Else、Repeat、Show Menu
- 逐步權限設置向導（含特殊權限與背景定位揭露）
- 首次啟動引導頁
- 更換桌布動作
- TIME 觸發改用 AlarmManager（可跨 Doze 與服務重啟）
- 地理圍欄觸發強化錯誤處理，避免單一觸發失敗導致引擎崩潰
- 多語言：繁體中文、簡體中文、日文、英文
- 匯入／匯出 `.flow`（JSON）與 MacroDroid `.mdr` 相容解析

[1.1.0]: https://github.com/ADSFAaron/NexFlow/releases/tag/v1.1.0
[1.0.2]: https://github.com/ADSFAaron/NexFlow/releases/tag/v1.0.2
