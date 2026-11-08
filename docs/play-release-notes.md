# Play 更新資訊（What's new）

路徑：Play 管理中心 → 發布 → 正式版 → 建立新版本 → 版本資訊，切換到各語言分頁。

- **上限每種語言 500 字元**（中日文一個字算一個字元）。超過會被擋下，不會自動截斷。
- 語言代碼要與商店資訊已啟用的語言一致：`zh-TW` / `en-US` / `ja-JP` / `zh-CN`。
- 這是寫給使用者看的，不是 CHANGELOG。挑「使用者看得到、或需要使用者動手」的事就好；
  完整的技術變更留在 [CHANGELOG.md](../CHANGELOG.md)。

---

# 上架用：1.7.0（versionCode 12）

前一版（1.6.0）的商店文案保留在 git 記錄裡：`git show 4054922:docs/play-release-notes.md`。

這一版沒有需要使用者動手的項目。但**上架前要到 Play 管理中心重新檢視 Data safety**：AI 助手開始
會把使用者附加的圖片傳給 Google，傳輸內容的範圍變了。文案裡也因此明講圖片會傳給 Gemini。

「其他服務（OpenAI 相容）」只在 debug 版出現，不要寫進文案。

## 繁體中文（zh-TW）— 231 字元

```
【AI 助手看得懂圖片】
輸入框左側的圖片鈕，每則訊息最多附 4 張。說不清楚是哪個按鈕時，一張截圖就夠了。圖片會和訊息一起傳給 Gemini。

【小工具顯示你挑的流程】
每個總覽小工具都能選擇「自動」或自己挑流程，最多 12 個。

【AI 助手更省也更穩】
• 建立流程少一次請求，額度用得更慢
• Gemini 忙碌或每分鐘額度用完時，自動等待後繼續
• 不會再選用手機上沒有的 App

【變更】
• AI 對話按 Enter 改為換行，送出請按送出鈕
```

---

## English（en-US）— 497 字元

```
AI ASSISTANT READS IMAGES
Attach up to 4 per message with the image button. A screenshot beats describing a button. Images go to Gemini with your message.

PICK WHAT A WIDGET SHOWS
Each overview widget can stay automatic or show flows you choose, up to 12.

A LEANER, STEADIER ASSISTANT
• One request fewer per flow, so quota lasts longer
• Waits and resumes when Gemini is busy or the minute's quota is spent
• No more apps you don't have

CHANGED
• Enter adds a line in AI chat; tap send to send
```

---

## 日本語（ja-JP）— 321 字元

```
【AI アシスタントが画像を理解】
入力欄の画像ボタンから、1 メッセージにつき 4 枚まで添付できます。どのボタンか説明しづらいときはスクリーンショットをどうぞ。画像はメッセージと一緒に Gemini へ送信されます。

【ウィジェットの表示を選べる】
概要ウィジェットごとに「自動」か、自分で選んだフロー（最大 12 個）を表示できます。

【AI アシスタントがより効率的で安定】
・フロー作成のリクエストが 1 回減り、クォータが長持ち
・Gemini の混雑時や 1 分あたりの上限時は、自動で待ってから続行
・端末にないアプリを選ばなくなりました

【変更】
・AI チャットの Enter は改行になりました。送信は送信ボタンで
```

---

## 简体中文（zh-CN）— 233 字元

```
【AI 助手看得懂图片】
输入框左侧的图片按钮，每条消息最多附 4 张。说不清是哪个按钮时，一张截图就够了。图片会和消息一起发送给 Gemini。

【小工具显示你挑的流程】
每个总览小工具都能选择「自动」或自己挑流程，最多 12 个。

【AI 助手更省也更稳】
• 创建流程少一次请求，额度用得更慢
• Gemini 繁忙或每分钟额度用完时，自动等待后继续
• 不会再选用手机上没有的 App

【变更】
• AI 对话按 Enter 改为换行，发送请按发送按钮
```

---

# 附註

這份檔案只留「下一次要貼進 Play 的那一版」。舊版文案保留在 git 記錄裡：
`git show d92d940:docs/play-release-notes.md`（1.5.0 + 1.5.1 合併版）、
`git show 618d26b:docs/play-release-notes.md`（1.5.0）。
