# Play 更新資訊（What's new）

路徑：Play 管理中心 → 發布 → 正式版 → 建立新版本 → 版本資訊，切換到各語言分頁。

- **上限每種語言 500 字元**（中日文一個字算一個字元）。超過會被擋下，不會自動截斷。
- 語言代碼要與商店資訊已啟用的語言一致：`zh-TW` / `en-US` / `ja-JP` / `zh-CN`。
- 這是寫給使用者看的，不是 CHANGELOG。挑「使用者看得到、或需要使用者動手」的事就好；
  完整的技術變更留在 [CHANGELOG.md](../CHANGELOG.md)。
- 這一版最該講的是**排程權限**：那是唯一需要使用者自己去授權才會好的項目，
  其餘都是安裝完就生效。

---

# 1.5.0

## 繁體中文（zh-TW）— 223 字元

```
【看得見正在執行的流程】
流程執行中，卡片右下角會顯示「執行中」。不論是側滑執行、時間到，或是背景觸發，都看得到。通知會寫出正在執行哪一個流程；長按右上角的閃電，可列出目前所有執行中的流程。

【排程準時了】
排定 9:00 卻晚十分鐘才跑，是因為缺少「鬧鐘和提醒」權限。流程卡片現在會提示並帶你前往授權，授權後既有排程立即重新校正。

【修正】
• App 開著時不再獨占 NFC，感應網址標籤或交通卡恢復正常
• 音量動作的摘要不再顯示「？」
```

---

## English（en-US）— 497 字元

```
SEE WHAT'S RUNNING
A running flow now shows "Running" on its card — whether you swiped it, a schedule fired it, or a background trigger did. The notification names the flow, and a long press on the lightning bolt lists them all.

SCHEDULES RUN ON TIME
A flow set for 9:00 could run ten minutes late: the "Alarms & reminders" permission was never requested. Flows now flag it and take you there.

FIXED
• NFC is no longer taken over while the app is open
• Volume actions no longer summarise as "?"
```

---

## 日本語（ja-JP）— 275 字元

```
【実行中のフローが見えます】
フローの実行中、カード右下に「実行中」と表示されます。スワイプ実行でも、時刻やバックグラウンドのトリガーでも同じです。通知には実行中のフロー名が表示され、右上の稲妻を長押しすると実行中のフローが一覧できます。

【スケジュールが時間どおりに】
9:00 に設定したフローが10分遅れることがありました。「アラームとリマインダー」の権限を要求していなかったためです。不足時はカードに表示し、設定画面へ案内します。

【修正】
・アプリ起動中に NFC を占有しなくなりました
・音量アクションの概要が「?」にならないように
```

---

## 简体中文（zh-CN）— 223 字元

```
【看得见正在运行的流程】
流程运行中，卡片右下角会显示「运行中」。无论是侧滑运行、时间到，还是后台触发，都看得到。通知会写出正在运行哪一个流程；长按右上角的闪电，可列出当前所有运行中的流程。

【定时准时了】
设定 9:00 却晚十分钟才跑，是因为缺少「闹钟和提醒」权限。流程卡片现在会提示并带你前往授权，授权后既有定时立即重新校正。

【修正】
• App 开着时不再独占 NFC，感应网址标签或交通卡恢复正常
• 音量动作的摘要不再显示「？」
```

---

# 1.5.1

## 繁體中文（zh-TW）— 231 字元

```
【關著 App 也能用 NFC 標籤觸發流程】
1.5.0 為了不讓 NexFlow 攔截門禁卡、交通卡，拿掉了背景 NFC。現在補回來，但只發給真的需要的人：只有在你有「啟用中的 NFC 流程」時，NexFlow 才會登記接收標籤；沒有的話完全不會出現在感應名單裡。

感應後不會跳出任何畫面，流程直接在背景執行。

【說明】
• 寫了網址的標籤仍會開啟瀏覽器，這是系統的優先順序
• 交通卡、門禁卡這類非 NDEF 標籤，背景不會觸發；App 開著時仍讀得到
```

---

## English（en-US）— 470 字元

```
NFC TAGS WORK WITH THE APP CLOSED
1.5.0 dropped background NFC so NexFlow would stop intercepting door badges and transit cards. It's back, but only for people who need it: NexFlow registers for tags only while you have an enabled NFC flow, and otherwise never appears in tag handling at all.

Scanning shows nothing on screen — the flow just runs.

NOTES
• Tags carrying a URL still open the browser
• Non-NDEF tags such as transit cards won't trigger in the background
```

---

## 日本語（ja-JP）— 270 字元

```
【アプリを閉じていても NFC タグでフローが動きます】
1.5.0 では、社員証や交通系 IC カードを横取りしないよう、バックグラウンドの NFC を外していました。今回、必要な人にだけ戻します。有効な NFC フローがあるときだけタグの受け取りに登録し、なければ一覧に一切現れません。

タグをかざしても画面には何も出ず、フローだけが実行されます。

【注意】
・URL が書かれたタグはブラウザが開きます（システムの優先順位）
・交通系 IC など NDEF でないタグはバックグラウンドでは動きません。アプリを開いていれば読めます
```

---

## 简体中文（zh-CN）— 231 字元

```
【关着 App 也能用 NFC 标签触发流程】
1.5.0 为了不让 NexFlow 拦截门禁卡、交通卡，去掉了后台 NFC。现在补回来，但只发给真的需要的人：只有在你有「启用中的 NFC 流程」时，NexFlow 才会登记接收标签；没有的话完全不会出现在感应名单里。

感应后不会弹出任何画面，流程直接在后台执行。

【说明】
• 写了网址的标签仍会打开浏览器，这是系统的优先顺序
• 交通卡、门禁卡这类非 NDEF 标签，后台不会触发；App 开着时仍读得到
```
