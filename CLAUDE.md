# NexFlow — Claude Code 操作手冊

## 專案概述

NexFlow 是一個 Android 自動化 App（類似 MacroDroid / Tasker）。

- **語言**: Kotlin 2.x + Jetpack Compose (Material 3)
- **DI**: Hilt 2.x
- **資料庫**: Room 2.8.x
- **最低 SDK**: 30 / 目標 SDK: 37

### Module 結構

```
app/                        # 主 App module（UI、executor、trigger、service）
core/automation/            # 核心模型、interpreter、repository 介面
core/flow-schema/           # JSON schema（匯入/匯出格式）
core/macrodroid-compat/     # MacroDroid .mdr 格式解析
```

---

## 最重要的開發規則

### 1. 先查官方文件，不要自己從頭寫

遇到任何 Jetpack Compose / Android API 用法問題：

1. **先查官方文件** — developer.android.com / d.android.com
2. **用 WebFetch 抓官方 sample code**
3. **照 sample 改**，不要憑印象自己發明實作

常見需要查文件的 API：
- `SwipeToDismissBox` → 用 `dismissState.dismissDirection`，不要用 `requireOffset()`
- `LazyColumn` reorder → sh.calvin.reorderable 的 `ReorderableItem` scope
- Material 3 Expressive API → 全部都是 `@ExperimentalMaterial3ExpressiveApi`，alpha 版 breaking change 很多
- `AnchoredDraggableState` → offset 在 layout phase 才初始化，composition phase 不能讀

### 2. 已知的 API 陷阱

| API | 問題 | 正確做法 |
|-----|------|----------|
| `SwipeToDismissBoxState.requireOffset()` | composition 時 offset=NaN，直接 crash | 用 `dismissState.dismissDirection` |
| `SwipeToDismissBoxState.targetValue` 做背景 | 預設 threshold 50%，要滑很遠才顯示 | `positionalThreshold = { it * 0.15f }` + `dismissDirection` |
| `HorizontalFloatingToolbar(expanded=...)` | M3 Expressive alpha，`expanded` 改變時收合會蓋掉子 view | 改用 `Column` + `FloatingActionButton` |
| M3 `MenuAnchorType` / `ExposedDropdownMenu` | 1.5.0-alpha26 把 `MenuAnchorType` 改名為 `ExposedDropdownMenuAnchorType`；`ExposedDropdownMenu` 從 scope member 變成 top-level extension，舊 member 是 HIDDEN 級 deprecated，症狀是「`MenuAnchorType` 找不到」連帶讓整個 `ExposedDropdownMenuBox` lambda 推導失敗，錯誤訊息會指向無關的行 | 改用新名字，並**明確 import** `androidx.compose.material3.ExposedDropdownMenu` |
| `sh.calvin.reorderable` v3.x `draggableHandle` | v3 改成 `ReorderableItemScope` member，不能 top-level import | 在 `ReorderableItem { }` lambda 內直接用，不 import |
| `Regex("""\{\{([^}]+)}}""")` | 某些 Android regex engine 對 `))}}` 報 syntax error | 改為 `\}\}` 明確 escape |
| `AccessibilityService.dispatchGesture()` | 沒在 accessibility-service XML 宣告 `android:canPerformGestures="true"` 就只會回 `false`，不丟例外、不寫 log，模擬點擊／滑動整個靜默失效 | `nexflow_accessibility_config.xml` 一定要有 `android:canPerformGestures="true"` |
| `GestureDescription.StrokeDescription` | 座標超出螢幕範圍會丟 `IllegalArgumentException`；座標來自使用者手打的設定，在 service 的 coroutine 裡丟出去會拖垮整個無障礙服務 | 建 `StrokeDescription` 要包 `runCatching`，失敗就回報 action 失敗。單點 `moveTo`（不 `lineTo`）是合法的「不移動的觸碰」，可直接當點擊用 |
| `NfcAdapter.enableReaderMode()` | **獨占且全裝置生效**：開著的時候所有感應都只進自己的 callback，別的 App 一律看不到標籤；`FLAG_READER_SKIP_NDEF_CHECK` 更直接關掉 NDEF 分派（javadoc 原話「NDEF-based tag dispatch will not be functional」）。在 `onResume` 無條件開啟＝只要 App 開著就吃掉使用者的網址標籤與交通卡 | 只在真的要用時開（有啟用中的 NFC 流程、或正在掃描設定），且只能有一個持有者。設定頁想掃卡就向那個持有者提出請求，不要自己 `enableReaderMode`——第二個持有者關閉時會把第一個的也關掉 |
| manifest `android.nfc.action.TAG_DISCOVERED` | 分派順序是 NDEF → TECH → TAG，TAG 是最後手段。若機器上只有你註冊，就成了所有其他 App 不處理的標籤（門禁卡、交通卡）的萬用接收者。API 37 已標 `@Deprecated` | 背景 NFC 用 `TECH_DISCOVERED` + 明確 tech-list，並把 filter 放在預設 `enabled="false"` 的 `<activity-alias>` 上，用 `setComponentEnabledSetting` 只在真的有 NFC 流程時打開——被停用的元件不參與 intent 解析。見 `NfcBackgroundDispatch` |
| NFC `tech-list` 匹配語意 | **反直覺**：官方定義是「tech-list 是標籤所報技術的 subset 才算匹配」，所以**列愈少技術匹配愈廣**。同一個 `<tech-list>` 內是 AND，多個 `<tech-list>` 之間是 OR。很多人以為列一個就是「只收這種」，其實是「所有帶這種的都收」 | 想收窄就多列幾個技術；想分開涵蓋就開多個 `<tech-list>` |
| `android.nfc.tech.Mifare*` 放進 tech-list | `MifareClassic`／`MifareUltralight` 的實作在 Android 上是**選配**的（javadoc：「If it is not implemented, then MifareClassic will never be enumerated in `getTechList`」），非 NXP 晶片的手機上永遠不會出現 → tech-list 安靜地永不匹配，不報錯也沒 log | tech-list 只用 `Ndef`／`NdefFormatable`／`NfcA`~`NfcV`。`Ndef` 是唯一「所有支援 NFC 的裝置都必須正確列舉」的，跨機一致 |
| `SCHEDULE_EXACT_ALARM` | targetSdk 33+ 的 App **全新安裝時預設拒絕**（備份還原到新機也是拒絕），但從舊版升級的裝置會保留已授予的 → 同一版有人正常有人遲到。沒授權時退回不精確鬧鐘，官方保證只有「一小時內」，排 9:00 的流程 9:10 才跑 | 宣告不等於拿到：一定要 `canScheduleExactAlarms()` 檢查 + 用 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 引導。並且要收 `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` 重排——鬧鐘的精度是排定當下決定的，授權後既有鬧鐘不會自己升級 |

