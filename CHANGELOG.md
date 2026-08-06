# Changelog

本檔記錄 NexFlow 的重要變更。格式依循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)，版本遵循 [語意化版本](https://semver.org/lang/zh-TW/)。

## [未發布]

### 新增（Added）

- **用 Gemini 修改現有流程**：流程詳細頁可直接「用 Gemini 編輯」，AI 會先讀進整個流程再依指示修改，儲存時更新原流程而不是產生一份近乎重複的副本
  - 重新進入同一個流程會保留對話；換一個流程則重新開始（否則模型的歷史仍在描述上一個流程）
- **AI 回覆支援 Markdown**：清單、粗體、行內程式碼等格式會正常呈現，不再顯示成原始符號

### 修正（Fixed）

- **MacroDroid 匯入實際上完全不會生效**（#6）：以下三件事任一都足以讓匯入失效，三件都存在
  - 自動判斷格式時用「內容開頭是 `{`」決定走哪個解析器，但 `.mdr` 本身就是 JSON，於是每個 MacroDroid 檔都被丟去 `.flow` 解析器並回報 parse failed
  - 解析器的欄位名稱與真實格式不符（真實格式為 `macroList` / `m_actionList` / `m_classType`，且設定欄位**平鋪**在物件上、沒有 `options` 子物件），套用在真實檔案上會得到零個巨集
  - 對照表 25 筆動作裡有 20 筆的 class 名稱在 MacroDroid 中根本不存在（`WaitAction` 實為 `PauseAction`、`LaunchApplication` 實為 `LaunchActivityAction`、`MediaAction` 實為 `ControlMediaAction`、`GeoFenceTrigger` 實為 `GeofenceTrigger`…）
- **匯入後設定欄位是空的**（#6）：新增設定欄位（option key）對照層，涵蓋 46 種 MacroDroid class，時間、電量、Wi-Fi、藍牙、通知、簡訊、延遲、音樂、變數、寫入檔案、HTTP 等都會連同設定一起轉換（例如 `m_hour`/`m_minute`/`m_daysOfWeek` → `time`/`repeat`/`days`）
  - 轉不過去的欄位會**逐項寫進匯入警告**並指出原因，不再靜默丟掉
  - 未知的動作型別在匯入後會降級成吐司，現在該吐司會寫明原本是哪個 MacroDroid 動作，而不是一個空吐司
- 動作在 MacroDroid 中被停用（`m_isDisabled`）時，匯入後維持停用

### 變更（Changed）

- MacroDroid 相容範圍擴充至 22 種觸發、30 種動作、7 種條件的 class type（#3、#4、#5、#7）
  - 新增：飛航模式、更換桌布、擴音、啟動捷徑（#3）
  - 新增：搖晃、環境光線觸發（#4）。搖晃靈敏度是 MacroDroid 的全域設定、不存在於巨集裡，因此沿用 NexFlow 預設值
  - 新增：模擬點擊／滑動（#5）。舊檔為 `TouchScreenAction`，新檔為 `UIInteractionAction` 並以 `uiInteractionConfiguration.type`（`Click`／`Gesture`）區分。**照樣對照**以保留座標，並固定附上「僅 GitHub 版可執行」的警告 —— Play 版依平台政策沒有執行器，執行時會失敗並在執行記錄寫明原因
  - 新增：選單（#7）。MacroDroid 的 `OptionDialogAction` 會展開成 `SHOW_MENU` + N×`MENU_CASE` + `END_MENU` 並重算 order；各選項的動作在 MacroDroid 是指向**另一個巨集**（`m_actionMacroGuids`）而非內嵌分支，因此 case 內容必為空，匯入警告會明講
- 支援 `.macro`（單一巨集分享）格式，先前只處理整份備份的 `.mdr`
- 新增 [docs/MACRODROID_IMPORT.md](docs/MACRODROID_IMPORT.md)：記錄真實檔案格式、每一筆對照的查證來源，以及已知一定轉不過來的項目

## [1.2.0] - 2026-08-03

觸發系統補完：觸發器現在會把「發生了什麼」交給流程使用，限制條件真正生效，`ALL` 觸發邏輯不再是空殼，通知也能把使用者直接送到下一個 App。

### 新增（Added）

- **觸發器變數 `{{trigger.x}}`**：觸發事件的內容可直接用在任何條件與動作欄位，編輯器的插入變數選單會列出目前流程可用的名稱
  - 每次執行都有 `trigger.type`、`trigger.timestamp`（手動執行也有）
  - 簡訊 `sender`/`body`、通知 `package`/`title`/`text`、來電 `number`、App 啟動 `package`、Wi-Fi `ssid`/`event`、藍牙 `device`/`event`、電池 `level`/`charging`、NFC `tag_id`、地理圍欄 `event`/`lat`/`lng`、環境光 `lux`、螢幕與耳機 `event`、時間 `time`
  - 平台不給的值會是空字串（例如沒有定位權限時的 Wi-Fi 名稱），而不是整個欄位消失
- **限制條件（Conditions）**：流程編輯器新增「限制條件」區段，觸發器決定「何時」，限制條件決定「要不要」
  - 8 種條件：時間範圍（可跨午夜）、星期、電池電量、充電狀態、Wi-Fi、藍牙音訊裝置、螢幕開關、運算式（與 If 相同語法，可比較觸發器變數與全域變數）
  - 每個條件都可反轉（NOT）；全部成立才執行
  - 不成立時記錄為「已跳過」並附上原因；手動執行被擋下時會顯示提示，不再毫無反應
- **通知／簡訊內容過濾**：關鍵字 + 比對範圍（標題／內文／兩者）+ 比對方式（包含／完全相同／正規表示式）；空白代表不過濾
- **`ALL` 觸發邏輯**：所有觸發器都要在 5 分鐘內響應，流程才會執行
  - 各觸發器回報的變數會合併，後響的覆蓋先響的
  - 刪除觸發器後舊記錄立即失效；手動執行不受 `ALL` 影響
  - 觸發區會顯示時間窗說明，避免「選了 ALL 卻好像壞掉」
- **可跳轉的通知**：通知動作新增「點擊通知時」——開啟 App、開啟連結（含 deep link）、開啟 App 捷徑
  - 適合「先做這件事，之後有空再點通知接續下一件事」的流程，使用者的點擊就是觸發時機
  - 目標 App 未安裝會在權限精靈提早提示；執行時仍會發出通知，但該次執行記為失敗並寫明原因
- **流程圖示**：改用完整 Material Symbols 圖示庫並支援搜尋；桌面捷徑（釘選與長按選單）改用流程自己的圖示與顏色

### 變更（Changed）

- 版本號 1.1.1 → 1.2.0（versionCode 5 → 6）
- 通知監聽保留標題與內文兩個欄位（先前合併成單一字串，無法分別比對）
- 權限檢查涵蓋限制條件（Wi-Fi 名稱需定位、藍牙需連線權限）與通知的點擊目標 App
- 設定欄位支援條件式顯示，AI 目錄會一併告知模型「某欄位僅在 x=y 時適用」
- 運算式求值抽成共用的 `ExpressionEvaluator`，If 動作與運算式條件不會再各自漂移
- 匯入警告改寫：條件現在會被執行，未支援的條件型別會擋住流程；MacroDroid 的限制條件只帶型別、設定需重新填寫
- `docs/FLOW_SCHEMA.md` 補上條件型別、觸發器變數、`ALL` 語意與通知點擊目標

### 修正（Fixed）

- **`ALL` 觸發邏輯從未實作**：UI 可切換、schema 與資料庫也有欄位，但引擎完全不理會——選 `ALL` 實際得到的是 `ANY`
- **限制條件從未執行**：`conditions` 會存檔、匯出、匯入，執行時卻被忽略，流程照跑
- **觸發事件資料全數遺失**：`TriggerEvent.metadata` 在引擎被丟棄，且除了電量以外沒有任何觸發器填寫——「收到訊息後轉發內容」這類流程根本寫不出來
- 通知觸發只能挑 App，無法比對標題或內文；簡訊觸發只能比對寄件者
- 釘選捷徑與長按選單捷徑共用同一組定義，避免其中一方覆寫另一方的圖示與標籤

## [1.1.1] - 2026-07-16

- 無障礙服務說明補上第三項自動化用途（偵測前景 App 以觸發「App 啟動」）
- AI 目錄測試補強

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

[1.2.0]: https://github.com/ADSFAaron/NexFlow/releases/tag/v1.2.0
[1.1.1]: https://github.com/ADSFAaron/NexFlow/releases/tag/v1.1.1
[1.1.0]: https://github.com/ADSFAaron/NexFlow/releases/tag/v1.1.0
[1.0.2]: https://github.com/ADSFAaron/NexFlow/releases/tag/v1.0.2
