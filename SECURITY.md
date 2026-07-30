# 安全性政策 Security Policy

NexFlow 會要求無障礙服務、通知存取、簡訊、精確定位等高風險權限，也會解析外部匯入的流程檔（`.flow` / `.mdr`）。
因此安全性問題我們看得很重，**請不要**把漏洞開成公開 issue。

> English: please report vulnerabilities privately (see below). Do not open a public issue.

---

## 支援的版本

本專案由個人維護，**只修最新版**。回報前請先確認問題在最新的
[Release](https://github.com/ADSFAaron/NexFlow/releases) 上仍可重現。

| 版本 | 是否支援安全性修復 |
|------|:---:|
| 最新 release（`github` flavor APK） | ✅ |
| 最新 release（`play` flavor） | ✅ |
| 更舊的版本 | ❌ 請先升級 |

---

## 如何回報

**優先使用 GitHub 私下回報**（回報內容只有維護者看得到）：

1. 到 repo 的 [**Security** 分頁](https://github.com/ADSFAaron/NexFlow/security/advisories/new)
2. 點 **Report a vulnerability**
3. 填寫下方「回報請附上」的內容

無法使用 GitHub Advisory 時，可寄信到 **aaron-chuang@haoder.dev**，主旨請加上 `[NexFlow Security]`。

### 回報請附上

- 受影響的版本（App 內「設定 → 關於」可看到版本號）與 flavor（`github` / `play`）
- Android 版本與機型／ROM
- 影響描述：攻擊者能做到什麼（讀取什麼資料、觸發什麼動作、需不需要使用者互動）
- 重現步驟；若涉及惡意檔案，請附上**最小化**的 `.flow` / `.mdr` 樣本
- 你認為的嚴重程度，以及（若有）修補建議

### 我們的回應

這是業餘時間維護的專案，以下是**盡力而為**的目標，不是保證：

| 階段 | 目標時間 |
|------|---------|
| 確認收到回報 | 72 小時內 |
| 初步評估（是否成立、嚴重程度） | 7 天內 |
| 修復並發佈新版 | 視嚴重程度，高風險問題優先 |

修復發佈後，我們會在 GitHub Security Advisory 與 Release 說明中公開問題，並**具名感謝回報者**（除非你希望匿名）。
請在修復發佈前先不要公開細節（協調揭露）。

---

## 什麼算在範圍內

以下都歡迎回報：

- **無障礙服務濫用** — 透過 `NexFlowAccessibilityService` 取得非預期的畫面內容、或讓模擬點擊／滑動被誘導執行
- **匯入解析** — 惡意的 `.flow` / `.mdr` 檔造成崩潰、路徑穿越、寫入非預期位置，或**繞過「匯入的流程一律停用」的安全閘門**
- **變數插值** — 透過 `{{變數}}` 把資料注入 HTTP 請求、檔案路徑、簡訊內容等造成的注入問題
- **Android 元件曝露** — export 的 Activity／Service／BroadcastReceiver／ContentProvider 讓第三方 App 能觸發流程、讀寫資料
- **敏感資料處理** — Gemini API 金鑰、通知／簡訊／剪貼簿內容的儲存與外流（含寫進 log、進入雲端備份）
- **權限提升** — 讓 App 取得使用者未授權的能力，或誤導使用者授予 `WRITE_SECURE_SETTINGS`
- **發佈通道** — Release APK 的簽章、校驗值或建置流程的問題

## 什麼不算在範圍內

- **使用者自己建立的流程做了他自己指定的事** — 這是產品功能。例如流程被設定成「把通知內容 POST 到某個網址」，那就是它該做的事
- 需要**實體接觸已解鎖裝置**、已開啟 USB 偵錯，或需要 root 的攻擊
- `WRITE_SECURE_SETTINGS` 這類**由使用者自行透過 ADB 授予**的權限所帶來的能力
- 自行修改／重新打包的 APK、第三方 ROM、Xposed 之類的框架下發生的問題
- 純粹的社交工程，或「App 需要很多權限」這件事本身（README 與 App 內都有逐項說明用途）
- 第三方函式庫的已知 CVE：請直接回報給上游；若 NexFlow 用到受影響的程式路徑，歡迎開一般 issue 提醒升級

---

## 給使用者的自保建議

Sideload 的 APK 不經商店掃描，**安裝前請核對官方公佈的 SHA-256 雜湊值與簽章憑證指紋**，
確認檔案沒有被竄改或重新打包。做法見 [README 的「安全性驗證」](README.md#-安全性驗證強烈建議)。

只從本專案的 [GitHub Releases](https://github.com/ADSFAaron/NexFlow/releases) 或 Google Play 下載；
匯入他人分享的流程檔前，請先在 App 內檢視它的觸發與動作再啟用。
