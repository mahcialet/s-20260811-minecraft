# Codex作業指示：Slime Tech Compatを検証可能な互換パッチとして仕上げる

## 0. この依頼で実施すること

あなたは、この作業ディレクトリのSlime Tech Compatを担当する開発エージェントです。
ユーザーが提供する `slime_tech-0.1.44.jar` を対象に、問題の再確認、独立した互換パッチの実装、実依存物でのビルド、実際のNeoForge環境での検証、日英ドキュメントの整備まで進めてください。

既存の `slime-tech-compat-0.3.0-source.zip` を参考実装として提供します。これは完成品ではなく、APIスタブでコンパイルされ、独自の注入モデルで試験された試作です。実際のMinecraft・NeoForge・Sponge Mixin・JEIで成立することから確認してください。既存コードを採用する箇所と書き直す箇所は、対象JARと実APIを根拠に決定します。

最初に、このリポジトリで適用される `AGENTS.md`、既存の開発・テスト・ライセンス方針を読み、その方針に従って作業してください。既存リポジトリが指定されていない場合はローカルの `slime-tech-compat/` を作業場所にします。GitHubへの公開、リポジトリ作成、本番サーバーへの導入は別の承認対象です。

本書と同じディレクトリにある `ISSUES.md`、`ACCEPTANCE_TESTS.md`、`INPUT_MANIFEST.json` を読み、`docs/exec-plans/slime-tech-compat-hardening.md` に実行計画を作成してから実装を開始してください。計画作成だけで終了せず、利用可能な環境で実装・ビルド・試験まで実行してください。

## 1. Goal：目的と成功条件

サバイバルの通常操作で進行でき、設置した機械を適切なツールで回収でき、流体・アイテム・電力の整合性を保つ互換パッチを作ることが目的です。

次の状態を目指します。

1. `Gel Ingot` からJEIのR検索で製造工程と初回入手手順を追える。
2. 対象9機械の採掘タグが補完され、既存のloot tableに沿って回収できる。
3. Fluid TankとPumpで流体の誤変換・消失・保存IDの取り違えが発生しない。
4. 修正の根拠と適用箇所が追跡でき、元MODの版違いを検出できる。
5. Gradle wrapperと実依存物を使ったビルド、専用サーバー起動、実Mixin適用、可能な範囲のゲーム内試験を再現できる。
6. 検証済み／未検証を分けた成果物、移行・復旧手順、日英文書を残す。

元MODの全機能を作り直す依頼ではありません。まず採掘・導線・データ保全を確立し、その後にリスクの大きい未修正項目へ進めます。

## 2. Inputs：対象環境と提供物

### 固定対象

| 項目 | 対象・扱い |
|---|---|
| Minecraft | `26.1.2` |
| 元MOD | `slime_tech-0.1.44.jar`、397,500 bytes |
| 元MODのSHA-256 | `f8ea80558f7f229314fa026311c194aa956f773bc235a76d7598b0a26d3030bb` |
| 元MODのmod ID | `slime_tech` |
| 元MODのJavaパッケージ | `dev.wizardjw.slimetech` |
| NeoForge | 元MODメタデータの下限は `26.1.2.30-beta`。ユーザーの実インストール版は起動ログ等で取得して固定する |
| JEI | ユーザー導入版は `jei-26.1.2-neoforge-29.16.0.46.jar` |
| パッチのmod ID | `slime_tech_compat` を維持 |
| パッチのJavaパッケージ | 参考実装は `dev.mahcialet.slimetechcompat` |
| Java/Gradle | 参考実装はJava 25、Gradle 9.1以上を前提としている。実際のNeoForge開発要件を確認して固定する |

`neoforge-1.0.0.jar` というファイル名だけからNeoForge本体の実バージョンを決めないでください。ログ・ランチャー設定・実依存物で特定します。

### 一緒に動かす主要MOD

ユーザーの提示した構成から、関連するものを抜粋しています。これは今回すべてのJARを同梱したという意味ではありません。

