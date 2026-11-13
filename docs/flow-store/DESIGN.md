# Flow Store 設計文件

> 這份是 Flow Store 的**定案設計**。實作中遇到和這裡不一致的地方，先改這份文件、寫明原因，再改程式。
> 追蹤：GitHub Project [NexFlow Flow Store](https://github.com/users/ADSFAaron/projects/5)；各 Phase 的父 issue 見文末。

## 目標

自己從零組流程太慢。開一個公開 repo [`ADSFAaron/nexflow-flow-store`](https://github.com/ADSFAaron/nexflow-flow-store)
放現成的流程，任何人都能投稿；NexFlow 在 App 內瀏覽、安裝、更新、還原。

**威脅模型**：商店流程的作者不是執行它的人。流程裡外部可控的資料（`trigger.body`、`trigger.text`、
`trigger.sender`、HTTP 回應⋯）由陌生人決定。每一條規則都要能回答「如果作者有惡意會怎樣」。

---

## 進度

每完成一個 sub-issue 就更新這裡，讓接手的人（或壓縮過記憶的 Claude）知道停在哪。

| 項目 | 狀態 | 分支／備註 |
|---|---|---|
| 1.7.1 執行期安全底線（速率限制、捷徑 intent 字面值等） | 完成，待發版 | `fix/security-hardening` |
| P1 #13 schema error 不存檔 | 完成 | `feat/flow-store-p1`；validator 分 ERROR／WARNING，見 P1 段落 |
| P1 #14 ID 相同發新 UUID | 完成 | 含 MDR；ID 格式不符 UUID 改列 STRICT（只有商店擋） |
| P1 #15 未知類型 warning | 完成 | `UNKNOWN_TRIGGER_TYPE`／`UNKNOWN_ACTION_TYPE`／`NOT_IN_THIS_BUILD`，.flow 與 .mdr 都套用 |
| P1 #16 `FlowConfigValidator` | 完成 | `com.nexflow.validation`；`validate(flow)` 對未知／此 flavor 隱藏的類型報錯（匯入只警告） |
| P1 #17 `PersonalFields`／`SensitiveSinks` | 完成 | 合併成 `core/flow-schema` 的 `FieldPolicy`（PERSONAL／SINKS／NEUTRAL，依 key 名稱分類） |
| P1 #18 `flow-catalog.json` | 完成 | 模型 `FlowCatalog`（core/flow-schema）；`FlowCatalogTest` 產生／比對；`since` 目前全是 1.7.1，P3 定 baseline 時重設 |
| P1 #19 `flow-store-tools` CLI | 完成 | 規則在 `StoreValidator`；檔案格式見「商店的檔案格式」；`./gradlew :core:flow-store-tools:installDist` 產生執行檔 |
| P2 CLI 補強 | 完成 | `validate --json/--format github`（含 dry run）、featured／maintainers／official、商店網站頁面（取代每個資料夾的 README） |
| P2 store#2–#8 商店 repo 內容 | 本機完成，未推 | 商店 repo 分支 `p2-store`；GitHub 端設定（Ruleset、Environment、App、金鑰、Pages）待使用者做，清單在商店 repo 的 `MAINTAINING.md` |

---

## 已決定的事

### 流程內容

| 項目 | 決定 |
|---|---|
| 安裝時要填的值 | 用 `setup`，**整欄由使用者填** |
| 機密值 | `secret: true` 的值在 UI、匯出、送給 AI、執行紀錄、錯誤訊息裡一律遮蔽或清掉；AI 修改流程時不得覆寫。存放維持 Room，不另外加密 |
| 高風險動作 | SIMULATE_*、LAUNCH_SHORTCUT 可以上架，但座標、intent、package 一律在 setup 由使用者填 |
| 全域變數 | 只能用自己宣告的，安裝時改名為 `g:store_<slug 轉底線>[_<n>]__<name>` |
| 外傳資料 | 外部可控的變數只能放進 HTTP 的 body 與 header。URL 要嘛寫死為 https 加固定 host，要嘛整欄由使用者填 |
| 個資 key 能不能放 `{{變數}}` | 一律不行，**不開例外**，所以「自動回覆給寄件者」這類流程不能上架 |

### 安裝與維護

| 項目 | 決定 |
|---|---|
| 重複安裝 | 可以。每一份各自有安裝紀錄，全域變數前綴加序號 `_<n>` |
| 更新 | **只在打開商店時檢查**，不做背景檢查。風險或權限變多時先停用；可一鍵還原前一版 |
| 下架（revoked） | MainActivity 進入前景時檢查，每 6 小時最多一次。惡意的自動停用並通知，壞掉的只通知 |
| 刪除流程 | 確認對話框預設勾選「一併刪除它建立的變數」；還有別的流程在用時不顯示這個選項 |

### 審核與營運

| 項目 | 決定 |
|---|---|
| 審核 | CI ＋ 維護者核准。Ruleset 的 bypass **只給維護者本人帳號**，Actions 與 App 都不能繞過 |
| 投稿 | 三條路：PR；issue 表單由**私人 GitHub App** 開 PR（GITHUB_TOKEN 開的 PR 不會觸發 workflow）；App 內匯出 |
| 作者 | 從 GitHub 帳號自動判定。改別人的流程由維護者核准並通知原作者 |
| 語言 | 至少一種，App 依 fallback 順序顯示並標註原文語言 |
| 檢舉 | **只用 GitHub issue**，不提供 email（見「風險」） |
| 留言與評分 | 先不做 |
| 授權 | 商店內容 CC0-1.0 |

### 技術

| 項目 | 決定 |
|---|---|
| 驗證器 | Kotlin CLI（`core/flow-store-tools`），重用 NexFlow 的程式碼，CI 與 App 用同一套規則 |
| `min_app_version` | 由 catalog 的 `since` 自動算出 |
| 相容性 | NexFlow CI 加一個不擋 PR 的 `store-compat` job |
| 網址 | `https://adsfaaron.github.io/nexflow-flow-store/`。**repo 與 GitHub 帳號 `ADSFAaron` 永遠不改名**（帳號名是 Pages 網址的一部分），不設備援來源 |
| 完整性 | sha256 ＋ Ed25519 簽章；簽章用 `production` Environment 的 secret，要人工核准。App 內建 current 與 next 兩把公鑰供輪替 |
| 速率限制 | 1.7.1 起套用在**所有**流程：每個流程每分鐘 SMS 5、撥號 5、HTTP 60。超過時略過那一步並記入紀錄，每個流程每小時最多通知一次，不停用（`SlidingWindowRateLimiter`） |

### 範圍與介面

| 項目 | 決定 |
|---|---|
| 入口 | 流程頁的 FAB 選單 |
| 分類 | `flows/<slug>/` 平鋪，用 `tags` 分類（前期流程少，不做目錄分類） |
| AI 整合 | P6：AI 助手先搜尋商店再產生流程 |

### 刻意不做、接受的風險（不是遺漏）

- 不做背景更新檢查：使用者沒開 App 的期間，被下架的惡意流程會繼續跑，直到下次開 App。
- 不設備援下載來源：連不到 GitHub Pages 的地區用不了商店。
- 不做留言、評分、下載數：沒有後端。
- secret 不另外加密：手機被 root 或實體取得時可讀到。雲端備份已排除 DB。
- 只有一位維護者：維護者不在的期間沒有人能下架。
- 簽章金鑰外洩後，舊版 App 在更新前仍會信任那把 key。

### 風險（待確認）

- 只用 GitHub 檢舉，沒有帳號的使用者無法檢舉。Play 的 UGC 政策要求 App 內檢舉管道，**P3 上架前要再確認是否合規**；不夠就補一個免帳號的管道。

---

## 和執行期規則的關係（1.7.1 已上線的部分）

商店的規則大多在 CLI 靜態檢查；執行期只保留少數「不管流程從哪來都要擋」的底線。設計 setup 與安裝時要遵守：

- **`ActionPolicy.LITERAL_ONLY_KEYS`**（目前是 LAUNCH_SHORTCUT 的 `intent_uri`、NOTIFICATION 的 `tap_shortcut_uri`）
  在執行期**不能含任何 `{{變數}}`**，連全域變數也不行。所以 setup 值**必須在安裝時直接寫進 config 成為字面值**，
  不能用「把 setup 值存成變數、config 用 `{{…}}` 參照」的方式實作。
- 執行期**不追蹤污染來源**（taint）。「外部變數不能流進哪些欄位」完全由 P1 CLI 的 `validate` 擋在投稿階段。
- 「開啟 App」的 `package_name` 執行期允許 `{{trigger.package}}`；商店端是否允許由 `PersonalFields` 規則決定（目前列為個資 key → 不允許變數）。

---

## 可以直接用的既有程式碼

| 用途 | 位置 |
|---|---|
| 匯入 | `ImportViewModel`、`FlowJsonMapper.toDomain(forceDisabled = true)` |
| 權限 | `FlowPermissionChecker`、`FlowsViewModel.toggleEnabled` 的權限提醒 |
| config 驗證 | `FlowDraftMapper.validateConfig`、`AiCatalog.*` |
| 填值 UI | `ConfigDialog`（`FlowDetailScreen.kt`） |
| 判斷 activity 是否對外公開 | `AppShortcutQuery.kt` |
| 變數代換 | `FlowInterpreter.interpolateAction`，所有 config 值都會代換 |
| 外部可控的變數 | `TriggerVariables`（`trigger.body`、`text`、`title`、`sender`、`number` 等），以及 HTTP 回應 |
| 變數名稱規則 | `^[a-zA-Z_][a-zA-Z0-9_]*$`（`FlowSchemaValidator.kt`）——不能有 `.` 或 `-` |
| FileProvider 開放範圍 | 只有 `cache/flow_exports/` |
| 雲端備份 | 已排除 `nexflow.db` 與 `nexflow_ai.xml` |
| Room | version 5，全部 AutoMigration |
| HTTP | Ktor `HttpClient`，目前在 `ExecutionModule` |

---

## 各 Phase 設計

依賴：P1 → P2、P1 → P3 → P4、P3 → P5、P3 → P6。P3 要等 1.7.1 發版之後。

### P1（NexFlow）：修匯入，建立共用工具 — [#12](https://github.com/ADSFAaron/NexFlow/issues/12)

**修匯入**
- 有 schema error 就不存檔（#13）。
  - **實作時的修正**：`FlowSchemaValidator` 的規則分成 ERROR 與 WARNING，只有 ERROR 擋匯入。App 自己的編輯器
    做得出來的情況（沒有觸發器或動作、名稱 > 100 字、描述 > 2000 字、變數名稱不符 `^[a-zA-Z_][a-zA-Z0-9_]*$`）
    是 WARNING，否則使用者自己匯出的流程會匯不回來。商店 CLI 要比匯入嚴格，所有 WARNING 都當錯誤處理。
  - 第三級 STRICT：裝置上無害、只有商店要擋的問題。目前是「ID 不是 UUID」——MacroDroid 匯入的流程與項目 ID
    是數字，項目又存在流程那一列的 JSON 裡，任何唯一字串都能用。匯入完全忽略 STRICT，CLI 當錯誤。
- 流程 ID 和既有的相同時發新 UUID，避免 REPLACE 觸發 CASCADE 刪掉執行紀錄（#14）。
- 未知類型與 flavor 隱藏的類型要出 warning（#15）。

**共用元件**
- `FlowConfigValidator`：從 `FlowDraftMapper` 抽出來（#16）。
- `PersonalFields`：ssid、lat/lng、number、contact、sender、tag_id、device_name、path、x/y、package_name、intent_uri、image、headers。
  `SensitiveSinks`：taint 檢查用的 sink 清單（#17）。
  - **實作**：兩份清單合成 `core/flow-schema` 的 `FieldPolicy`，放在純 JVM 模組讓 CLI 直接用。key 在每個步驟裡意思都一樣，
    所以依名稱分類成 PERSONAL、SINKS、NEUTRAL（一個 key 可以同時是 PERSONAL 與 SINK，例如 `number`）。
    `FieldPolicyTest` 走過所有編輯器，出現沒分類的 key、或清單裡有已不存在的 key 都會失敗。
  - **和「外傳資料」那條決定的對齊**：`headers` 在個資清單裡（常放 API token），而個資 key 一律不能放變數，
    所以「外部變數只能進 HTTP body 與 header」實際上是**只能進 body**。外部變數仍可進 NEUTRAL 欄位
    （例如通知文字、簡訊內容），因為收件對象（`number`）與網址本身已由 PERSONAL／SINKS 規則鎖住。
  - **`expression` 不是 sink**（#19 實作時改的）：用 IF 判斷簡訊或通知內容是最常見的寫法，外部資料進到條件式
    最多只能決定走作者寫好的哪一條分支，擋掉反而讓大部分實用流程上不了架。

**`docs/flow-catalog.json`**（golden 檔，#18）
- 內容：key、允許值、範圍、是否為個資或 sink、flavor、`since`。
- `since` 的 baseline 是「第一個帶有商店功能的版本」，到 P3 時再決定。

**`core/flow-store-tools` CLI**（#19）
- 子命令：`validate`、`dry-run`、`build-index`、`from-export`。
- `validate` 檢查：
  - schema 與 catalog 都要符合，**triggers、conditions、actions 三種都檢查**（目前 `FlowDraftMapper` 只檢查 trigger 與 action；catalog 要涵蓋 `ConditionConfig`）。
  - 個資 key 要嘛為空、要嘛在 setup 裡，而且不能放任何變數。
  - taint 與 URL 規則照上面的決定；URL 規則套用在 HTTP_REQUEST 的 `url`、OPEN_URL 的 `url`、NOTIFICATION 的 `tap_url`。
  - host 不能是 localhost、私有 IP 或 link-local（`network_security_config` 允許 localhost 明文，等於能打裝置上其他 App 的本機服務）。
  - 只能讀寫自己宣告的 `g:` 變數。
  - slug 格式 `^[a-z0-9-]{3,50}$`，不能改名，**被下架的 slug 永遠不能再用**。
  - 至少一種語言；改了 `flow.json` 就要遞增 `version`。
  - REPEAT 次數 ≤ 100，REGEX 長度有上限（執行期上限是 500 字元）。
  - 算出 `min_app_version` 與 flavor，列出高風險動作。
- 轉換邏輯放在 `core/flow-schema`，P5 也會用。

### 商店的檔案格式（P1 #19 定案，P2–P5 共用）

模型在 `core/flow-schema` 的 `StoreFormat.kt`，CLI 與 App 共用。

**`flows/<slug>/flow.json`**：就是一般的 `.flow` 匯出檔（`FlowJson`），所以 App 的匯出可以直接變成投稿。
被 setup 接管的欄位在這裡**留空字串**。

**`flows/<slug>/meta.json`**（人寫，CI 驗證）
```json
{
  "slug": "silent-at-night",
  "version": 3,
  "languages": {
    "en":    { "title": "Silent at night", "description": "…" },
    "zh-TW": { "title": "晚上自動靜音",    "description": "…" }
  },
  "tags": ["sleep", "sound"],
  "setup": [
    {
      "id": "home_wifi",
      "target": { "kind": "trigger", "item": "<trigger 的 id>", "key": "ssid" },
      "label": { "en": "Your home Wi-Fi name", "zh-TW": "家裡的 Wi-Fi 名稱" },
      "secret": false
    }
  ]
}
```
- `slug` 必須等於資料夾名稱。`version` 是正整數，`flow.json` 或 `meta.json` 有改就要比 base 大。
- `languages` 至少一種，`title` 不可空白。語言代碼用 App 支援的 `en`／`zh-TW`／`zh-CN`／`ja`。
- `setup[].target.kind` 是 `trigger`／`condition`／`action`，`item` 是該步驟的 id，`key` 是 config key；
  每個 target 只能被一個 setup 接管，`label` 至少要有 `languages` 裡的每一種語言。
- **作者不寫在 meta.json**：由 CI 依 PR 作者判定、寫進 index。

**`revoked.json`**
```json
{ "revoked": [ { "slug": "x", "reason": "malicious", "date": "2026-11-09", "note": "…" } ] }
```
`reason` 是 `malicious`（App 自動停用）或 `broken`（只通知）。
下架＝同一個 PR 裡加進 `revoked.json` **並刪掉** `flows/<slug>/`；`validate --base` 只允許這種刪除，而資料夾留著的話 slug 規則會擋。

**`featured.json`**：`{ "featured": ["slug", …] }`，依序列在最前面；只能列 `flows/` 裡有的。
**`maintainers.json`**：`{ "maintainers": ["ADSFAaron"] }`，作者在這份名單裡的流程，index 標 `official: true`。

**資料夾裡只能有 `meta.json` 與 `flow.json`。** 給人看的說明不放 repo（CI 要 commit 回受保護的 master 就得有 bypass），
改由 `build-index` 同時產生商店網站：`index.html` 與 `flows/<slug>.html`，所有投稿者寫的字都 HTML escape，CSP 不允許任何 script。

**`index.json`**（`build-index` 產生，publish 時簽章）
```json
{
  "index_version": 1,
  "generated_at": "2026-11-09T00:00:00Z",
  "flows": [
    {
      "slug": "…", "version": 3, "author": "…", "official": false, "languages": { … }, "tags": [ … ],
      "min_app_version": "1.8.0", "flavors": ["github", "play"],
      "risks": ["HTTP_REQUEST"], "setup_count": 1,
      "bundle": "bundles/silent-at-night-3.json", "sha256": "…"
    }
  ],
  "revoked": [ … ],
  "featured": [ "slug", … ]
}
```
- `author` 是**第一個把 `flows/<slug>/meta.json` commit 進 master 的 GitHub 帳號**（publish.yml 用 API 查）。
  issue 投稿由 App 開 PR，但 commit 的 author 設成投稿者的 noreply 信箱，所以算投稿者的。
- `bundle` 是 `{ "meta": …, "flow": … }` 合併成一個檔，App 一次下載一個檔、比一次 sha256。
- `min_app_version` 取流程用到的每個步驟與 key 在 catalog 裡 `since` 的最大值；`flavors` 是所有步驟的交集。
- `risks` 列出流程用到的高風險動作：SEND_SMS、CALL_PHONE、HTTP_REQUEST、LAUNCH_SHORTCUT、
  SIMULATE_TAP、SIMULATE_SWIPE、WRITE_FILE、SET_WALLPAPER。

**外部可控的變數（taint 來源）**：`{{trigger.*}}`、HTTP_REQUEST 的 `response_var` 與 `__http_status__`，
以及值引用到上述任一者的 SET_VARIABLE 目標（反覆傳遞到不再變動為止）。這些變數不能出現在 `FieldPolicy.SINKS` 的欄位。

### P2（商店 repo）— [nexflow-flow-store#1](https://github.com/ADSFAaron/nexflow-flow-store/issues/1)

**目錄結構**
```
flows/<slug>/{flow.json, meta.json}          （說明頁由 build-index 產生在 Pages，不放 repo）
revoked.json, featured.json, maintainers.json（決定誰的流程算 official）
keys/{current.pub, next.pub}
README(.zh-TW).md, CONTRIBUTING.md, MODERATION.md, SECURITY.md, MAINTAINING.md, LICENSE(CC0-1.0)
.github/workflows/{validate.yml, report-validate.yml(workflow_run), publish.yml, submission.yml}
.github/actions/flow-store-tools/（建 CLI 的 composite action）, .github/scripts/（讀不可信資料的 Python）
.github/nexflow-tools.ref（publish 用的 NexFlow 固定 SHA）
.github/ISSUE_TEMPLATE/{submit-flow.yml, report-flow.yml, config.yml}, PULL_REQUEST_TEMPLATE.md, CODEOWNERS
```

- **CLI 版本**：validate 與 submission 用 repo 變數 `NEXFLOW_REF`（未設＝`master`）；publish 只用 `.github/nexflow-tools.ref` 裡的 SHA，改它要走 PR。
- **issue 投稿**接受兩種貼上內容：一般 `.flow` 匯出檔（表單填 slug／語言／標題，跑 `from-export`），或 P5 的投稿包 `{meta, flow}`。

- **`validate.yml`**：`pull_request` ＋ 每週排程。checkout NexFlow `master` 跑 CLI，權限唯讀、沒有 secret。結果寫進 job summary 與 annotation。每週排程失敗時（通常是 App 改了 catalog）自動開 issue 並加入 Project。
- **`report-validate.yml`**：`workflow_run` 觸發，在 PR 留言、@ 原作者。前一個 workflow 的產出只當 JSON 讀，絕不執行。
- **`publish.yml`**：固定 SHA 的 CLI `build-index` → `production` Environment 人工核准 → `openssl pkeyutl -rawin` 簽章 → 部署 Pages → smoke test（驗簽章與 sha256）。
- **`submission.yml`**：issue 內文只透過環境變數傳入，偵測到個資就回覆警告。用私人 GitHub App token 開 PR（只有 contents 與 pull-requests 寫入權，**不能有 `workflows` 或 administration 權限**）；token 外洩最多只能開垃圾 PR，Ruleset 擋住合併。
- **保護**：Ruleset 要求 PR、CI 通過、CODEOWNERS 核准，bypass 只給維護者本人；CODEOWNERS 涵蓋 `.github/` 與 `flows/`；帳號用 passkey 或 2FA。
- **金鑰**：Ed25519 私鑰離線備份；App 內建 current 與 next 兩把公鑰。
- **文件**：
  - PR 範本：有沒有實機測過、能在哪個 flavor 跑、有沒有個資、同意 CC0、**擁有這個流程的權利**（不可照抄他牌 App 的範本）。
  - `MODERATION.md`：禁止內容、處理時限、再犯處置、下架 SOP。
  - `SECURITY.md`：private vulnerability reporting；**私鑰外洩 SOP**：改用 next key 簽章、立刻發一版 App、移除外洩公鑰並補上新的 next key；寫明舊版 App 在更新前仍信任外洩的 key。
- **初始內容**：6 個範例流程。

### P3（NexFlow）：瀏覽、安裝、檢舉 — [#20](https://github.com/ADSFAaron/NexFlow/issues/20)

- **Room v6 `store_installs`**：`flow_id`（PK，FK CASCADE）、`slug`、`install_seq`、`version`、`setup_values`、
  `content_fingerprint`（setup 欄位遮蔽後計算）、`previous_flow_json`、`installed_at`、`revoked_handled`。slug 不設唯一。
- **Client**：先把 `HttpClient` 從 `ExecutionModule` 拆到 `NetworkModule`。ETag 快取；index ≤ 1 MB、單一流程 ≤ 256 KB。
  Ed25519 驗證（minSdk 30 沒內建，實作時查文件決定 Tink 或 BouncyCastle）。比對 sha256；`generated_at` 只能往前；
  `index_version` 比 App 新時提示更新 App。
- **UI**：FAB 選單入口；列表（精選、搜尋、tag、已安裝／有更新）；詳情（純文字描述、風險摘要、需要的權限、official 標章、
  setup 表單沿用 `ConfigDialog` 且 secret 遮蔽、檢舉開 GitHub issue 表單並預填 slug 與版本）。
- **安裝**：已裝過先確認「要再裝一份嗎」→ 驗證 → 發新流程 ID → 全域變數加前綴與序號 → setup 值寫成字面值 →
  `forceDisabled` 存檔 → 寫入 `store_installs`。
- **刪除流程**：依前面的決定處理全域變數。
- **secret 遮蔽**：一般匯出、送給 AI、執行紀錄、`error_message`（例如 HTTP 失敗時印出的 URL）；AI 修改流程時 secret 保留原值。
- **其他**：debug 版可覆寫商店網址；`store-compat.yml`；四語字串與截圖。

### P4（NexFlow）：更新、還原、下架 — [#30](https://github.com/ADSFAaron/NexFlow/issues/30)

- **更新（打開商店時檢查）**
  - 沒改過的流程：用 `@Update` 寫回原本那一列（保留執行紀錄），重新套用**流程目前的** setup 值，存下 `previous_flow_json`；
    風險或權限比舊版多時先停用、請使用者確認。
  - 改過的流程：讓使用者選另存新副本或保留。
- **還原**：流程詳情頁「還原成 vN」。
- **下架**：MainActivity 進入前景時檢查（不放 `Application.onCreate`，背景觸發器啟動 process 也會跑到那裡），
  每 6 小時最多一次。惡意：停用＋通知＋橫幅；壞掉：只通知。

### P5（NexFlow）：匯出到商店 — [#35](https://github.com/ADSFAaron/NexFlow/issues/35)

- 精靈：偵測個資欄位並預設改成 setup、token 類標 secret → 填 metadata → 本機先驗證一次。
- 輸出：投稿包複製到剪貼簿並開啟 issue 表單，或存成檔案。

### P6（NexFlow）：AI 助手先搜尋商店再產生流程 — [#38](https://github.com/ADSFAaron/NexFlow/issues/38)

### 發布與文件（P3／P4 發版時）

- 新增使用者文件 `docs/FLOW_STORE.md`；更新 CHANGELOG 與四語 `docs/play-release-notes.md`。
- 隱私權政策：App 會從 GitHub Pages 下載公開檔案，不會傳出使用者資料。Data safety 發版時再確認。
- 服務條款：商店流程由第三方提供，不保證能用，安裝與啟用風險由使用者自行承擔。
- CLAUDE.md 陷阱表：repo 與帳號不可改名、GITHUB_TOKEN 開的 PR 不觸發 workflow、變數名不能有 `.` 或 `-`。

---

## 自動化測試策略

| 位置 | 層 | 內容 |
|---|---|---|
| `flow-store-tools` | JVM | `validate` 每條規則各一組好、壞範例：個資、taint、host、全域變數、slug 重用、REPEAT、版本、語言；另測 `from-export` 與 `build-index` |
| `core/flow-schema` | JVM | 解析 index、meta、revoked；`index_version` 比 App 新 |
| app | Robolectric | catalog golden 檔比對；`FlowConfigValidator`；匯入修正；secret 在匯出、AI、紀錄、錯誤訊息都有遮蔽；AI 修改不覆寫 secret |
| app | Robolectric（`ktor-client-mock`） | ETag 304、簽章錯誤、sha256 不符、replay 舊 index、超過大小上限、離線 |
| app | Robolectric（in-memory Room） | 前綴與重複安裝序號；更新後執行紀錄還在；風險變多就停用；還原；惡意下架；刪除時一併刪變數 |
| app | Robolectric Compose | setup 沒填完不能安裝；secret 遮蔽；`ConfigDialog`（dialog 截圖截不到，在這層驗）；檢舉 intent |
| app | 截圖 | 列表、詳情、下架橫幅、匯出精靈，各 8 種組合 |
| app | Instrumented | Migration 5 → 6；Hilt 換掉 `NetworkModule` 從頭跑到尾 |
| 商店 repo | CI | 每次 PR ＋每週排程跑 validate 與 dry-run；publish 後 smoke test |
| 商店 repo | 手動一次 | 投稿 issue 經 App 開 PR 並觸發 validate；injection 內文不被執行；fork 的 PR 也收得到留言 |
| NexFlow | CI（不擋 PR） | `store-compat.yml` |
| — | 手動 | 模擬器＋debug 網址覆寫，走完安裝、重複安裝、更新、還原、下架 |

---

## 追蹤

| Phase | 父 issue | repo |
|---|---|---|
| P1 匯入修正與共用工具 | [#12](https://github.com/ADSFAaron/NexFlow/issues/12)（sub：#13–#19） | NexFlow |
| P2 商店 repo | [nexflow-flow-store#1](https://github.com/ADSFAaron/nexflow-flow-store/issues/1) | nexflow-flow-store |
| P3 瀏覽、安裝、檢舉 | [#20](https://github.com/ADSFAaron/NexFlow/issues/20) | NexFlow |
| P4 更新、還原、下架 | [#30](https://github.com/ADSFAaron/NexFlow/issues/30) | NexFlow |
| P5 匯出到商店 | [#35](https://github.com/ADSFAaron/NexFlow/issues/35) | NexFlow |
| P6 AI 整合 | [#38](https://github.com/ADSFAaron/NexFlow/issues/38) | NexFlow |

Project：<https://github.com/users/ADSFAaron/projects/5>
