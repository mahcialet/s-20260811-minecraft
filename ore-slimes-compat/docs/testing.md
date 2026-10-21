# 検証

## 自動検証

Java 25、NeoForge 26.1.2.71、`../mods/oreslimes-1.0.108.jar` を使う。

```sh
./gradlew build
./gradlew runCompatTestServer
```

後者はローカル専用の隔離サーバー (`run-tests/`、localhost、動的 port) を起動する。
テスト Mod は配布 jar に含まれない。
全 assert 完了時のみ `run-tests/compat-test-result.txt` に `PASS assertions=N` を出し、
サーバーを停止する。Gradle はこの結果を確認する。
開発起動では NeoForge が IDE 用の EULA 処理を用いる。既存の本番ワールドは使用しない。

2026-10-22 の検証結果: `runCompatTestServer` 成功、`PASS assertions=232`。
次を実際の NeoForge サーバーと変換済み Ore Slimes 1.0.108 で確認した。

- 初期設定で120000回の tick handler 呼び出し（100周期）後にも Ore Slime が存在し上限以内。
- 強制確率の100 passで近傍3体上限、interval前の追加なし、確率0・上限0・enabled=false。
- 20 / 100 blocks間隔の FakePlayer 2名で、1 passの追加が倍増せず共有上限を維持。
- Minecraft の実 census による CREATURE cap で補充抑制。
- EntityJoinLevelEvent キャンセル時に追加成功と誤認せず、探索回数上限を維持。
- enabled=false でも既存通常 Slime の10 tick周期の calm 処理を維持。
- Ore Slime が存在する DESERT / PLAINS で Rabbit / Cow / Sheep / Pig の標準生成経路。
- 独自通常 Slime のオプション切替、および削除 modifier 上書き後の標準 Slime 自然生成経路。

これは制御試験であり、120000回の実ワールド tick や実時間の長時間放置ではない。
動物と Slime の試験は実 biome の spawn list を使う
`NaturalSpawner.spawnCategoryForPosition` で抽選・配置・spawn permission を通す。
その overload は通常 scheduler と global/local cap gate を通さないため、
global censusによる独自補充抑制は別の試験で検証している。
実クライアント2台、長時間移動・繁殖を含むプレイ、関連4 Mod全体の組合せは未実施。
以下の手順で確認できる。

## 診断ログの有効化と確認先