- `oreslimes-1.0.108.jar`：Mixerの素材連携を確認する対象。
- `refinedstorage-neoforge-3.2.1.jar`：Importer / Exporter / External Storageとの流体連携。
- `refinedstorage-jei-integration-neoforge-2.0.1.jar`。
- `ftb-industrial-contraptions-26.1.2.9.jar`：Geothermal Generator等との連携。
- `ltxi-26.1.2-1.1.3.jar`、`limacore-26.1.2-2.4.0.jar`：蓄電・流体設備との連携。
- `arcane_slime_tech-0.1.2.jar`、`slime_dimensions-0.1.18.jar`、`slimequests-1.0.55.jar`：存在する場合の追加確認対象。

最小構成での成功を先に確立し、その後に上記連携を追加してください。クエストの導線は `slimequests-1.0.55.jar` 未確認なので、入手できたときに独立して確認します。

### 参考実装

`reference/slime-tech-compat-0.3.0-source.zip` を展開し、次を読んでください。

- `README.md` / `README.en.md`
- `docs/IMPLEMENTATION.md`
- `docs/VERIFICATION.md` / `docs/verification.json`
- `docs/evidence/` 以下の既存ログ
- `build.gradle`、`gradle.properties`、`settings.gradle`
- `src/main/java/dev/mahcialet/slimetechcompat/` 以下
- `tools/offline_verify.py`、`tools/OfflineWeaver.java`、`tools/abi_stubs.py`

元MODとJEIのJARはローカルの `libs/` 等に配置し、バージョン管理・配布物から分離します。対象JARがない場合は未入手と記録し、そのJARに依存しない準備・試験を先に進めます。

## 3. Evidence：事実と検証段階の区別

`ISSUES.md` は、次の根拠を区別しています。

- **U**：ユーザーが実ゲームで報告した現象。
- **S**：対象ハッシュの元JAR、または参考パッチのソースで今回確認した構造・処理。
- **M**：0.3.0に同梱されたモデル試験の記録。実Minecraftでの再現とは別。
- **H**：以前の会話での調査報告。今回の引継ぎ整理では当該処理を再確認していない。
- **I**：静的な処理から導いた追加仮説。再現試験で判断する。

確定度は問題ごとに更新してください。元JARの静的な事実、再現された障害、設計上の選択を別々に記録します。「作者の意図」「仕様として間違い」については、コードだけから推定して断定する必要はありません。

既存の検証記録には、ポリシー33件、注入対象20箇所、元バイトコードの部分実行25件×設定ON/OFF、JEIモデル18件等が記載されています。これらは独自fixture・注入モデルによる検査です。実APIでのビルド、実Mixin適用、実機試験の結果には数えません。

## 4. 現時点の問題一覧

詳細な根拠・再現手順・受け入れ条件は `ISSUES.md` を参照してください。