### 3. 測試

測試分四層，全部由 `.github/workflows/ci.yml` 在 PR 上把關。**寫新測試前先問：最低能在哪一層驗？** 越低層跑越快、回饋越早。

| 層 | 位置 | 任務 | 何時用 |
|----|------|------|--------|
| JVM 單元 | `*/src/test`（純 Kotlin） | `:app:testGithubDebugUnitTest` 等 | 沒有 Android 相依的邏輯 |
| Robolectric | `app/src/test`（`@RunWith(AndroidJUnit4::class)`） | 同上 | 要 Android runtime／字串資源／Compose，但不要實機 |
| 截圖 | `app/src/screenshotTest` | `validateGithubDebugScreenshotTest` | 版面、翻譯長度、深淺色 |
| Instrumented | `app/src/androidTest` | `api30GithubDebugAndroidTest` | 只有真 Android 才有的東西（Hilt 全圖、Room migration） |

#### JVM 單元 + Robolectric

- `./gradlew :app:testGithubDebugUnitTest`（`:app` 有 flavor，`testDebugUnitTest` 會因名稱歧義失敗）
- 新增 JVM 測試模組時，`testRuntimeOnly(libs.junit5.platform.launcher)` 不能漏，否則 Gradle 9 會報 `Failed to load JUnit Platform`
- **Robolectric 與 Compose 測試是 JUnit 4，整個專案卻是 `useJUnitPlatform()`**：靠 `testRuntimeOnly(libs.junit5.vintage.engine)` 才跑得起來，漏了會「測試不見了但 build 成功」
- `app/src/test/resources/robolectric.properties` 釘了 `sdk=36`（Robolectric 4.16 沒有 API 37 的 image）與 `application=android.app.Application`（避免每個測試都去啟動 `@HiltAndroidApp` 的 `NexFlowApplication`）。要注入的 Robolectric 測試自己標 `@Config(application = HiltTestApplication::class)`

