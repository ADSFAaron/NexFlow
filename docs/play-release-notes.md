# Play 更新資訊（What's new）

路徑：Play 管理中心 → 發布 → 正式版 → 建立新版本 → 版本資訊，切換到各語言分頁。

- **上限每種語言 500 字元**（中日文一個字算一個字元）。超過會被擋下，不會自動截斷。
- 語言代碼要與商店資訊已啟用的語言一致：`zh-TW` / `en-US` / `ja-JP` / `zh-CN`。
- 這是寫給使用者看的，不是 CHANGELOG。挑「使用者看得到、或需要使用者動手」的事就好；
  完整的技術變更留在 [CHANGELOG.md](../CHANGELOG.md)。

---

# 上架用：1.7.3（versionCode 15）

Play 上目前是 1.7.2（2026-11-13 上架），所以這次只寫 1.7.2 之後的變更：

- **App 更新後自動恢復執行**（PR #41）。這個廣播送給更新後的新版本，所以從 1.7.2 更新到 1.7.3 的那一次就會生效。
- **匯入檢查**（PR #40，商店 CLI 一起帶進來的 App 端修正）：格式錯誤的檔案直接拒絕；這個版本跑不了的步驟在匯入結果逐項列出。
- **Data safety、無障礙聲明都不用動**：傳出的資料與無障礙用途都沒有變。

---

## 繁體中文（zh-TW） — 119 字元

```
【修正】
• App 更新後，自動化會自己恢復執行，不必再打開 App 一次
• 匯入格式錯誤的流程檔時會直接拒絕，並列出哪裡不對，不會再留下半套的流程
• 匯入的流程如果有這個版本無法執行的步驟，匯入結果會逐項標出，點一下就能跳過去修改
```

---

## English（en-US） — 303 字元

```
FIXED
• Automation now restarts by itself after an app update, no need to open NexFlow
• Importing a malformed flow file is refused with a list of what's wrong, instead of saving a half-broken flow
• Imported steps this version can't run are listed in the import result; tap one to jump to it and fix it
```

---

## 日本語（ja-JP） — 138 字元

```
【修正】
・アプリの更新後、自動化が自動で再開されます（アプリを開き直す必要はありません）
・形式が正しくないフローファイルは取り込まず、問題のある箇所を一覧で表示します
・このバージョンで実行できないステップは取り込み結果に表示され、タップするとその行に移動して修正できます
```

---

## 简体中文（zh-CN） — 120 字元

```
【修正】
• App 更新后，自动化会自己恢复运行，不必再打开 App 一次
• 导入格式错误的流程文件时会直接拒绝，并列出哪里不对，不会再留下残缺的流程
• 导入的流程如果有这个版本无法执行的步骤，导入结果会逐项标出，点一下即可跳过去修改
```

---

# 附註

這份檔案只留「下一次要貼進 Play 的那一版」。舊版文案保留在 git 記錄裡：
`git show c150b83:docs/play-release-notes.md`（1.7.2，Play 從 1.7.0 直接跳上來的那一版）、
`git show dc484f1:docs/play-release-notes.md`（1.7.0）、
`git show 4054922:docs/play-release-notes.md`（1.6.0）、
`git show d92d940:docs/play-release-notes.md`（1.5.0 + 1.5.1 合併版）、
`git show 618d26b:docs/play-release-notes.md`（1.5.0）。