| ID | 優先度 | 問題・挙動 | 参考0.3.0の状態 |
|---|---|---|---|
| BUILD-001 | P0 | 実依存ビルド・実Mixin・起動未検証。Gradle wrapperも未同梱 | 最初に解消する開発基盤課題 |
| ST-001 | P1 | 9機械に採掘用pickaxeタグがない。Casting Moldが鉄ピッケルで回収困難 | タグ補完あり・実機未検証 |
| ST-002 | P1 | Gel Ingotのコード内鋳造処理をJEIから追えない | Casting Mold表示あり・実画面未検証 |
| ST-003 | P1 | 初回Gel Ingot、かまどの隠し流体、バケツ、耐熱配管の導線不足 | JEI説明あり・初回手順の実証が必要 |
| ST-004 | P0 | 連結Fluid Tankが異種流体の量を合算し、誤変換し得る | 混在時停止の候補実装あり |
| ST-005 | P0 | Pumpが動的な流体番号だけを保存し、再起動後に種類を取り違え得る | レジストリID保存・旧不明データ保留あり |
| ST-006 | P0 | Pumpが流体源消失・変更時にバッファの種類を消去／上書きする | 種類保持の候補実装あり |
| ST-007 | P2・任意 | Pumpが流体源を消費せず汲み上げる | 非水源禁止設定あり・初期値OFF |
| ST-008 | P1/P2・任意 | Grinderの開始条件15 FEと完了コスト120 FEが不整合。新品2,000 FE | 条件120 FE・新品0 FEの設定あり・初期値OFF |
| ST-009 | P1 | かまどの隠し流体が座標に保存され、撤去後の残存が疑われる | 未修正 |
| ST-010 | P1 | Slimy Sandの塊移動が非衝突ブロックを直接上書きし得る | 未修正 |
| ST-011 | P1 | Mixerの一部工程がOre Slimes素材に依存するが元MODの依存宣言がない | JEIレシピだけ存在時登録 |
| ST-012 | P1・導線 | Mixerの「アイテムちょうど1個」「流体ちょうど1,000 mB」等の厳密条件 | 説明・表示のみ、条件は維持 |
| ST-013 | P2 | Energy Converterの変換モードがplaceholder | 説明のみ |
| ST-014 | P2 | GeneratorのDrainModeが保存・表示される一方、処理に反映されない | 説明のみ |
| ST-015 | P2 | Tank / Energy Cell等の探索コスト、パッチ側検査の追加コスト | Tankの上限・未ロード停止のみ |
| ST-016 | P2 | Item Tubeの接続先判定がContainer中心で汎用Item Capability対応が限定的 | 未修正 |
| ST-017 | P3・仕様確認 | Bouncer Grinderの実動作・人力加工条件が分かりにくい | 過去報告を要再確認 |
| ST-018 | P3・仕様確認 | Radioactive Slimeの名称と効果の受け取り方に差がある | 過去報告を要再確認 |
| ST-019 | P3・仕様確認 | ReactorのHeat表示に対応する事故モデルの有無 | 過去報告を要再確認 |
| ST-020 | P3・仕様確認 | Gel HookのSneak減速・落下制御操作が分かりにくい | 未対応、まず説明対象 |
| ST-021 | P3・運用確認 | 開発用コマンドの同梱と権限境界 | 権限付き。存在だけで脆弱性扱いしない |
| ST-022 | P0調査 | かまどの端数mB変換で素材未消費の流体増加が起こる可能性 | 今回の追加仮説・未修正 |
| ST-023 | P2調査 | かまど互換処理が毎server tickに各Levelのチャンク・BEを走査 | 今回の静的確認。実負荷未測定 |
| PATCH-001 | P0/P1 | 暫定パッチ自身の採用条件、停止時表示、設定説明、移行・同期 | 独立してレビュー・回帰試験する |

## 5. Decisions：維持する設計方針

### 独立パッチと責任範囲

元MODはそのまま外部依存として使います。パッチは独立した採掘タグ、JEIプラグイン、必要最小限のMixin、安全性検査、独立文書で構成してください。

配布物には独立作成したコード・データ・文書を収録します。元Slime Techのclass・画像・音声・langの複製は配布JAR・ソース公開物から除外します。依存ライブラリや生成済み検証スタブclassは配布JARに含めません。独立作成した検証スクリプトを残す場合はtools/test用と明示します。解析用の逆アセンブル結果はローカル作業物とし、公開する根拠は対象ハッシュ、クラス・メソッド名、観測事実、独立した最小再現にまとめます。

パッチ自身のMITライセンスと既存attributionを維持します。元MODの権利・利用条件とパッチのライセンスは別々に記載します。

### 互換性とゲームバランス

- データの誤変換・消失を防ぐ保護は、通常の同種転送を保ったうえで既定有効にします。
- Pumpの非消費汲み上げ制限、Grinderの初期充電など、既存設備の運用を変えるポリシーは明示的な設定にします。既存0.3.0の初期値を引き継ぎます。
- JEI補完は表示用にします。サーバーの実レシピ、元の数量、処理時間、消費量は表示対象として読み取り、ゲームバランス変更は別の判断として記録します。
- Energy ConverterやReactor等の未実装とされる機能は、まず実態を説明します。新しい変換システム、放射線、メルトダウン等の追加は本依頼の安定化スコープに含めません。

### 既存ワールドの保存・復旧

流体の種類と量、アイテム数、既存の保存済み電力の保全を優先します。種類が失われた旧データは、推測変換する代わりに「保留」として保存し、座標・理由・量・旧コードを確認できるようにしてください。

0.3.0が追加した以下のキーを読み込む互換性を維持し、保存形式を変更する場合は明示的なスキーマ移行を実装します。

```text
SlimeTechCompatSchema
SlimeTechCompatFluidId
SlimeTechCompatUnresolvedCode
```