#### 截圖測試（Compose Preview Screenshot Testing）

- 8 種組合 = 4 語系（en / zh-rTW / zh-rCN / ja）× 深淺色，定義在 `app/src/screenshotTest/.../LocaleThemePreviews.kt`
- 參考圖在 `app/src/screenshotTestGithubDebug/reference/`，**要進 repo**。UI 改動是有意的就重跑 `./gradlew :app:updateGithubDebugScreenshotTest` 並一起 commit
- 只維護 github flavor 的參考圖；被截圖的 composable 都與 flavor 無關，`validatePlayDebugScreenshotTest` 沒有參考圖可比
- 預覽用 `NexFlowTheme(dynamicColor = false)`——動態色會讓不同機器算出不同顏色，圖就永遠對不起來
- **`AlertDialog` 截不到**：layoutlib 下 dialog 是獨立 window，畫面會是空的。對話框要驗就用 Robolectric 斷言，別用截圖

#### Instrumented（實體／模擬器）

- 一般跑法：`./gradlew :app:connectedGithubDebugAndroidTest`（`connectedDebugAndroidTest` 這個任務不存在，flavor 使然）
- CI 與無實機時用 Gradle Managed Device，模擬器由 Gradle 自己下載開機：

  ```bash
  ./gradlew :app:api30GithubDebugAndroidTest        # minSdk 底線，aosp-atd
  ./gradlew :app:api36GithubDebugAndroidTest        # 目前最新的 aosp-atd（沒有 API 37 的）
  ./gradlew :app:ciGroupGithubDebugAndroidTest      # 兩台都跑
  ```

- **執行前必須關閉系統動畫**（用實機時），否則 Compose 文字輸入測試會因 Espresso 等不到 idle 而逾時：

  ```bash
  adb shell settings put global window_animation_scale 0
  adb shell settings put global transition_animation_scale 0
  adb shell settings put global animator_duration_scale 0
  ```

- instrumented 測試用 `com.nexflow.HiltTestRunner`（HiltTestApplication），新的 Hilt 測試直接標 `@HiltAndroidTest` 即可
- **Room migration 有自動測試了**（`NexFlowDatabaseMigrationTest`），改 schema 一定要跟著跑。`DatabaseModule` 沒有 `fallbackToDestructiveMigration`，migration 失敗等於既有使用者一開 App 就 crash

#### Lint

- `./gradlew :app:lintGithubDebug :app:lintPlayDebug`，CI 會擋
- `checkDependencies = true`，所以 `:core:*` 也一起被檢查
- 既有問題放在 `app/lint-baseline.xml`。**修好一項就從 baseline 刪一項，不要為了讓新問題消音而整份重生**
- `NewerVersionAvailable` / `GradleDependency` / `AndroidGradlePluginVersion` 已停用：它們要連網、而且會隨上游發版自己變動

#### 實機驗證（手動）

實機是使用者的私人手機，上面有真實流程：**不要啟用或執行既有流程，不要儲存 AI 對既有流程的修改**。

- 先確認前景與螢幕狀態：`adb shell dumpsys activity activities | grep mResumedActivity`、`adb shell dumpsys power | grep mWakefulness`
- **保留資料升級**：`adb install -r`，但 flavor 與簽章必須與裝置上的一致。先確認裝的是什麼：
  （schema 層級的 migration 已由 `NexFlowDatabaseMigrationTest` 自動驗證，這裡只剩「真實資料 + 真實安裝」這一層）

  ```bash
  adb shell dumpsys package com.adsf.nexflow | grep -E "versionName|pkgFlags"   # pkgFlags 有 DEBUGGABLE 即 debug 版
  adb shell dumpsys package com.adsf.nexflow | grep RECEIVE_SMS                  # 有 = github flavor
  ```

  debug 與 release 簽章不同，**互蓋會失敗，只能先解除安裝 → 使用者資料全毀**。
