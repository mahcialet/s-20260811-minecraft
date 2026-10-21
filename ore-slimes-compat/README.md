# Ore Slimes Compat

Minecraft **26.1.2 / NeoForge 26.1.2.71 / Java 25** 用の server-side 互換 Mod。
対象は **`oreslimes-1.0.108.jar` のみ**です。Ore Slimes 本体 jar を変更・同梱しません。
Slime Tech / Arcane Slime Tech / Slime Dimensions / Slimequests にはパッチしません。

Ore Slime は Rabbit / Cow / Sheep / Pig と同じ `CREATURE` category です。
独自 world spawner が標準 mob cap を確認せず直接 Entity を追加し、
近傍の Ore Slime を高頻度で補充するため、通常の動物が自然湧きする余地を圧迫します。
この Mod は独自スポーナーの頻度と補充数を抑えます。Ore Slime 自体は削除せず、
category・AI・ドロップ・レシピ・能力値も変更しません。

また、1.0.108 は biome modifier で Overworld の通常 Slime を自然スポーン一覧から
削除しています。独自追加を無効にしても通常 Slime が本来の条件で湧けるよう、
この削除 modifier だけを `neoforge:remove_spawns`（`biomes: []`） に上書きします。

## ビルド・導入

`../mods/oreslimes-1.0.108.jar` と Java 25 を用意してください。

```sh
./gradlew build
./gradlew runCompatTestServer
```

生成物: `build/libs/ore-slimes-compat-1.0.0.jar`。
NeoForge サーバーの `mods/` に Ore Slimes 本体と一緒に配置します。
シングルプレイの場合はクライアントの `mods/` に配置します（処理は統合サーバー側のみ）。
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
通常設定ではこの Mod はスポーンログを出力しません。

距離の矛盾を安全に扱うため、実効 max distance は min 以上、実効 count radius は
max distance 以上に補正します。設定ファイルそのものは書き換えません。
探索は円環上の水平距離（ブロック座標への丸め誤差あり）で、未ロード chunk を生成しません。

## 挙動・制約

独自スポーナーが各ディメンションのプレイヤーを一巡する処理を pass と呼びます。
近接したプレイヤーについて、同一 pass 内で実効 count radius の2倍以内
（初期値144 blocks、水平距離）の coverage を重複抽選しません。
抽選失敗時も coverage を予約するので、2人で確率が単純に2倍になりません。
代表はレベルの player list 順で選ぶため、近隣プレイヤーへの公平な持ち回りは保証しません。
離れた集落は独立して処理します。移動や繁殖などもあるため、世界全体の3体上限ではありません。

標準スポーナーが集計した最新の `CREATURE` 個体数（census）を読み、
そのディメンションの標準上限（global cap）が埋まっていれば補充しません。
同じ pass で成功した追加も加算します。census 未作成時は補充を見送ります。
Vanilla がプレイヤーごとに判定する上限（local cap）を再実装・置換するものではなく、他 Mod の同 tick の追加まで
完全に追跡するものでもありません。

既存の大量個体は消しません。自然湧きは地形・明るさ・プレイヤー距離・既存の家畜などにも
左右されるため、導入だけで即座に Rabbit が湧くとは限りません。
Ore Slimes 本来の、通常 Slime の攻撃対象を条件に応じて解除する処理（temperament）は、
10 ticks間隔でそのまま維持します。

## 保守・検証

[調査](docs/investigation.md)、[設計](docs/design.md)、[検証手順](docs/testing.md) を参照。
upstream 更新時は EntityType category、`OreSlimeWorldSpawner` の tick loop、
private helper signatures、直接追加 call site、spawn rules、通常 Slime の処理と
`reduce_vanilla_slime_spawns` biome modifier を再確認してください。
NeoForge/Minecraft 更新時は census API と cap 計算も再検証が必要です。
誤適用を避けるため Ore Slimes の依存バージョンを 1.0.108 に固定し、Mixin を required にしています。