採掘タグ修正による本体回収と、内容入り機械のNBT・電力・流体保持は別の検証対象です。実際の元MODの回収仕様を確認したうえで、保持する内容と失われる内容を文書化してください。

## 6. Attempted：既にある実装

0.1.0は9機械の採掘タグ、0.2.0はCasting MoldのJEI表示を追加した試作です。0.3.0はそれらにFluid Tankの混在停止、PumpのID保存・種類保持、任意のPump/Grinder設定、MixerのJEI表示と説明を加えています。

参考0.3.0の主なファイル：

```text
src/main/java/dev/mahcialet/slimetechcompat/
  CompatConfig.java
  SlimeTechCompat.java
  mixin/FluidTankMixin.java
  mixin/PumpMixin.java
  mixin/GrinderMixin.java
  safety/TankAccess.java
  safety/TankPolicy.java
  safety/TankSafety.java
  safety/PumpIdentityPolicy.java
  safety/PumpSafety.java
  jei/SlimeTechCompatJeiPlugin.java
  jei/CastingMoldRecipeCategory.java
  jei/MixerRecipeCategory.java
src/main/resources/
  META-INF/neoforge.mods.toml
  slime_tech_compat.mixins.json
  data/minecraft/tags/block/mineable/pickaxe.json
  assets/slime_tech_compat/lang/{ja_jp,en_us}.json
```

設定ファイルは `config/slime-tech-compat.properties` で、既存の初期値は次のとおりです。

```properties
blockNonWaterWorldSources=false
fixGrinderEnergy=false
maxTankNetworkMembers=512
```

`blockNonWaterWorldSources=true` は**水以外すべて（溶岩を含む）**のワールド流体源からの汲み上げを拒否する設定です。流体源を消費して回収する実装ではありません。参考版の日本語説明には「溶岩・水以外」と読める曖昧な箇所があるので、コードと一致する文言へ揃えてください。

## 7. Next：実行順序

### Phase 0：現状把握と実ビルド基盤

1. 提供ファイルのSHA-256とmodメタデータを照合し、参照ソースを展開する。
2. `ISSUES.md` の各項目に「再確認済み／未確認／再現済み／仕様として維持」を記録する。
3. 対象版の公式NeoForge・JEI・Mixin資料と実JARでAPIを確認する。別Minecraft版のdefault branchからの推測を実装根拠にしない。
4. Gradle wrapper、実際のJDK・ModDevGradle・NeoForge・JEIを固定する。依存物取得・ローカル配置方法をREADMEとCIに明記する。
5. 実依存物でコンパイルし、実際のMixinによる変換を実行する。コンストラクタへのModifyConstant、Accessor、static handler、ordinal指定、対象ディスクリプタ、注入箇所数、メタデータを個別確認する。
6. 専用サーバーはJEIなしで起動確認する。参考build.gradleはJEIを共通runtimeOnlyに置くため、runServer側へ入り込まない構成・試験を用意する。

この段階で参考ソースと実APIが食い違った箇所は修正してください。スタブ側を都合よく合わせただけで解決扱いにはしません。

### Phase 1：必須の修正・保護を実証

採掘タグ・Casting Mold JEI・初回導線を維持し、ST-004〜006を優先して完成させます。ST-022も同時に最小再現を行い、資源増殖を再現できた場合はP0として修正してください。

各問題について、無修正の元MODで現象が出るテストと、パッチ適用で意図した性質を満たすテストを組にします。同種流体、空タンク、通常Pump、設定OFF、JEIなしなどの正常系も並行して残します。

### Phase 2：任意設定・移行・運用性

Pump/Grinderの任意設定を既存初期値で維持し、両設定のON/OFFを試験します。旧データ保留、欠損MOD、登録順変更、2回以上の保存再読込、0.3.0からの移行を検証してください。

「停止しているタンクが0表示」の問題は、残量保全と利用者への説明を両立する表示・診断方法を検討してください。保護を解除するために中身入りタンクを壊すことだけが手順になる状態を避け、読み取り専用診断と安全な復旧手順を用意します。

### Phase 3：未修正項目を小さく処理

