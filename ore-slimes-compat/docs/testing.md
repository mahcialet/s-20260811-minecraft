# 検証

## 自動検証

独自スポーナーの制御条件を検証するには、次のコマンドを実行する。
Java 25、NeoForge 26.1.2.71、`../mods/oreslimes-1.0.108.jar` を使う。

```sh
./gradlew build
./gradlew runCompatTestServer
```

後者は `run-tests/` にローカル専用の隔離サーバーを起動する。
接続先は localhost で、port は動的に割り当てる。
テスト Mod は検証用サーバーだけで使用し、配布 jar は互換 Mod の本体のみで構成する。
全 assert 完了時のみ `run-tests/compat-test-result.txt` に `PASS assertions=N` を出し、
サーバーを停止する。Gradle はこの結果を確認する。
開発起動では NeoForge が IDE 用の EULA 処理を用いる。使用するのは隔離したテストワールドである。

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

この試験では tick handler を直接呼び出して制御条件を確認する。
120000回という値は handler の呼び出し回数であり、実ワールド tick の経過や実時間の放置を表す値ではない。
動物と Slime の試験は実 biome の spawn list を使う
`NaturalSpawner.spawnCategoryForPosition` で抽選・配置・spawn permission を通す。
この overload は通常の scheduler と global/local cap の判定を通さない。
そのため、global census による独自補充の抑制は別の試験で検証している。
固定地点での経過観測には次の統計収集を使える。
実クライアント2台、長時間移動・繁殖を含むプレイ、関連4 Mod全体の組合せは、
後述する実プレイでの追加確認項目として残っている。

## 通常サーバー tick による統計収集

手動で待機しなくても、次のコマンドでサーバーを起動して記録できる。

```sh
./gradlew runCompatStatisticsServer
```

`run-statistics/` の隔離サーバーで、1人、20 blocks 間隔の2人、
`enabled=false` の2人という3条件を順に各24000 ticks実行する。
合計は20 TPS換算で60分に相当する。サーバーの tick sprint 機能で高速実行するため、
実時間で60分間稼働した結果ではなく、性能・TPSを保証するものでもない。
疑似プレイヤーを通常のチャンク管理へ登録し、自然スポーン対象チャンクと
個体数集計が有効であることを確認する。
スポーン関数は通常のサーバー処理から呼び出し、tick counter と census も実サーバーの値を使う。

毎回 seed `384731` の新規 flat world（plains、草の地表はY64）を作り、
プレイヤーを `(530.5, 65, 530.5)`、2人目をX方向へ20 blocks離して置く。
開始前に600 ticks以上待ち、条件を切り替える時だけ読み込み済みのMobを除去する。
計測中は個体をそのまま残し、自然な移動・死亡・分裂も進む。
探索回数や距離などが既定値と異なる設定では、収集を中止する。

結果は `run-statistics/statistics.jsonl` に保存する。

- `start`: 条件名、開始ゲーム時刻、seed、人数。
- `sample`: 200 ticksごとの種別個体数、累積加入数、CREATURE census、
  自然スポーン対象チャンク数。その期間の加入イベントには種別・ゲーム時刻・座標も記録する。
- `summary`: 各条件の加入総数と、種別・生成理由別の `FinalizeSpawnEvent` 回数。

`statistics-result.txt` の `COMPLETE` は、3条件の収集完了を意味する。
受け入れ要件の合否は、収集結果と各試験の条件から別途判断する。途中で集計対象チャンクがなくなった場合や
例外が発生した場合は失敗し、Gradleも異常終了する。
JSONLと完了ファイルは次回の実行で更新される。過去のテストワールドは残る。

加入数はディスクからの再読み込みを除くMobの加入イベント数であり、
独自補充・自然生成・分裂等を合算した値である。生成理由別の集計も加入成功とは別なので、
独自補充の35%確率、1200 tick間隔、近傍3体制限は、前節の自動検証で確認する。
3条件は同じワールドで順に進むため、時刻・乱数・読み込み範囲が異なる。
結果は各条件下の経過観測として扱い、人数だけによる増加率やcompat導入前後の改善率の算出には使わない。
Rabbit用のdesert、地下やswampでの通常Slime自然生成、関連4 Modの組合せは、別の試験条件で扱う。

