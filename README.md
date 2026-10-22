# しらべてまとめる君 — Backend

日本語の質問から、回答・出典・Markdown / 安全化した HTML レポートを生成し、
ナレッジベースに保存・検索する Java 25 / Spring Boot 4.1.2 API です。

**デプロイ、Azure リソース作成、実環境への接続検証は行っていません。**
ローカルモードはそのまま実行できます。Foundry モードは公式 Java SDK を使用する実装ですが、
実際のエンドツーエンド検証には利用者の Azure プロジェクト、モデルデプロイ、マネージド ID が必要です。

## ローカルで実行

必要環境: **JDK 25、Maven 3.9.11 以降**。

```sh
java -version
mvn -B -ntp verify
mvn spring-boot:run
```

または:

```sh
mvn -B -ntp package
java -jar target/shirabete-matomeru-kun-backend-0.0.1-SNAPSHOT.jar
```

既定の URL は `http://localhost:8080`、保存先は起動ディレクトリの `./data/reports`。
API の応答前に保存し、再起動しても同じディレクトリを指定すればレポートを取得できます。
`.env.example` は設定例です。Spring Boot は `.env` を自動読込しません。
環境変数として設定するか、`--app.storage.directory=...` のような起動引数を使用してください。

```sh
curl http://localhost:8080/api/health
curl -sS http://localhost:8080/api/questions \
  -H 'Content-Type: application/json' \
  -d '{"question":"AzureのマネージドIDとは何ですか？"}'
curl -G http://localhost:8080/api/reports --data-urlencode 'q=マネージドID'
# 上の応答の report.id を使用:
curl http://localhost:8080/api/reports/00000000-0000-0000-0000-000000000000
```

### ローカルモードの意味

既定の `RESEARCH_MODE=local` は **開発用デモ** です。
外部検索、LLM 推論、Foundry 呼出しはしません。質問に応じた調査の進め方を日本語で返し、
Spring Boot / Microsoft Foundry の公式文書を **「参考例（質問の根拠ではありません）」** として添付します。
回答にもレポートにも「ローカルデモ」「実際の調査結果ではない」と表示します。
任意の質問に対する事実の調査が完了したと誤認しないでください。

## API 契約

日付は UTC の ISO 8601 文字列、ID は UUID 文字列。成功応答はすべて HTTP 200 です。
ルートはフロントエンド向けに以下で固定しています。

### `GET /api/health`

```json
{"status":"UP"}
```

プロセスの liveness です。外部サービス、ディスクの空き容量、マネージド ID の認証可否を
確認する readiness ではありません。

### `POST /api/questions`

`Content-Type: application/json`

```json
{"question":"AzureのマネージドIDとは？"}
```

`question` は必須の非空文字列、最大 2000 UTF-16 コード単位です。
前後の空白は除去します。数値・真偽値から文字列への暗黙変換、未知の JSON フィールドは拒否します。

応答の構造（内容はモードにより異なります）:

```json
{
  "answer": "日本語の回答 [1]",
  "sources": [{"title": "文書のタイトル", "url": "https://learn.microsoft.com/..."}],
  "report": {
    "id": "b1103cbb-b124-4aa6-ac5f-66cf567ce49a",
    "title": "AzureのマネージドIDとは？",
    "markdown": "# AzureのマネージドIDとは？\n\n...",
    "html": "<h1>AzureのマネージドIDとは？</h1>\n...",
    "createdAt": "2026-10-22T00:00:00Z"
  }
}
```

`answer` はプレーンテキストです。`report.markdown` に質問・回答・番号付き出典リンクが入り、
`report.html` はサーバーで安全化された HTML フラグメントです。
タイトルは質問から生成し、80 Unicode コードポイントを超える場合は省略記号を付けます。
同じ質問でも呼出しごとに新しいレポートを作成します（冪等 API ではありません）。

### `GET /api/reports?q=...`

```json
[
  {
    "id": "b1103cbb-b124-4aa6-ac5f-66cf567ce49a",
    "title": "AzureのマネージドIDとは？",
    "summary": "HTMLタグを含まない本文の概要",
    "createdAt": "2026-10-22T00:00:00Z"
  }
]
```