ST-009、010、011、012、015、016、023を再確認し、破壊・消失・増殖に関わるものから、独立したコミットと試験で修正します。安全な変更境界が決まらない項目は、影響・再現条件・検討済み案をissueとして残し、完成済み項目の検証を進めてください。

ST-017〜021は挙動の記録と説明を基本にします。名前から推定した機能を追加するのではなく、ユーザーが実際の操作と制約を理解できるようにします。

### Phase 4：成果物と受け入れ

`ACCEPTANCE_TESTS.md` に沿って実施結果を記録し、実依存ビルドのJARとソースを出力してください。試作0.3.0と判別できるバージョン番号を使い、検証が終わるまではdev/pre-releaseとして扱います。

実行できなかった試験があれば、未実施の理由と再現コマンドを残します。ヘッドレス環境でJEI画面を確認できないことと、ビルドや専用サーバーまで未検証であることは分けて報告してください。

## 8. Constraints：実装の判断基準

- 対象は指定ハッシュのSlime Tech 0.1.44。対応版の追加は別変更にする。
- 元のID、ブロック、アイテム、既存JEIカテゴリIDをできるだけ維持する。
- Mixinは小さく、対象数と対象メソッドを明示する。注入不一致は対象版・APIの再確認につなげる。
- 流体のsimulation/transaction rollbackで保存内容やワールドが変化しないことを試験する。旧boolean simulateとNeoForge実APIの橋渡しも確認する。
- 停止・保留を通常の「空」と区別して記録する。診断ログは連続出力を抑え、状態変化と対象座標を追跡できるようにする。
- 安全性検査が未ロードチャンクを読み込まないこと、負荷が許容できることを計測する。キャッシュを導入する場合は、同tickの内容変更・接続変更・チャンク移動で失効する条件を試験する。
- 設定の実効値はサーバー側を基準にする。クライアント表示が設定状態を示すなら同期を実装し、同期しない説明は条件付きの一般説明にする。
- 既存設定を保全し、不正値や未知キーの扱いは現行仕様から変更した場合に記録する。
- 本番ワールドへの導入とバックアップ復元はユーザーの運用作業とし、開発用コピーで検証する。

## 9. Deliverables：残す成果物

- 実依存物でビルドできる独立MODのソース、Gradle wrapper、固定した依存設定。
- ビルドJAR、ソースZIP、SHA-256一覧。スタブ版と正式依存ビルドを区別したmanifest。
- `README.md`（日本語）と `README.en.md`（英語）。対象版、導入、設定、回収、既知制限を含める。
- `docs/ISSUES.md`：本書のIDを維持し、根拠・再現・状態・対応コミットを記録。
- `docs/VERIFICATION.md`：実APIビルド／実Mixin／専用サーバー／クライアント／モデル試験を分けた結果。
- `docs/MIGRATION.md` と英語版：旧Pump、混在Tank、0.3.0由来データ、ロールバックの扱い。
- `CHANGELOG.md`、採用した設計判断のADR、実行計画、継続用 `CHATGPT_HANDOFF.md`。
- 自動テストとCI。元MODをCIで用意できないときは理由付きskipを明示し、統合試験済みと誤認させない。

最終報告には、変更した問題ID、実行したコマンド、検証の段階、成果物パス、未解決事項、ユーザー側で必要な移行作業を含めてください。

## 10. Unresolved：特に残っている判断

- 参考0.3.0が実際のMixin・NeoForge環境でロードできるか。
- 内容入り機械の回収・再設置で何を保持するかという元MODの現状。
- かまどの隠し流体の撤去時の扱い。消去・アイテム保持・別保存への移行を、資源保全と元の回収仕様を踏まえて決定する必要がある。
- ST-022の端数変換が実機で再現するか。
- Tank安全検査の実負荷と、チャンク境界での保守的停止が許容できるか。
- Slime QuestsのQuest Bookに導線があるか。現時点では未確認。
- 過去の「不審な外部通信等は見当たらない」という説明は、包括的なセキュリティ保証ではない。本依頼はゲーム内整合性と互換性が主対象。

## 参照の優先順位

元JARと対象版の実API → 実機再現 → 本書に明示した静的観測 → 参考実装のソース・モデル試験記録 → 過去会話の説明、の順に判断してください。食い違いは差分として記録し、確認できた根拠に沿って問題台帳と説明を更新します。