2026-10-22 の実行は3条件とも120 samplesを記録し、`COMPLETE` で終了した。
同時に `build` と既存の制御試験も成功し、`PASS assertions=232` を確認した。

| 条件 | 自然スポーン対象チャンク数 | Ore Slime加入総数（全経路） | 通常動物の観測 |
| --- | --- | --- | --- |
| 初期設定・1人 | 289 | 40 | Pig加入4、NATURALの生成処理4 |
| 初期設定・2人 | 306 | 44 | Chicken加入2、生成理由はJOCKEY。通常動物の自然湧き成功とは数えない |
| 独自スポーナー無効・2人 | 306 | 41 | Sheep加入4、NATURALの生成処理4 |

無効化後のOre Slime加入は、独自スポーナー以外の生成経路が残ることと整合する。
各個体の由来を特定するには、個体ごとの生成記録との照合が必要である。
全動物の共存と自然湧きの頻度については、追加の受け入れ確認が必要である。

この3条件ではCowを観測できなかったため、次の追加試験で確認した。

### 牛の追加観測

```sh
./gradlew runCompatStatisticsServer -PobserveCows=true
```

`run-cows/` の新規ワールドで初期設定・1人の観測を行う。
24000 ticksを1区間とする。牛を確認できなければ区間の間でMobを除去し、
最大10区間まで観測を続ける。
`NATURAL` の生成処理、加入イベント、後続sampleでの生存確認が同じUUIDで揃った牛を
`verifiedNaturalCows` に記録する。未確認のまま上限に達すれば失敗する。
2026-10-22 の実行では2区間目で4体を確認し、`COMPLETE` で終了した。
この結果は通常のサーバー処理による自然湧きの観測で得たものである。

### CREATURE の対象一覧

```sh
./gradlew runCompatStatisticsServer -PcreatureInventory=true
```

実際の登録情報を `run-creature-inventory/creature-inventory.json` に出力する。
CREATURE に属する型を、全スポーンカテゴリの biome 候補一覧と照合する。
ヤマネコは型としては CREATURE に属するが、biome の候補一覧では MONSTER 側に登録されている。
そのため、CREATURE の候補一覧だけを調べると対象から漏れる。
このコマンドの `COMPLETE` は、登録情報の一覧を取得できたことを示す。出現の実測は網羅試験で行う。

Minecraft 26.1.2 / Ore Slimes 1.0.108 / 本compatの構成ではCREATUREは44種類。
通常のbiome自然湧き対象はVanilla 21種類とOre Slime 13種類の計34種類である。
残る10種類は、実装調査に基づいて次の専用経路として一覧化した。
今回の実測範囲は自然湧きの34種類であり、専用経路の動作は未実測である。

| 種類 | 専用の生成経路 |
| --- | --- |
| Allay | 構造物の保存個体、踊り中の複製 |
| Bee | 巣の保存個体の放出、繁殖 |
| Cat | 村のベッド・既存個体数や対象構造物を判定するCatSpawner |
| Happy Ghast | 水没したDried Ghastの水和完了 |
| Mule | HorseとDonkeyの繁殖 |
| Skeleton Horse | 雷雨時の罠生成、罠の起動 |
| Sniffer | Sniffer Eggの孵化 |
| Tadpole | Frogspawnの孵化 |
| Wandering Trader | 専用spawnerの時間・確率・配置判定 |
| Trader Llama | Wandering Traderの生成に伴う追加試行 |

### 自然湧き34種類の網羅試験

```sh
./gradlew runCompatStatisticsServer -PcreatureCoverage=true
```

`run-creature-coverage/` に専用の新規ワールドを作る。通常のサーバーtickと
自然スポーン処理を使い、次の地形条件を順に試す。候補一覧と抽選重みは元の値を使う。
バイオームは実レジストリの値を使うが、床とバイオーム配置は試験用に整える。

