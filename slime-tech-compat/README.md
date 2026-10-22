# Slime Tech Compat 0.4.0-dev

[English](README.en.md)

Slime Tech **0.1.44** / Minecraft **26.1.2** / NeoForge 向けの独立互換パッチです。元MODと一緒に導入します。0.2.0の採掘タグとCasting MoldのJEI表示を維持し、流体の整合性保護と追加説明を加えました。

> **実環境で検証を進めている開発版です。** 実JARを使ったビルド、本物のMixin適用、専用サーバー試験、英語singleplayerでの一部JEI導線を確認しました。NeoForge 26.1.2.94、JEI 29.16.0.46での結果と、次に確認する項目を[検証記録](docs/VERIFICATION.md)にまとめています。導入はバックアップのコピー上で試してください。

## 対象

| 項目 | 対象 |
|---|---|
| 元MOD | `slime_tech-0.1.44.jar` のみ |
| Minecraft | `26.1.2` |
| NeoForge | 検証対象 `26.1.2.94`（元MODの下限は `26.1.2.30-beta`） |
| JEI | クライアント任意依存。APIの基準は `29.16.0.46` |
| 互換パッチ | `slime_tech_compat` / `0.4.0-dev` |
| パッチのライセンス | MIT。元MODの許諾とは別です。 |

解析対象の元JAR SHA-256:

```text
f8ea80558f7f229314fa026311c194aa956f773bc235a76d7598b0a26d3030bb
```

別版や同名の改変JARは対象外です。ビルド時と実行時のMod構築時に元JAR全体のSHA-256を確認します。NeoForgeは26.1.2.94に固定し、版・注入数の不一致も拒否します。

かまどの端数保存で素材未消費の流体が増えるST-022も修正しました。移行・停止・復元は[移行手順](docs/MIGRATION.md)を参照してください。

## 変更点

| 機能 | 初期状態 | 内容 |
|---|---|---|
| 採掘タグ | 有効 | 既存9ブロックへ `mineable/pickaxe` を追加。元の採掘Tierを維持します。 |
| Casting MoldのJEI表示 | 有効 | Condensed Slime 1,000 mB → Gel Ingot 1個。元の製法・量・時間に沿って表示します。 |
| Fluid Tankの混在防止 | 有効 | 連結タンクの異種流体・不正内容を検出すると転送を停止。各タンクの保存内容はそのまま保持します。 |
| Tank探索上限 | 512台 | 上限内の連結群全体を検査します。上限超過や隣接チャンク未ロードでは転送を停止します。 |
| Pumpの流体ID保存 | 有効 | 数字だけでなくレジストリIDを保存。再読込時に現セッションの番号へ解決します。 |
| Pumpの種類保持 | 有効 | 流体源を失ったときの種類消去、および別種流体源発見時のバッファ再ラベル付けを防ぎます。 |
| Pumpの非消費汲み上げ禁止 | **無効** | 設定を有効にすると、水以外のワールド流体源からの汲み上げを拒否します。 |
| Grinderのエネルギー修正 | **無効** | 設定を有効にすると新品0 FE、加工条件120 FE以上。120 FE/加工という既存の完了時コストは維持します。 |
| MixerのJEI表示 | 有効 | 粘土ブロック工程とRadioactive Slime工程。後者はOre Slimesの両材料が存在する場合のみ。 |
| 制約の説明 | 有効 | かまど→バケツ→鋳型の導線、配管の耐熱制約、未実装モード等をJEI情報ページに日英で追加。 |

### Fluid Tank：保存内容を保持して転送を保護

異種流体や不正な内容を検出すると、各ブロックの `localAmount` / `storedFluidId` を保持したまま転送を停止します。停止中は安全に集計できないため、GUIや流体ハンドラの集計値が0になることがあります。ログの `Tank transfers blocked` と接続状態を確認してください。

混在を解消すると転送を再開します。コピーしたワールドで接続解除・内容救出手順を確認してください。**中身が入ったタンクは、設置した状態で対処してください。** 元MODの回収時の流体保持は保証されません。

