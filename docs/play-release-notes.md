# Play 更新資訊（What's new）

路徑：Play 管理中心 → 發布 → 正式版 → 建立新版本 → 版本資訊，切換到各語言分頁。

- **上限每種語言 500 字元**（中日文一個字算一個字元）。超過會被擋下，不會自動截斷。
- 語言代碼要與商店資訊已啟用的語言一致：`zh-TW` / `en-US` / `ja-JP` / `zh-CN`。
- 這是寫給使用者看的，不是 CHANGELOG。挑「使用者看得到、或需要使用者動手」的事就好；
  完整的技術變更留在 [CHANGELOG.md](../CHANGELOG.md)。

---

# 上架用：1.5.1（versionCode 10）

**1.5.0 沒有上架 Play**，所以商店的使用者是從 1.4.0 直接跳到 1.5.1。下面這份文案
因此涵蓋 1.5.0 與 1.5.1 兩版的內容，不是只有 1.5.1 的部分。

排在最前面的是**排程權限**以外唯一的新功能，而排程權限是這兩版裡唯一
**需要使用者自己動手授權**才會好的項目——其餘都是安裝完就生效。

英文版塞不下音量摘要那一條（500 字元上限，英文最不省字），所以只有中日文保留；
那是四件事裡最小的一件，捨它最不虧。

## 繁體中文（zh-TW）— 232 字元

```
【看得見正在執行的流程】
流程執行時，卡片右下角會顯示「執行中」，不論是側滑執行、時間到或背景觸發。通知會寫出正在執行哪一個流程；長按右上角的閃電可列出全部。

【排程準時了】
排定 9:00 卻晚十分鐘才跑，是因為缺少「鬧鐘和提醒」權限。流程卡片現在會提示，並帶你前往授權。

【NFC 修好了】
App 開著時不再獨占感應，網址標籤與交通卡恢復正常；而在你有啟用中的 NFC 流程時，關著 App 感應一樣會觸發。

【其他】
• 音量動作的摘要不再顯示「？」
```

---

## English（en-US）— 483 字元

```
SEE WHAT'S RUNNING
A running flow shows "Running" on its card — swiped, scheduled or triggered in the background. The notification names it, and a long press on the lightning bolt lists them all.

SCHEDULES RUN ON TIME
A flow set for 9:00 could run ten minutes late: the "Alarms & reminders" permission was never requested. Flows now flag it and take you there.

NFC FIXED
Tags are no longer taken over while the app is open, and work with the app closed once an NFC flow is enabled.
```

---

## 日本語（ja-JP）— 289 字元

```
【実行中のフローが見えます】
実行中はカード右下に「実行中」と表示されます。スワイプ実行でも、時刻やバックグラウンドのトリガーでも同じです。通知にはフロー名が表示され、右上の稲妻の長押しで一覧できます。

【スケジュールが時間どおりに】
9:00 のフローが10分遅れることがありました。「アラームとリマインダー」の権限を要求していなかったためです。不足時はカードに表示し、設定へ案内します。

【NFC の修正】
アプリ起動中にタグを占有しなくなりました。有効な NFC フローがあれば、アプリを閉じていてもタグが動作します。

・音量アクションの概要が「?」にならないように
```

---

## 简体中文（zh-CN）— 232 字元

```
【看得见正在运行的流程】
流程运行时，卡片右下角会显示「运行中」，无论是侧滑运行、时间到或后台触发。通知会写出正在运行哪一个流程；长按右上角的闪电可列出全部。

【定时准时了】
设定 9:00 却晚十分钟才跑，是因为缺少「闹钟和提醒」权限。流程卡片现在会提示，并带你前往授权。

【NFC 修好了】
App 开着时不再独占感应，网址标签与交通卡恢复正常；而在你有启用中的 NFC 流程时，关着 App 感应一样会触发。

【其他】
• 音量动作的摘要不再显示「？」
```

---

# 附註

若日後需要分版本的文案（例如補發 GitHub Release），1.5.0 與 1.5.1 各自的四語版本
保留在 git 記錄裡：`git show 618d26b:docs/play-release-notes.md`（1.5.0）與
`git show 897abb1:docs/play-release-notes.md`（1.5.0 + 1.5.1 分開版）。