- **Room 是 WAL 模式**：`run-as cat databases/nexflow.db` 只拿得到已 checkpoint 的內容，剛寫入的資料會「查不到」而讓人誤判成沒寫進去。必須連 `nexflow.db-wal` 一起拉。裝置上沒有 `sqlite3`，要拉回電腦查。
- `adb shell input text` 在注音 IME 下會被當成組字輸入（英數也一樣），文字進不了欄位。要嘛點候選字提交，要嘛換測試路徑。
- 截圖用 `adb exec-out screencap -p`；`screenrecord` 遇到螢幕休眠會直接中斷。

#### Doze / App Standby 檢查表（手動，發版前）

自動化測試完全涵蓋不到這一塊：**沒有任何一層測試會在省電狀態下執行**。而 NexFlow 的整個賣點就是「手機沒在用的時候幫你做事」，所以這是唯一能證明產品核心沒壞的驗證。Core App Quality 的 `T-Power_Management` 也是必測項。

四個會被省電機制掐住的路徑：

| 機制 | 程式碼 | Doze 下的風險 |
|------|--------|---------------|
| TIME 觸發器 | `TimeTriggerScheduler`（`setExactAndAllowWhileIdle`） | 沒有 `SCHEDULE_EXACT_ALARM` 時會退回 `setAndAllowWhileIdle`，只能在維護視窗觸發 → 定時流程晚幾分鐘到幾小時 |
| 執行引擎 | `FlowExecutionService`（FGS，`specialUse`） | 前景服務不受 Doze 限制，但**啟動它的東西**受 |
| 地理圍欄 | `GeofenceTriggerHandler` | Doze 下位置更新被批次化，進出圍欄的通知會延遲 |
| 日誌清理 | `LogPrunerWorker`（WorkManager） | 只能在維護視窗跑，這是可接受的 |

```bash
PKG=com.adsf.nexflow

# ---- Doze ----
adb shell dumpsys battery unplug          # 先讓系統以為拔了電，否則 force-idle 進不去
adb shell dumpsys deviceidle force-idle   # 立刻進 Doze，不必等真的靜置
#   → 在此驗：預定時間到時 TIME 流程有沒有跑、通知有沒有出來
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset           # 一定要 reset，忘了會讓手機一直以為沒插電

# ---- App Standby（單一 App 被冷落）----
adb shell dumpsys battery unplug
adb shell am set-inactive $PKG true
adb shell am get-inactive $PKG            # 確認真的進去了
adb shell am set-inactive $PKG false
adb shell dumpsys battery reset

# ---- Standby bucket（比 set-inactive 更接近長期不用的真實情況）----
adb shell am get-standby-bucket $PKG
adb shell am set-standby-bucket $PKG restricted   # 最嚴：鬧鐘一天一次
#   → restricted 下 TIME 觸發器基本上會失效，這是預期行為；要確認的是「安靜失敗」有沒有留下 log
adb shell am set-standby-bucket $PKG active
```

- 驗完**務必** `dumpsys battery reset`，否則使用者的手機會一直處在「沒插電」狀態，耗電行為全走樣
- 診斷用 Battery Historian 或 Android Studio 的 Power Profiler；`adb shell dumpsys deviceidle` 可以看目前處在哪個 idle 階段

### 4. Git 規則

- **`**/build/` 不進 repo** — .gitignore 已設定
- `*.dex`, `*.bin`, `*.class`, `*.jar`, `*.apk`, `*.aab`, `*.jks` 都不進 repo
- Commit 前先 `git ls-files | grep "/build/"` 確認是否乾淨
- **預設分支是 `master`，不是 `main`** —— App 內連向 GitHub 的網址要寫 `blob/master/`。曾經因為寫成 `main`，導致「關於」頁的隱私權政策與服務條款連結雙雙 404

### 5. 發布到 Google Play

- **簽章設定在 `app/keystore.properties`（不是 repo 根目錄）**，金鑰為 `app/upload-keystore.jks`，兩者都在 .gitignore。看不到就以為「這台機器不能出正式版」是錯的，先確認路徑
- 簽過章的 release 是 **V2 scheme**，`META-INF/` 底下不會有 `.RSA`／`.SF`。要判斷有沒有簽必須用：

  ```bash
  $ANDROID_HOME/build-tools/*/apksigner verify --print-certs <apk>
  ```

