<!--
感謝貢獻！送出前請看一下 CONTRIBUTING.md。
大改動建議先開 issue 討論過再送 PR。
-->

## 這個 PR 做了什麼

<!-- 一兩句說明變更內容，以及「為什麼」需要這個改動 -->

Closes #

## 變更類型

- [ ] 🐛 Bug 修復
- [ ] ✨ 新的觸發條件（Trigger）或動作（Action）
- [ ] 🎨 UI／使用體驗
- [ ] 🌐 翻譯／在地化
- [ ] 📖 文件
- [ ] ♻️ 重構（行為不變）
- [ ] 🔧 建置／CI

## 檢查清單

- [ ] 本機跑過單元測試：`:app:testGithubDebugUnitTest` 與 `:core:*:test`
- [ ] 動到 `app/src/main/` 的共用程式碼時，確認 **`play` flavor 也能編譯**（`:app:testPlayDebugUnitTest`）
- [ ] 新增／修改的字串已同步四個語系：`values`（en）、`values-zh-rTW`、`values-zh-rCN`、`values-ja`
- [ ] 使用者看得到的變更已寫進 [CHANGELOG.md](../CHANGELOG.md) 的 `## [Unreleased]` 段
- [ ] **沒有**修改 `app/build.gradle.kts` 的 `versionName` / `versionCode`（版本由維護者發佈時處理）
- [ ] 沒有提交建置產物或機密檔案（`**/build/`、`*.apk`、`*.jks`、`keystore.properties`）

## 如果新增了 Trigger／Action

四個步驟缺一不可（漏了只會在執行期靜默失效）：

- [ ] 在 `TriggerType.kt` / `ActionType.kt` 加了 enum
- [ ] 實作了 `TriggerHandler` / `ActionExecutor`
- [ ] 在 `app/src/main/java/com/nexflow/di/ExecutionModule.kt` 加了 `@Binds @IntoSet`
- [ ] 在 `TriggerConfig.kt` / `ActionConfig.kt` 補了 picker 資訊與設定欄位
- [ ] 需要新權限的話，已在下方說明用途，並確認 `play` flavor 是否允許

## 新權限／敏感能力

<!-- 沒有就寫「無」。有的話請說明：權限名稱、為什麼需要、play flavor 能不能用 -->

無

## 測試方式

<!-- 你怎麼驗證這個改動？用了什麼機型／Android 版本？ -->

## 截圖／錄影

<!-- 有 UI 變更請附上前後對照 -->