| バイオーム | 床 | Vanillaの対象 |
| --- | --- | --- |
| savanna_plateau | 草 | Sheep、Pig、Chicken、Cow、Donkey、Armadillo、Llama、Wolf |
| plains | 草 | Horse |
| taiga | 草 | Fox、Rabbit |
| bamboo_jungle | 草 | Panda、Parrot、Ocelot |
| swamp | 草 | Frog |
| frozen_ocean | 氷 | Polar Bear |
| snowy_slopes | 草 | Goat |
| desert | 砂 | Camel |
| mushroom_fields | 菌糸 | Mooshroom |
| beach | 砂 | Turtle |
| nether_wastes | 溶岩 | Strider |

Ore SlimeはPlant、Mushroom、Bee、Stone、Coal、Copper、Iron、Redstone、Lapis、
Gold、Emerald、Diamond、Magicの13種類を確認対象とする。
最初の条件でVanillaの対象とOre Slime全種を待ち、その後の条件へ進む。
Overworldは床Y64・足元Y65、Strider用には実Netherの天井上に床Y199・足元Y200の溶岩平台を作る。
観測対象は近傍チャンク全体であり、Nether内の既存の溶岩で生成した個体も含む。
昼・晴天を固定し、疑似プレイヤーは1人、compatは初期設定を使う。

自然湧きの試行を続けられるよう、400 ticksごとの観測区間の間でMobを除去して枠を空ける。
各条件の観測は最大2000区間までとする。上限に達しても未確認の種類が残った場合は、
次の条件の観測へ進み、すべての条件を終えてから未確認の種類を報告する。
`FinalizeSpawnEvent` の理由が `NATURAL` であり、同じUUIDの加入と
**生成時刻より後のtickでのワールド内生存**が確認できた種類だけを成功とする。
生成は通常のサーバー処理に任せ、census と tick counter も実サーバーの値を使う。

`creature-coverage.jsonl` は種別・UUID・生成/確認時刻・座標・バイオーム・
稼働チャンク数・近傍プレイヤー数などを記録する。
`creature-coverage-result.json` は確認済み・未確認・専用経路の除外種を一覧化する。
**34種類すべてが確認できた場合だけ** `statistics-result.txt` を `COMPLETE` にする。
未確認の種類が残る場合や例外が発生した場合は、Gradleも失敗する。

この試験で確認するのは、各種類が通常の自然生成経路を通って出現することである。
自然地形での出現頻度、長時間の個体数推移、全種類の同時共存を判定するには、別の観測が必要である。
Ore Slimeのbiome抽選重みの合計は8990であり、通常動物の抽選頻度を下げる要因になる。
独自補充の抑制と、この自然湧き側の重みは別の設定・処理である。

未確認の種類だけを別実行で追試する場合は、対象IDを指定する。

```sh
./gradlew runCompatStatisticsServer -PcreatureCoverage=true -PcoverageTarget=minecraft:horse
```

追試の結果は `run-creature-followup/` に分けて保存し、元の網羅試験の結果を保持する。
馬にはplains、それ以外には上表の対応する条件を使う。Ore Slimeはsavanna_plateauを使う。
追試の `COMPLETE` は指定した1種類だけの確認成功であり、34種類の合格とは区別する。
追試を再実行すると同ディレクトリの結果が更新されるため、複数種を追試する場合は結果を保存しておく。

高度制限に抵触しない種類には、床Y-63・足元Y-62の薄いflat worldでも追試できる。

```sh
./gradlew runCompatStatisticsServer -PcreatureCoverage=true -PcoverageTarget=minecraft:polar_bear -PcompactCoverage=true
```

この場合は `run-creature-low-floor/` に結果を保存し、床高度を `overworldFloorY` に記録する。
高さの抽選による配置失敗を減らすための地形条件であり、種別の抽選重みと生成処理は元のまま使う。
標準高度の結果とは分けて扱う。海面以上が必要なOcelotと、Nether専用のStriderの条件には使わない。

#### 2026-10-22 の実測結果