空タンクを間に挟んでも同じ連結群として判定します。保存IDが不明なタンク、量が負または8,000 mB超のタンクも保留します。検査はロード済みチャンクを対象とするため、チャンク境界に置く場合は隣接チャンクもロード済みにしてください。

### Pump：流体IDを保存し、旧データを移行

元の0.1.44で固定の意味を持つ番号は、水・溶岩・Condensed Slime・Radioactive Slimeの1〜4です。それらは移行できます。一方、**1000以降の動的番号しか保存されていない旧Pumpは、再起動後の番号から元の流体を特定できません**。ここを推測すると別流体に化けるため、本パッチは以下を行います。

- 残量・電力・旧番号・分かる範囲の流体名を保持する。
- 実行時の種類を不明扱いにして、そのPumpの稼働と抽出を止める。
- ログへ `Quarantined Pump`、座標、残量、旧番号、保存済みIDを出す。

**バックアップを確保した後、可能なら検証用コピーを旧環境で起動して他MOD流体を扱うPumpを空にしてください。** 停止から更新までの順序は[移行手順](docs/MIGRATION.md)に従ってください。 水・溶岩・Slime Tech固有の2流体は、この動的番号の問題とは別です。既に流体が誤変換されたデータや、失われた流体名を復元する機能はありません。

追加する保存キーは `SlimeTechCompatSchema`、`SlimeTechCompatFluidId`、`SlimeTechCompatUnresolvedCode` です。レジストリIDが残っていて提供MODだけが欠けている場合は、そのMODを戻して再ロードすることで解決できます。流体名のない旧動的番号には自動救済できません。

## 導入