診断ログには、Mod の設定とロガーの設定の両方が必要になる。
まず、対象ワールドで有効な `ore_slimes_compat-server.toml` のルート項目を
`debugSpawner=true` にする。設定ファイルの場所とワールド専用設定の優先関係は
[README の Server config](../README.md#server-config) を参照。

このプロジェクトから開発用サーバーを起動する場合は、`build.gradle` の既存の
`neoForge` → `runs` → `configureEach` ブロックへ、診断中だけ次の行を追加する。

```groovy
logLevel = org.slf4j.event.Level.DEBUG
```

これは使用中の ModDevGradle 2.0.148 が提供するログ設定である。
既存の `loadedMods` 設定は残す。生成先の Log4j 設定ファイルを直接編集する必要はない。
設定後に `./gradlew runServer` で起動すると、確認先は `run/logs/debug.log` になる。
`./gradlew runCompatTestServer` の場合は `run-tests/logs/debug.log` を確認する。
後者の Mod 設定は `run-tests/` を基準とするため、通常の開発用サーバーとは別に設定する。
既定のファイル出力設定では `latest.log` に DEBUG が出ないため、`debug.log` を使う。

配布 jar を導入したサーバーで独自の Log4j 設定を使っている場合は、
`com.jeremiah.oreslimes.world.OreSlimeWorldSpawner` のロガーを DEBUG にし、
出力先の appender でも DEBUG を除外していないことを確認する。
出力ファイルの場所は、そのサーバーの Log4j 設定に従う。

`Ore Slime spawn skipped:`、`Ore Slime spawned at`、`Vanilla slime injection disabled`
を検索して、独自スポーナーの判定を確認する。ログがないことだけで追加停止を判定せず、
両方の設定が有効であることと、処理周期が到来したことも確認する。
診断後は `debugSpawner=false` に戻し、一時的に追加したログ設定も戻す。

## 実際の長時間プレイによる受け入れ確認

以下は自動テストの制御された条件とは別に実施する手順。
生成済みの大量個体が残るワールドでの導入前後だけでは自然湧きを比較できないため、
同じ seed の新規テストワールドを複製して比較する。
Minecraft 26.1.2 / NeoForge 26.1.2.71 / Ore Slimes 1.0.108 / 本 compat を使用。
実際の Modpack での検証時は、関連する Slime Tech / Arcane Slime Tech /
Slime Dimensions / Slimequests の4 Modと、他 Modの正確なバージョンも記録する。

各試験は独立して実施する。試験1〜6を始めるたびに、
[配布初期設定](../config/ore_slimes_compat-server.toml) に戻してから、当該試験で指定された項目を変更し、
サーバーを再起動する。診断用の `debugSpawner=true` は維持してよい。
前の試験の設定や個体数を結果へ持ち込まないよう、必要に応じて比較用ワールドの複製を使う。

テストワールドで `doMobSpawning=true`、昼、適切な難易度、自然湧き可能な
明るさ・地面・空間・プレイヤー距離（通常24 blocks以上）を確保する。
繁殖・卵・summon・ワールド生成時の動物を自然湧きの成功例として数えない。
時刻、個体種、出現位置、近傍数、設定、既存 CREATURE 数を記録する。
[診断ログの手順](#診断ログの有効化と確認先) に従って独自追加のログを収集すると、由来を区別しやすい。

1. **Single player / 初期設定**: 固定地点で30–60分待機する。
   待機地点から探索される範囲内に slime chunk があり、その地表に生成可能な床と空間があることを先に確認する。
   独自 Ore Slime 生成には、適切な床に加えて slime chunk が必要である。
   許可される床は [調査結果の独自 spawn flow](investigation.md#3-独自-spawn-flow) を参照。
   この条件を満たす場所で Ore Slime が出現し、同じ72 block近傍が概ね3体で
   補充停止することを確認する。最短でも1200 ticks間隔であり、短時間で8体に戻らないこと。
   移動・分裂・他の生成経路を含む総数が絶対3体以下であるという試験にはしない。
2. **Rabbit**: Rabbit が湧く biome（例: desert）で砂と十分な空間を確保する。
   既存動物で cap が埋まっていない状態から、生成済み chunk 内で Rabbit の
   標準自然湧きを確認する。Ore Slimeが存在しても CREATURE を占有し続けないこと。
3. **Cow / Sheep / Pig**: plains 等の適切な biome に草ブロックを用意し、
   各種の標準自然湧きを確認する。cap が埋まった場合は理由を記録し、
   他動物が満たした cap を compat の不具合と誤認しない。
4. **2 players**: 同一地点および100 blocks以内に2人を置き、同じ時間観測する。
   同一 pass の coverage skip と近傍上限を確認し、単純な2倍の抽選・密度にならないこと。
   144 blocksを超えて離れた別地域では独立した補充が許可されることも確認する。
5. **enabled=false**: 設定を変更して再起動し、独自の Ore/Vanilla 追加ログがないこと。
   卵・レシピ・ドロップ等の Ore Slimes の他機能と通常動物の自然湧きが動くこと。
   既存 Ore Slime や他の自然生成経路は消えないこと。
6. **spawnVanillaSlimes=false**: 独自通常 Slime 追加がないことを確認し、
   別途 slime chunk / swamp の Vanilla 条件で標準自然湧きが続くことを確認する。
   試験5の `enabled=false` は引き継がず、`enabled=true` で実施する。
   `spawnVanillaSlimes=true`、vanillaSlimeChance=1 の比較試験では非 Peaceful で独自追加が発生し、
   Peaceful では発生しないことを確認する。
   この追加も Ore Slime の近傍上限、CREATURE cap、Ore 抽選の条件を通った場合に限られる。
   `vanillaSlimeChance=1` でも、それらの条件で見送られた回には追加されないため、診断ログで区別する。

追加の境界確認: interval=20/72000、chance=0/1、maxNearby=0、min>max、
countRadius<maxDistance、spectatorのみ、満員CREATURE cap、設定リロード、
他 Mod の EntityJoinLevelEvent キャンセルを試す。
小さな interval や確率1は試験専用とし、運用設定は配布初期値に戻す。

自然湧きには乱数があり、短時間の未出現だけでは失敗を確定できない。
地形・人口・距離条件が正しいことを確認し、独自追加ログと標準 spawn 経路を分けて評価する。