初回の網羅試験と種別ごとの追試を合わせて、**34種類すべて**を確認した。
実レジストリの自然湧き対象34種類と、証拠の種別集合が一致し、未確認は0種類である。
すべての証拠について、生成時刻より後のtickでの生存と、
稼働チャンク・近傍プレイヤーの存在を照合した。

| 実行 | 確認した種類・条件 |
| --- | --- |
| 初回の網羅試験 | Ore Slime全13種類を含む30種類。Overworldの標準高度の試験地形で確認 |
| Horse追試 | plains、足元Y65、42区間目で確認 |
| Polar Bear追試 | frozen_ocean、足元Y-62の氷床、25区間目で確認 |
| Turtle追試 | beach、足元Y-62の砂床、64区間目で確認 |
| Strider追試 | 実Nether、1区間目で確認。実際の確認位置はY約17で、天井上の平台ではなく既存の溶岩領域 |

初回の草地条件ではHorse、雪原条件ではPolar Bearが上限まで未確認だった。
別条件の追試で確認し、全種の証拠が揃った時点で残っていた重複実行を停止した。
**34種類の確認は追試との統合結果であり、初回単独の状態は `COMPLETE` 未達のままである。**
再実行用の地形は、Horseをplains、Polar Bearをfrozen_oceanへ更新している。

統合結果は `run-creature-coverage/combined-result.json`、対応する保存済み証拠は
同ディレクトリの `evidence/` にある。統合結果には検証日と各種の証拠ファイルを記録した。
この統合結果は当日の記録として保持する。通常の再実行では実行ごとの結果ファイルを更新するため、
新しい実行の成否はそちらで確認する。
専用経路の10種類については、依頼範囲に従って実装調査による一覧を成果とし、動作は未実測である。

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
既存の `loadedMods` 設定は残し、生成先の Log4j 設定は ModDevGradle に任せる。
設定後に `./gradlew runServer` で起動すると、確認先は `run/logs/debug.log` になる。
`./gradlew runCompatTestServer` の場合は `run-tests/logs/debug.log` を確認する。
後者の Mod 設定は `run-tests/` を基準とするため、通常の開発用サーバーとは別に設定する。
既定のファイル出力設定では `latest.log` に DEBUG が出ないため、`debug.log` を使う。

配布 jar を導入したサーバーで独自の Log4j 設定を使っている場合は、
`com.jeremiah.oreslimes.world.OreSlimeWorldSpawner` のロガーを DEBUG にし、
出力先の appender でも DEBUG を除外していないことを確認する。
出力ファイルの場所は、そのサーバーの Log4j 設定に従う。

`Ore Slime spawn skipped:`、`Ore Slime spawned at`、`Vanilla slime injection disabled`
を検索して、独自スポーナーの判定を確認する。ログから追加停止を判断する際は、
両方の設定が有効であることと、処理周期が到来したことも併せて確認する。
診断後は `debugSpawner=false` に戻し、一時的に追加したログ設定も戻す。

## 実際の長時間プレイによる受け入れ確認

以下は、実際の長時間プレイに向けた追加の受け入れ確認項目である。
固定地点での待機と個体数の記録は、上記の統計収集と同様に自動化できる。
現行の統計収集に含まれない地形・設定・Mod構成は、専用の試験条件を用意する。
実クライアントの表示や操作感は実プレイで確認する。
生成済みの大量個体が残るワールドでの導入前後だけでは自然湧きを比較できないため、
同じ seed の新規テストワールドを複製して比較する。
Minecraft 26.1.2 / NeoForge 26.1.2.71 / Ore Slimes 1.0.108 / 本 compat を使用。
実際の Modpack での検証時は、関連する Slime Tech / Arcane Slime Tech /
Slime Dimensions / Slimequests の4 Modと、他 Modの正確なバージョンも記録する。

各試験は独立して実施する。試験1〜6を始めるたびに、
[配布初期設定](../config/ore_slimes_compat-server.toml) に戻してから、当該試験で指定された項目を変更し、
サーバーを再起動する。診断用の `debugSpawner=true` は維持してよい。
前の試験の設定や個体数を引き継がず、各試験を独立した状態で始めるため、必要に応じて比較用ワールドの複製を使う。

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
