# Ore Slimes Compat

Minecraft **26.1.2 / NeoForge 26.1.2.71 / Java 25** 用のサーバー側互換 Mod です。
**`oreslimes-1.0.108.jar` 専用**のパッチを、独立した jar として提供します。
Ore Slimes 本体 jar は元のまま別途導入して使います。
Slime Tech / Arcane Slime Tech / Slime Dimensions / Slimequests はパッチ対象外です。

Ore Slime は Rabbit / Cow / Sheep / Pig と同じ `CREATURE` カテゴリに属します。
Ore Slimes の独自スポーナー（world spawner）は、標準の個体数上限（mob cap）を確認せず直接 Entity を追加し、
近傍の Ore Slime を高頻度で補充するため、通常の動物が自然湧きする余地を圧迫します。
この Mod は独自スポーナーの頻度と補充数を抑えます。
Ore Slime 自体と、category・AI・ドロップ・レシピ・能力値は元のまま維持します。

また、1.0.108 は biome modifier で Overworld の通常 Slime を自然スポーン一覧から
削除しています。独自追加を無効にしても通常 Slime が本来の条件で湧けるよう、
この削除 modifier だけを `neoforge:remove_spawns`（`biomes: []`） に上書きします。

## ビルド・導入

`../mods/oreslimes-1.0.108.jar` と Java 25 を用意してください。

```sh
./gradlew build
./gradlew runCompatTestServer
```

通常のサーバー tick による個体数・出現時刻の収集には `./gradlew runCompatStatisticsServer` を使えます。
条件と結果の読み方は [統計収集](docs/testing.md#通常サーバー-tick-による統計収集) を参照してください。
CREATURE のうち、自然湧き対象の34種類は [網羅試験](docs/testing.md#自然湧き34種類の網羅試験) で確認できます。

生成された `build/libs/ore-slimes-compat-1.0.0.jar` を、
NeoForge サーバーの `mods/` に Ore Slimes 本体と一緒に配置します。
シングルプレイの場合はクライアントの `mods/` に配置します。処理は統合サーバー側でのみ行います。
専用サーバーへの接続クライアントには本 compat の導入は不要です。

## Server config

初回ワールド起動時に `<instance>/config/ore_slimes_compat-server.toml` を生成します。
配布用の初期設定例は [config/ore_slimes_compat-server.toml](config/ore_slimes_compat-server.toml)。
この NeoForge 版では、ワールド専用の設定を使う場合に
`<world>/serverconfig/ore_slimes_compat-server.toml` を配置して標準設定を上書きします。
通常はサーバーの `config/` 内を編集してください。確実な適用にはサーバーを再起動します。

`oreSlimeSpawner` セクションで、独自スポーナーの頻度と上限を設定します。
以下では、重複した抽選を避けるためにまとめるプレイヤー周辺の範囲を coverage と呼びます。
個体数の確認には、プレイヤーの当たり判定を囲む直方体（AABB）を広げた範囲を使います。

| 項目 | 初期値 | 許容範囲・意味 |
|---|---:|---|
| enabled | true | false で独自 Ore/Vanilla Slime の追加を停止 |
| intervalTicks | 1200 | 20–72000、20 TPS で60秒 |
| maxNearby | 3 | 0–64、0 でも独自追加を停止 |
| spawnChance | 0.35 | 0–1、coverage ごとの抽選確率 |
| searchAttempts | 4 | 1–32、1回の抽選成功後の探索回数 |
| countRadius | 72 | 16–256、プレイヤー AABB の拡張量 |
| minSpawnDistance | 18 | 1–128、水平距離の内側境界 |
| maxSpawnDistance | 52 | 1–128、水平距離の外側境界 |
| spawnVanillaSlimes | false | 独自通常 Slime 追加。標準の自然湧きは変更しない |
| vanillaSlimeChance | 0.02 | 0–1、Ore 抽選等を通過した後の追加確率 |

ルート項目 `debugSpawner = false` を true にすると、スキップ理由と追加成功位置を
DEBUG ログへ出力します。ロガー側でも DEBUG の有効化が必要です。
設定方法とログの確認先は [検証手順のログ設定](docs/testing.md#診断ログの有効化と確認先) を参照してください。
この Mod のスポーンログは診断を有効にしたときだけ出力します。

距離の設定に矛盾がある場合は、実効 max distance を min 以上に、
実効 count radius を max distance 以上に補正します。設定ファイルの値はそのまま保持します。
読み込み済みの chunk を対象に、円環上の水平距離で探索します。ブロック座標への丸め誤差があります。

## 挙動・制約

独自スポーナーが各ディメンションのプレイヤーを一巡する処理を pass と呼びます。
近接したプレイヤーについて、同一 pass 内で実効 count radius の2倍以内
（初期値144 blocks、水平距離）の coverage をまとめて抽選します。
抽選に失敗した場合も、その coverage は抽選済みとして扱い、
近接した2人による抽選確率の単純な倍増を防ぎます。
代表はレベルの player list 順で選ぶため、近隣プレイヤーへの公平な持ち回りは保証しません。
離れた集落は独立して処理します。3体という上限は近傍への独自補充に適用し、
世界全体では移動や繁殖などによって個体数が変わります。

標準スポーナーが集計した最新の `CREATURE` 個体数（census）を読み、
そのディメンションの標準上限（global cap）が埋まっていれば補充しません。
同じ pass で成功した追加も加算します。census 未作成時は補充を見送ります。
標準の自然湧きでは、プレイヤーごとの上限（local cap）を引き続き Vanilla が判定します。
本 Mod の独自補充は、上記の census と global cap に基づいて制御します。
他 Mod による同 tick の追加は、追跡しきれない場合があります。

導入後も既存の個体はそのまま残り、以後の独自補充を抑えます。
Rabbit などの自然湧きは、地形・明るさ・プレイヤー距離・既存の家畜などの条件に左右されます。
導入によって即座に出現するとは限りません。
Ore Slimes 本来の、通常 Slime の攻撃対象を条件に応じて解除する処理（temperament）は、
10 ticks間隔でそのまま維持します。

## 保守・検証

[調査](docs/investigation.md)、[設計](docs/design.md)、[検証手順](docs/testing.md) を参照。
upstream 更新時は EntityType category、`OreSlimeWorldSpawner` の tick loop、
private helper signatures、直接追加 call site、spawn rules、通常 Slime の処理と
`reduce_vanilla_slime_spawns` biome modifier を再確認してください。
NeoForge/Minecraft 更新時は census API と cap 計算も再検証が必要です。
誤適用を避けるため Ore Slimes の依存バージョンを 1.0.108 に固定し、Mixin を required にしています。
