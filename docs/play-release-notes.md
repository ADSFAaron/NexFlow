# Play 更新資訊（What's new）

路徑：Play 管理中心 → 發布 → 正式版 → 建立新版本 → 版本資訊，切換到各語言分頁。

- **上限每種語言 500 字元**（中日文一個字算一個字元）。超過會被擋下，不會自動截斷。
- 語言代碼要與商店資訊已啟用的語言一致：`zh-TW` / `en-US` / `ja-JP` / `zh-CN`。
- 這是寫給使用者看的，不是 CHANGELOG。挑「使用者看得到、或需要使用者動手」的事就好；
  完整的技術變更留在 [CHANGELOG.md](../CHANGELOG.md)。

---

# 上架用：1.7.1（versionCode 13）

這一版是安全性修正加上 1.7.0 之後的介面改版（1.7.0 的 AAB 打包於 `dc484f1`，之後的 commit 都在這一版）。

- **需要使用者動手**：「啟動 App 捷徑」或通知的「開啟 App 捷徑」若是用變數組成的，更新後會執行失敗，
  要重新從清單挑選。文案裡已寫明。
- **Data safety 不用重填**：傳出的資料範圍沒有變。
- **無障礙聲明要對照一次**：App 內的揭露文字改成逐項列出三種用途（截圖存到 Pictures/Screenshots、
  模擬點擊／滑動、偵測前景 App）。Play 管理中心「無障礙 API」聲明裡的用途說明若沒有提到截圖與滑動，
  要一併補上，兩邊說法不一致是審查會退件的理由。
- 文案只寫使用者看得到的行為改變，不寫為什麼要收緊、也不寫原本能怎麼利用。

---

## 繁體中文（zh-TW） — 318 字元

```
【更安全地使用別人分享的流程】
• 「啟動 App 捷徑」與通知的「開啟 App 捷徑」只能使用從清單挑選的捷徑。若您的捷徑是用變數組成的，請重新挑選一次
• 「寫入檔案」不能寫進 NexFlow 自己的資料夾
• 每個流程每分鐘最多傳 5 則簡訊、撥 5 通電話、發 60 個網路請求，超過的步驟會略過並通知您

【新外觀】
• 設定、流程、日誌、AI 助手、執行明細與小工具設定頁都改成 Android 16／17 的設定樣式
• 日誌依日期分組
• 流程編輯器點一列就能編輯，刪除移到編輯對話框裡
• AI 設定有自己的頁面，AI 輸入框整合成一個

【修正】
• 橫向時新增按鈕不再蓋住流程開關
• 無障礙服務的說明列出所有用途
```

---

## English（en-US） — 499 字元

```
SAFER SHARED FLOWS
• Shortcuts in flows and notifications must be picked from the list; if yours used a variable, pick it again
• Write file can't write into NexFlow's own folder
• Each flow: max 5 SMS, 5 calls and 60 web requests a minute. Extras are skipped and you're told

NEW LOOK
• Android 16/17 style across the app
• Logs grouped by day
• Flow editor: tap a row to edit; Delete is in its dialog

FIXED
• Add button no longer hides switches in landscape
• Accessibility notice lists every use
```

---

## 日本語（ja-JP） — 415 字元

```
【共有されたフローをより安全に】
・「アプリのショートカットを起動」と通知の「アプリのショートカットを開く」は、一覧から選んだショートカットのみ使えます。変数で組み立てていた場合は選び直してください
・「ファイルに書き込み」は NexFlow 自身のフォルダに書き込めなくなりました
・フローごとに 1 分あたり SMS 5 件、通話 5 件、ネットワーク要求 60 件まで。超えた手順はスキップして通知します

【新しいデザイン】
・設定・フロー・ログ・AI アシスタント・実行の詳細・ウィジェット設定を Android 16／17 の設定スタイルに
・ログを日付ごとに表示
・フローエディタは行をタップして編集、削除は編集ダイアログ内に
・AI 設定を専用ページに、AI 入力欄を 1 つにまとめました

【修正】
・横向きで追加ボタンがフローのスイッチに重ならなくなりました
・ユーザー補助サービスの説明にすべての用途を記載
```

---

## 简体中文（zh-CN） — 326 字元

```
【更安全地使用别人分享的流程】
• 「启动 App 快捷方式」与通知的「打开 App 快捷方式」只能使用从列表挑选的快捷方式。若您的快捷方式是用变量组成的，请重新挑选一次
• 「写入文件」不能写进 NexFlow 自己的文件夹
• 每个流程每分钟最多发 5 条短信、拨 5 通电话、发 60 个网络请求，超出的步骤会跳过并通知您

【新外观】
• 设置、流程、日志、AI 助手、执行明细与小工具设置页都改为 Android 16／17 的设置样式
• 日志按日期分组
• 流程编辑器点一行即可编辑，删除移到编辑对话框里
• AI 设置有了自己的页面，AI 输入框合而为一

【修正】
• 横屏时新增按钮不再挡住流程开关
• 无障碍服务的说明列出全部用途
```

---

# 附註

這份檔案只留「下一次要貼進 Play 的那一版」。舊版文案保留在 git 記錄裡：
`git show dc484f1:docs/play-release-notes.md`（1.7.0）、
`git show 4054922:docs/play-release-notes.md`（1.6.0）、
`git show d92d940:docs/play-release-notes.md`（1.5.0 + 1.5.1 合併版）、
`git show 618d26b:docs/play-release-notes.md`（1.5.0）。
