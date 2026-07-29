# NexFlow — Claude Code 操作手冊

## 專案概述

NexFlow 是一個 Android 自動化 App（類似 MacroDroid / Tasker）。

- **語言**: Kotlin 2.x + Jetpack Compose (Material 3)
- **DI**: Hilt 2.x
- **資料庫**: Room 2.7.x
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
| `sh.calvin.reorderable` v3.x `draggableHandle` | v3 改成 `ReorderableItemScope` member，不能 top-level import | 在 `ReorderableItem { }` lambda 內直接用，不 import |
| `Regex("""\{\{([^}]+)}}""")` | 某些 Android regex engine 對 `))}}` 報 syntax error | 改為 `\}\}` 明確 escape |
| `AccessibilityService.dispatchGesture()` | 沒在 accessibility-service XML 宣告 `android:canPerformGestures="true"` 就只會回 `false`，不丟例外、不寫 log，模擬點擊／滑動整個靜默失效 | `nexflow_accessibility_config.xml` 一定要有 `android:canPerformGestures="true"` |
| `GestureDescription.StrokeDescription` | 座標超出螢幕範圍會丟 `IllegalArgumentException`；座標來自使用者手打的設定，在 service 的 coroutine 裡丟出去會拖垮整個無障礙服務 | 建 `StrokeDescription` 要包 `runCatching`，失敗就回報 action 失敗。單點 `moveTo`（不 `lineTo`）是合法的「不移動的觸碰」，可直接當點擊用 |

### 3. 測試

- JVM 單元測試：`./gradlew test :app:testGithubDebugUnitTest`
  - `:app` 有 product flavor，`:app:testDebugUnitTest` 會因為名稱有歧義而失敗（候選為 `testGithubDebugUnitTest` / `testPlayDebugUnitTest`）
  - 新增 JVM 測試模組時，`testRuntimeOnly(libs.junit5.platform.launcher)` 不能漏，否則 Gradle 9 會報 `Failed to load JUnit Platform`
- 裝置 instrumented 測試：`./gradlew :app:connectedGithubDebugAndroidTest`
  - 同樣因為 flavor，`connectedDebugAndroidTest` 這個任務根本不存在（只有 `connectedGithubDebugAndroidTest` / `connectedPlayDebugAndroidTest`）
  - **執行前必須關閉系統動畫**，否則 Compose 文字輸入測試會因 Espresso 等不到 idle 而逾時：

    ```bash
    adb shell settings put global window_animation_scale 0
    adb shell settings put global transition_animation_scale 0
    adb shell settings put global animator_duration_scale 0
    ```

  - instrumented 測試用 `com.nexflow.HiltTestRunner`（HiltTestApplication），新的 Hilt 測試直接標 `@HiltAndroidTest` 即可

### 4. Git 規則

- **`**/build/` 不進 repo** — .gitignore 已設定
- `*.dex`, `*.bin`, `*.class`, `*.jar`, `*.apk`, `*.aab`, `*.jks` 都不進 repo
- Commit 前先 `git ls-files | grep "/build/"` 確認是否乾淨

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
