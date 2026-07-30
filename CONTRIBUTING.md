# 貢獻指南 Contributing to NexFlow

感謝你願意花時間改善 NexFlow！這份文件說明怎麼回報問題、怎麼建置專案，以及送 PR 前該做什麼。

> **English speakers welcome.** Issues and pull requests in English are perfectly fine — the codebase
> comments are in English, only the user-facing docs are Chinese-first.

本專案採用 [貢獻者行為準則](CODE_OF_CONDUCT.md)，參與即表示你同意遵守。
發現安全性漏洞請**不要**開公開 issue，改走 [SECURITY.md](SECURITY.md) 的私下回報流程。

---

## 我可以貢獻什麼

| 類型 | 說明 |
|------|------|
| 🐛 **回報 bug** | 用 [Bug 回報模板](https://github.com/ADSFAaron/NexFlow/issues/new/choose)，附上機型、Android 版本、flavor 與執行記錄 |
| ✨ **新的觸發／動作** | 最常見也最受歡迎的貢獻，見下方[新增 Trigger 與 Action](#新增-trigger-與-action) |
| 🌐 **翻譯** | 目前支援 en / zh-TW / zh-CN / ja，歡迎補強或新增語言 |
| 🔌 **MacroDroid 相容** | `.mdr` 目前只覆蓋部分 class type，補一種對照就是一個完整的小 PR |
| 📖 **文件** | README、`docs/FLOW_SCHEMA.md`、程式碼註解 |

不確定要不要動手？**先開 issue 討論**再寫程式，尤其是大改動——避免你花了時間卻因方向不合被退。

### 目前不打算收的

- 需要 **root** 或需要使用者安裝額外模組（Xposed / Magisk）才能運作的功能
- 為了規避 Google Play 政策而做的偽裝（政策限制的功能請放在 `github` flavor，見下方）
- 會在背景**靜默**取得使用者資料且沒有對應 UI 揭露的功能
- 大規模的架構重寫，除非事先在 issue 討論並達成共識

---

## 開發環境

| 需求 | 版本 |
|------|------|
| JDK | 17 以上（CI 用 21；Gradle toolchain 會處理各 module 的 JVM 11 目標） |
| Android SDK | Platform **37**（套件 id 是 `platforms;android-37.0`）、Build-Tools 37.0.0 |
| Android Studio | 需支援 AGP 9.2.1 / Gradle 9.5 的版本 |

```bash
git clone https://github.com/ADSFAaron/NexFlow.git
cd NexFlow
./gradlew :app:assembleGithubDebug
```

### 兩個 product flavor

```text
github  → 功能完整，發佈 APK 給 sideload 使用者（含簡訊／撥號／模擬點擊滑動）
play    → 上架 Google Play，依平台政策移除上述功能與權限
```

**所有 Gradle 任務都要帶 flavor 名稱**，不帶會因為候選任務有歧義而失敗：

```bash
./gradlew :app:assembleGithubDebug        # ✅
./gradlew :app:assembleDebug              # ❌ 有歧義
```

政策受限的程式碼放在 `app/src/github/`，`app/src/play/` 提供對應的空實作或替代品。
**動到 `app/src/main/` 的共用程式碼時，請確認 `play` flavor 也還能編譯**（CI 會跑）。

---

## 測試

### JVM 單元測試（不需裝置）

```bash
./gradlew :app:testGithubDebugUnitTest
./gradlew :core:automation:test
./gradlew :core:flow-schema:test
./gradlew :core:macrodroid-compat:test
```

> 新增 JVM 測試模組時，`testRuntimeOnly(libs.junit5.platform.launcher)` 不能漏，
> 否則 Gradle 9 會報 `Failed to load JUnit Platform`。

### 裝置 instrumented 測試

**執行前必須關閉系統動畫**，否則 Compose 文字輸入測試會因為 Espresso 等不到 main looper idle 而逾時：

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

./gradlew :app:connectedGithubDebugAndroidTest

# 測完還原
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings put global animator_duration_scale 1.0
```

instrumented 測試使用 `com.nexflow.HiltTestRunner`（HiltTestApplication），新的 Hilt 測試標上 `@HiltAndroidTest` 即可。

---

## 新增 Trigger 與 Action

這是最容易漏步驟的地方。**四步缺一不可**，少做任何一步都會被自動化測試抓到：

1. **加 enum**
   - Trigger → [core/automation/…/model/TriggerType.kt](core/automation/src/main/kotlin/com/nexflow/core/automation/model/TriggerType.kt)
   - Action → [core/automation/…/model/ActionType.kt](core/automation/src/main/kotlin/com/nexflow/core/automation/model/ActionType.kt)

2. **實作 handler / executor**

   ```kotlin
   // Trigger：Android 元件（Service / BroadcastReceiver）透過 singleton EventSource
   // （MutableSharedFlow）把事件橋接給 handler，不要讓 handler 直接碰 Android 元件
   interface TriggerHandler {
       val supportedType: TriggerType
       fun observe(trigger: Trigger, context: Context): Flow<TriggerEvent>
   }

   interface ActionExecutor {
       val supportedType: ActionType
       suspend fun execute(action: Action, variables: Map<String, String>): ActionResult
   }
   ```

3. **註冊 Hilt multibinding** — 到 [app/src/main/java/com/nexflow/di/ExecutionModule.kt](app/src/main/java/com/nexflow/di/ExecutionModule.kt) 加：

   ```kotlin
   @Binds @IntoSet
   abstract fun bindXxxHandler(impl: XxxTriggerHandler): TriggerHandler
   ```

   漏掉這步只會在**執行期**靜默失效，`ExecutionBindingsTest` 就是為了抓這個。

4. **加設定 UI 欄位** — [TriggerConfig.kt](app/src/main/java/com/nexflow/ui/flows/detail/config/TriggerConfig.kt) /
   [ActionConfig.kt](app/src/main/java/com/nexflow/ui/flows/detail/config/ActionConfig.kt)：
   picker 的標題、圖示、分類，以及各欄位的 key 與型別。
   `TriggerConfigTest` / `ActionConfigTest` 會逐一驗證**每個型別**都有 picker 資訊、欄位 key 唯一、
   摘要能產生，以及與 interpreter 的 key 契約。

另外：

- 新的字串一律進 `res/values/strings.xml`（英文）並**同步四個語系**：`values-zh-rTW`、`values-zh-rCN`、`values-ja`
- 需要新權限的話，請在 PR 說明為什麼、以及 `play` flavor 是否允許
- 如果動作會影響使用者資料或造成費用（發簡訊、撥號、HTTP），請確認執行記錄有寫清楚結果

---

## 程式碼風格

- Kotlin official code style；縮排 4 空格，程式碼註解用英文
- 註解寫**為什麼**這樣做（尤其是繞過 API 陷阱的地方），不要寫程式碼已經說明的事
- annotation processor 一律用 **KSP**，不要引入 kapt
- Compose：遵守官方文件的用法。專案裡踩過的坑記在 [CLAUDE.md](CLAUDE.md) 的「已知的 API 陷阱」表，動到相關 API 前先看一眼
- 不要提交 `**/build/`、`*.apk`、`*.aab`、`*.jks`、`local.properties`、`app/keystore.properties`（`.gitignore` 已排除，提交前可用 `git ls-files | grep "/build/"` 確認）

---

## 送出 Pull Request

1. 從 `master` 開分支：`git switch -c feat/shake-sensitivity`
2. **一個 PR 做一件事**——重構和新功能請分開，方便 review
3. 本機跑過單元測試；動到 UI 的話也跑 instrumented 測試
4. 使用者看得到的變更請寫進 [CHANGELOG.md](CHANGELOG.md) 最上方的 `## [Unreleased]` 段
   （沒有這一段就新增一段；格式依循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)）
5. 填好 PR 模板的檢查清單，CI 必須全綠
6. Commit 訊息建議用 [Conventional Commits](https://www.conventionalcommits.org/)：`feat:` / `fix:` / `docs:` / `refactor:` / `test:`

維護者會盡量在**一週內**回覆。這是業餘時間維護的專案，如果過久沒動靜，在 PR 裡 ping 一下沒關係。

### 版本與發佈

發佈由維護者處理：`app/build.gradle.kts` 的 `versionName` 一改成尚未有 tag 的新版本，
push 到 `master` 後 [release workflow](.github/workflows/release.yml) 就會自動簽章、打包、建立 tag 與 Release。
**請不要在 PR 裡自行提高版本號。**

---

## 授權

送出貢獻即表示你同意你的貢獻以 [Apache License 2.0](LICENSE) 授權釋出，
且你有權以此授權釋出這些內容。