`q` は省略可、最大 **200 UTF-16 コード単位**。
空または空白だけなら全件、新しい順。ローカル検索はタイトルと Markdown 本文に対する、
英字の大小を区別しない部分一致です。空白区切りの複数語は AND 条件です。
概要はプレーンテキストの先頭 200 Unicode コードポイント（超過時は `…`）。
結果なしは `[]`。この初期版ではページング・日本語形態素解析・ベクトル検索はありません。

### `GET /api/reports/{id}`

上記 `report` オブジェクトそのものを返します（ラッパーなし）。
UUID として不正な ID は 400、正しい形式でも存在しなければ 404。

### エラー

`application/problem+json`（RFC 9457）の `status` / `title` / `detail` 等を返します。

| HTTP | 条件 |
| --- | --- |
| 400 | 未指定・空・長すぎる質問、不正 JSON、型違い、長すぎる検索語、不正 UUID |
| 404 | レポートやルートが存在しない |
| 405 / 415 | 不正なメソッド / Content-Type |
| 502 | Foundry、MCP、認証、応答形式、出典検証の失敗 |
| 503 | 保存・読込・検索の失敗（破損 JSON を含む） |
| 500 | その他の想定外エラー |

例:

```json
{"type":"about:blank","title":"Not Found","status":404,"detail":"レポートが見つかりません。","instance":"/api/reports/..."}
```

パス・資格情報・外部サービスの生のエラーはレスポンスに含めません。運用ログはアクセス制限してください。

## 設定

| 環境変数 | 既定値 / 意味 |
| --- | --- |
| `PORT` | `8080` |
| `RESEARCH_MODE` | `local` / `foundry` |
| `REPORT_STORAGE_DIRECTORY` | `./data/reports` |
| `STORAGE_MODE` | `local`（`azure-blob` は予約・未実装） |
| `SEARCH_MODE` | `local`（`azure-ai-search` は予約・未実装） |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000`（カンマ区切り） |
| `FOUNDRY_PROJECT_ENDPOINT` | Foundry モードで必須。ポータルで確認したプロジェクトの HTTPS エンドポイント |
| `FOUNDRY_MODEL_DEPLOYMENT` | Foundry モードで必須。利用可能なモデルの**デプロイ名** |
| `FOUNDRY_AGENT_NAME_PREFIX` | Foundry モードで必須。英小文字開始、英小文字・数字・ハイフン、最大31文字 |
| `LEARN_MCP_URL` | `https://learn.microsoft.com/api/mcp`（検証済みのこの URL のみ許可） |
| `AZURE_CLIENT_ID` | ユーザー割当マネージド ID のクライアント ID。空ならシステム割当 ID |
| `FOUNDRY_TIMEOUT_SECONDS` | HTTP 呼出しのタイムアウト `90`、10〜180 秒 |

CORS は `/api/**` の許可済み origin の GET / POST / OPTIONS、
Content-Type / Accept のみ。Cookie 等の credentials は許可しません。
CORS は認証・アクセス制御の代わりではありません。

## 実際の Foundry Agent + Microsoft Learn MCP

公式 SDK **`com.azure:azure-ai-agents:2.8.0`**、
**`azure-identity:1.18.7`** を使用しています。手製の REST エンドポイントや推測した API 形式は使用しません。
認証は `ManagedIdentityCredentialBuilder` に限定しており、
環境変数のシークレット、API キー、開発者の Azure CLI ログインへの自動フォールバックはありません。
`DefaultAzureCredentialBuilder` を使う公式サンプルとはこの点が異なります。

### Azure 側の前提

1. Foundry プロジェクトと、Responses / MCP 対応のモデルデプロイを用意する。
2. 実行基盤でシステム割当またはユーザー割当マネージド ID を有効化する。
3. その ID に対象プロジェクトでエージェント作成・呼出し・削除とモデル利用を行える
   RBAC（例: 対象スコープの Azure AI User。必要な権限は利用環境の最新仕様で確認）を割り当てる。
4. プロジェクトエンドポイント、デプロイ名、名前プレフィックスを設定する。
   Foundry から Learn MCP への通信も許可する。

設定例（プレースホルダーは実際の値に置換）:

```sh
export RESEARCH_MODE=foundry
export FOUNDRY_PROJECT_ENDPOINT='https://YOUR-RESOURCE.services.ai.azure.com/api/projects/YOUR-PROJECT'
export FOUNDRY_MODEL_DEPLOYMENT='YOUR-MODEL-DEPLOYMENT'
export FOUNDRY_AGENT_NAME_PREFIX='shirabete'
# ユーザー割当 ID の場合だけ設定:
# export AZURE_CLIENT_ID='YOUR-MANAGED-IDENTITY-CLIENT-ID'
mvn spring-boot:run
```

PC などマネージド ID のない環境はローカルモードを使用してください。
必須設定が欠ける場合、未確認の MCP サーバー・ツールを指定した場合は**起動に失敗**します。
認証・RBAC・モデル名・外部通信の可否は最初の質問処理時に判明し、失敗は 502 です。
local への黙示的フォールバックはしません。

### 実行と承認の流れ

- `AgentsClientBuilder` → `AgentsClient.createAgentVersion` で
  `PromptAgentDefinition` + `McpTool` を持つエージェントを質問単位で作成します。
  名前は設定したプレフィックス + ランダム ID。同時リクエストで latest version が混線しません。
- エージェントスコープの `buildAgentScopedOpenAIClient` から Responses を呼びます。
  HTTP 通信は JDK transport、接続10秒、設定タイムアウト、アプリの自動再試行は無効です。
- MCP の URL は公式文書で確認済みの `https://learn.microsoft.com/api/mcp` に固定検証します。
  読み取り専用の `microsoft_docs_search` / `microsoft_docs_fetch` /
  `microsoft_code_sample_search` のみ許可します。
  `app.foundry.allowed-tools` でこの集合の**部分集合**に絞れますが、任意ツールの追加は起動エラーです。
- 許可ツールすべてに承認を要求する構造化 `McpToolRequireApproval` を設定し、
  **server label、ツール名、引数を検査したものだけ**承認します。
  fetch は HTTPS の `learn.microsoft.com` 文書 URL のみ。未知のサーバー・ツール・不正引数は失敗させ、
  無条件 `approve(true)` ループや全ツール `never` 承認は使いません。
  MCP の発見・プロトコル処理は Foundry の公式 MCP tool に委譲しています。
- 最大4承認ラウンド、各 Responses 呼出しの tool call 上限4、出力上限6000トークン。
  これはリクエスト全体の時間・料金上限ではありません。基盤側でも制限してください。
- 回答 JSON を型付き `ResearchResult` として解析し、
  すべての出典 URL が**成功した Learn MCP 出力にも存在するか**を確認します。
  未取得 URL、出典なし、不正な応答、未完了応答、MCP エラーは 502 とし、レポートを保存しません。
  URL 検証は主張の正しさまで保証しないので、利用者による内容確認は必要です。
- 処理後はエージェントを削除します。削除に失敗しても成功扱いせず 502 にします。
  プロセス強制終了や通信障害で孤立エージェントが残る可能性があるため、
  運用ではプレフィックスによる棚卸し・削除を用意してください。
  Responses のサーバー側保存・保持期間は Azure 側のデータポリシーにも従います。

この provider の対象は **Microsoft の技術文書** です。一般の Web 全体の調査ではありません。
質問文は Azure / モデルに送信され、検索語は Learn MCP に送信されます。機密情報の入力は禁止・制限してください。

実装確認に使用した一次資料:

- [公式 Java MCP サンプル](https://raw.githubusercontent.com/Azure/azure-sdk-for-java/main/sdk/ai/azure-ai-agents/src/samples/java/com/azure/ai/agents/tools/McpSync.java)
- [公式 Java SDK README](https://raw.githubusercontent.com/Azure/azure-sdk-for-java/main/sdk/ai/azure-ai-agents/README.md)
- [Microsoft Learn MCP の公式 URL・ツール説明](https://raw.githubusercontent.com/MicrosoftDocs/mcp/main/README.md)
- SDK 2.8.0 の公開 Maven アーティファクトのソース・API を確認し、実際に依存解決・コンパイルしています。
  SDK アップグレードや MCP ツール変更時は、承認ポリシーと応答解析を再検証してください。

## アーキテクチャ / 永続化

```text
ApiController → QuestionService → ResearchProvider
                                ├─ LocalResearchProvider（開発用）
                                └─ FoundryResearchProvider
                                   → FoundryAgentSessionFactory（公式SDKアダプター）
              → ReportRenderer（CommonMark + jsoup）
              → ReportStore / ReportSearch
                 └─ LocalFileReportRepository
```

- Controller は API 契約・入力検証、Service は調査→変換→保存を担当します。
- リポジトリ、検索、調査プロバイダー、Foundry セッションに独立したインターフェースがあります。
- ローカル保存は UUID ごとの JSON。保存先内の staging ファイルに書込み・force 後、
  同じファイルシステムで atomic rename します。atomic move 非対応の場合は 503（安全でない代替保存はしない）。
  JVM 内は排他制御し、検索に staging ファイルは含めません。破損した正式ファイルは黙って隠さず 503 にします。
- HTML は CommonMark の raw HTML 無効化・危険 URL 無効化に加え jsoup の許可リストで安全化します。
  質問・モデル回答は Markdown の命令ではなくテキストとしてエスケープします。
  保存済み HTML も信用せず、読込時に Markdown から再生成します。画像や script / iframe は出力しません。
- 保存先は信頼された管理領域にしてください。外部利用者が JSON やシンボリックリンクを書き込める共有領域は使わないでください。
- 初期版のローカル検索は全ファイル走査です。単一インスタンス・小規模開発用であり、
  分散排他・OS/ディスク障害時の完全な耐久性・暗号化・バックアップを提供するものではありません。

### Azure Blob / Azure AI Search への拡張（未実装）

Foundry による調査は実装済みですが、**Blob と AI Search の保存・検索アダプターは未実装**です。
`STORAGE_MODE=azure-blob` または `SEARCH_MODE=azure-ai-search` を設定すると、
実装がないことを示して起動に失敗します。設定だけでクラウド永続化されることはありません。

実装時は:

1. `ReportStore` を Blob 実装に差し替え、`reports/{UUID}.json` の読み書きを実装する。
   マネージド ID、専用コンテナー、ETag / 再試行ポリシーを使用する。
2. `ReportSearch` を Azure AI Search 実装に差し替え、`id` / `title` / `summary` /
   `markdown` / `createdAt` をインデックス化する。日本語 analyzer やページングも設計する。
3. Blob 保存とインデックス更新は分散トランザクションではないため、`ReportStore` の decorator 等で
   outbox / 再投入・再構築を用意し、更新漏れ・二重登録と検索の結果整合性を扱う。
4. `ApplicationConfig.cloudAdapterGuard` を置き換え、conditional bean と cloud 設定の必須検証を追加する。
   Blob / Search の URL、インデックス名、コンテナー名、ID 権限はアダプター実装時に設定項目として追加する。

## Docker

```sh
docker build -t shirabete-backend .
docker volume create shirabete-reports
docker run --rm -p 8080:8080 \
  -v shirabete-reports:/app/data/reports \
  -e CORS_ALLOWED_ORIGINS=http://localhost:5173 \
  shirabete-backend
```

Java 25 のマルチステージビルドでテストを実行し、実行時は非 root（UID 10001）。
永続ボリュームを指定しない場合、コンテナー削除でデータは失われます。
bind mount 使用時は UID 10001 が書き込める権限を設定してください。
コンテナーのヘルスチェック / オーケストレーターの liveness probe には `GET /api/health` を使用してください。
Foundry モードはマネージド ID を提供する実行基盤と上記環境変数が別途必要です。

## テストと運用上の注意

```sh
mvn -B -ntp verify
```

テストは外部認証不要で、API の全ルート、入力検証、CORS、エラー変換、
再生成したリポジトリからの永続データ取得、検索・順序・並行保存、
Markdown 変換と危険 HTML、Foundry 選択 / 設定不足 / 承認制限 / 未検証出典 / provider 障害を検証します。
テストデータは `target/test-data` 内に作成し、終了時に削除します。
Foundry のユニットテストは SDK の型を使用したセッション代替によるもので、Azure のライブ接続試験ではありません。

認証、認可、ユーザーごとのデータ分離、レート制限、要求全体のタイムアウト、監査、
TLS 終端、大容量 JSON の ingress 制限はこの初期版にはありません。
認証なしでインターネットに公開しないでください。利用者のレポート閲覧権限と
Foundry 呼出し費用を制御した上で運用してください。
**この作業でデプロイ・リソース作成・コミット・push は行っていません。**