- 上架產物：`./gradlew :app:bundlePlayRelease`（AAB）。`assemblePlayRelease` 出的是 APK，Play 不收
- **沒有** gradle-play-publisher / fastlane，上傳是 Play Console 的人工步驟，不要嘗試自動化
- Play Console 端的聲明（無障礙使用聲明、背景定位示範影片與表單、Data safety、隱私權政策 URL）**在 1.1.0 首次上架時就已完成並過審**，不是待辦事項。隱私權政策用公開 repo 的 GitHub blob URL 即可，Play 接受
- Gemini 助手自 **1.1.0** 起就會把資料送到 Google，「對外傳輸」不是後來才新增的。只有在**傳輸內容的範圍**改變時（例如 1.3.0 開始把既有流程的完整設定值送出）才需要重新評估 Data safety 表單，而那是使用者才看得到的東西

---

## 架構重點

### Trigger 系統

```
TriggerHandler (interface)
  ├── fun supportedType: TriggerType
  └── fun observe(trigger, context): Flow<TriggerEvent>

// Android 元件橋接：用 singleton object + MutableSharedFlow
AppLaunchEventSource     ← NexFlowAccessibilityService → AppLaunchTriggerHandler
NotificationEventSource  ← NexFlowNotificationListenerService → NotificationTriggerHandler
SmsEventSource           ← SmsReceiver (BroadcastReceiver) → SmsReceivedTriggerHandler
PhoneCallEventSource     ← PhoneStateReceiver → IncomingCallTriggerHandler
NfcEventSource           ← MainActivity.enableReaderMode() → NfcTagTriggerHandler
GeofenceEventSource      ← GeofenceTransitionReceiver → GeofenceTriggerHandler
```

新增 TriggerHandler 後必須在 `ExecutionModule.kt` 加 `@Binds @IntoSet`。

### Action 系統

```
ActionExecutor (interface)
  ├── val supportedType: ActionType
  └── suspend fun execute(action, variables): ActionResult

// Scoped Storage (Android 10+)
// 路徑以 /storage/emulated/0/ 或 /sdcard/ 開頭 → 用 MediaStore
// 其他 → 直接 File.writeText()
```

新增 ActionExecutor 後必須在 `ExecutionModule.kt` 加 `@Binds @IntoSet`。

### 特殊權限需求

| 功能 | 需要使用者手動開啟 |
|------|--------------------|
| APP_LAUNCH trigger | 無障礙服務 (Accessibility Service) |
| Notification trigger | 通知存取權限 (Notification Listener) |
| Geofence | ACCESS_FINE_LOCATION + ACCESS_BACKGROUND_LOCATION |
| NFC | 只在 App 前景時有效 (MainActivity.enableReaderMode) |
| WRITE_SETTINGS | 需引導使用者到系統設定頁 |

---

## 常用 Compose 模式

### StateFlow → UI

```kotlin
val isRunning by vm.isRunning.collectAsState()
```

### 新增 Flow 後立即導航

```kotlin
// ViewModel
private val _navigateToFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
val navigateToFlow: SharedFlow<String> = _navigateToFlow.asSharedFlow()

// Screen
LaunchedEffect(vm) {
    vm.navigateToFlow.collect { flowId -> onFlowClick(flowId) }
}
```

### SwipeToDismissBox（照官方文件）

```kotlin
val dismissState = rememberSwipeToDismissBoxState(
    positionalThreshold = { it * 0.15f },   // 15% 就觸發，預設 50%
    confirmValueChange = { ... }
)

SwipeToDismissBox(
    state = dismissState,
    backgroundContent = {
        when (dismissState.dismissDirection) {        // ← 用 dismissDirection，不要用 requireOffset()
            SwipeToDismissBoxValue.StartToEnd -> { /* 左滑背景 */ }
            SwipeToDismissBoxValue.EndToStart -> { /* 右滑背景 */ }
            SwipeToDismissBoxValue.Settled -> {}
        }
    }
) { /* 主內容 */ }
```

### isRunning 狀態切換（避免 Compose batching 吃掉 true 狀態）

```kotlin
viewModelScope.launch {
    _isRunning.value = true
    yield()   // 讓 Compose 先 recompose，再繼續執行
    try { flowEngine.runNow(flowId) }
    finally { _isRunning.value = false }
}
```