1. **サーバーを停止し、ワールド・config・modsのバックアップを取ります。** コピーしたワールドを試験用に用意します。
2. `slime-tech-compat-0.1.0.jar` 、`0.2.0.jar` または `0.3.0.jar` を外し、`slime-tech-compat-0.4.0-dev.jar` に置換します。元の `slime_tech-0.1.44.jar` は残します。クライアント・サーバーとも同じパッチを使用してください。
3. コピー上で起動し、ログ・採掘・タンク・Pump再読込・JEIを確認します。[試験手順](docs/VERIFICATION.md#実機で未実施の受け入れ試験)

JEI表示を使うクライアントにはJEIを追加します。専用サーバーはJEIなしで起動・保存・終了を確認済みです。共通パッチとクライアント表示を分離した構成で、詳細は[検証記録](docs/VERIFICATION.md)にあります。

## 設定

初回ロード時に **`config/slime-tech-compat.properties`** を作成します。既存設定を自動で上書きしません。変更後はサーバー・該当クライアントを再起動してください。マルチのゲーム挙動はサーバー側設定が基準です。

```properties
# true: 水以外の流体源からの「元ブロックを消費しない汲み上げ」を禁止
blockNonWaterWorldSources=false

# true: Grinder新品の初期充電を0にし、120 FE未満では加工しない
fixGrinderEnergy=false

# 連結したSlime Tech Fluid Tankの検査上限（1〜4096）
maxTankNetworkMembers=512
```

既存設備の動作を維持して導入できるよう、最初の2項目は**初期値false**です。`blockNonWaterWorldSources=true` にすると、溶岩・MOD流体を含む水以外のワールド流体源からの汲み上げを停止し、水の汲み上げを許可します。流体源を消費せずに汲む元の方式を前提とした制限設定です。

`fixGrinderEnergy=true` は保存済み充電量と既存の完了時コスト120 FE/加工を維持し、新品の初期充電と加工開始条件を調整します。設定名の綴り間違い、不正値、読込不能は説明付きで起動エラーにします。設定例は `examples/slime-tech-compat.properties` にあります。

## JEIでの確認

英語singleplayerでGel IngotのR検索、Casting表示の1,000 mBツールチップ、Ore SlimesなしのMixerのU検索を確認しました。画面確認と初回Gel Ingot到達の試験を続ける際は、[検証記録](docs/VERIFICATION.md)を参照してください。9機械の採掘タグ、適正ツルハシ判定、本体アイテム1個のlootを実サーバーで確認しています。内容コピーの定義は元loot tableにないため、内容入り機械はコピーしたワールドで回収手順を確認してください。

`Gel Ingot` にRでCasting Mold、`Clay` または `Radioactive Slime` にRでMixerの表示を確認します。Mixerの素材を1枠ずつ表示するだけでは分からない制約も、Mixerの情報ページへ記載しました。

粘土工程では中央のSlime Ballと周囲1枠のDustは**それぞれちょうど1個**で、他の枠は空です。Radioactive Slime工程では流体入力が**ちょうど1,000 mB**という元コードの条件があります。Radioactive工程の各アイテム枠は2個以上のスタックでも成立し、各枠1個ずつ消費します。両工程は100 tick、12 FE/t（合計1,200 FE）です。元の加工条件をJEI表示にも反映しています。

Energy ConverterやGeneratorのモードの実装状況は、JEI情報ページで確認できます。今回はその説明を追加しており、機械画面の警告オーバーレイや変換・排水ロジックは追加対象に含みません。

## 機械ごとの確認と運用

機械ごとの対応状況と確認項目は[問題別状況](docs/ISSUES.md)で確認できます。かまどの座標ベース隠し流体バッファの撤去時清掃、Slimy Sandの塊移動、一般Item CapabilityへのItem Tube対応、Energy Cell等の探索方式、炉心の事故モデルは元MODの動作を引き継ぎます。この開発版の検証範囲は、変更した機能とその回帰試験です。

Tankの探索上限は、安全性検査を含めた探索規模を制限します。大きなTank群では元の探索に加えて検査も発生するため、実際の構成で性能を測定してください。

## 開発・再現

### 実依存物を使う標準ビルド

Java 25と同梱Gradle wrapperを使用します。`../mods/slime_tech-0.1.44.jar` と `../mods/jei-26.1.2-neoforge-29.16.0.46.jar` を用意してください。依存JARは配布物へ同梱しません。元JARのSHA-256をビルド時に照合します。

```sh
./gradlew clean build policyTest
./gradlew runServer
./gradlew runClient
```

成果物は `build/libs/slime-tech-compat-0.4.0-dev.jar` です。JEIは `runClient` の実行時クラスパスに追加します。NeoForge版を変更する場合は `gradle.properties` と厳密な版チェックを見直し、適用と動作を再検証してください。

配布用のソースZIPとSHA-256一覧も作る場合は `./gradlew artifactHashes` を実行します。JAR・ソースZIP・`SHA256SUMS` は、他MODと同じ `build/libs/` にまとめて出力します。`cd build/libs && sha256sum -c SHA256SUMS` で照合できます。

参考0.3.0のスタブ・注入モデルを調べる場合は、旧参考版を別の作業場所へ展開して `tools/offline_verify.py` を実行してください。現在の配布判断には、上記の実依存ビルドと[検証記録](docs/VERIFICATION.md)の実Mixin・ゲーム内試験を使用します。

## ロールバック

コピー上で問題が出たら起動を止め、**導入前バックアップのワールド・mods・設定をセットで戻す**のが安全です。特に保留されたPumpを0.2.0や元MODのみの環境で開くと、新しい保存キーを理解しないため、安全性を保てません。JARだけのダウングレードで回復できるとは限りません。

## 出典と境界

ゲーム内の挙動・数値の説明は、ユーザー提供の上記SHA-256のJARを静的解析した結果に基づきます。元MODの実装を説明する資料として参照してください。[対象箇所と判断](docs/IMPLEMENTATION.md)

NeoForgeのメタデータ・Java/Gradle要件は公式資料で確認しています。

- NeoForge 26.1移行資料: https://neoforged.net/news/26.1release/
- `neoforge.mods.toml` とMixin宣言: https://docs.neoforged.net/docs/gettingstarted/modfiles/

配布JARには、この独立パッチのclassとリソースを収録します。元MODのJARはそのまま併用する構成です。MITの適用対象はこの独立パッチのソース・検査ツール・文書です。

