# Phase 2: 標準自然スポーンへの統合調査

Ore Slimes 1.0.108 は、バイオームのスポーン候補から選ばれる標準経路と、プレイヤーの近くへ直接追加する backup 経路を持つ。Phase 2 では標準経路を基本に据え、backup は管理者が必要に応じて有効にする補助経路とした。本書はその判断に使う実装上の条件をまとめる。個体数の推移と採用した既定値は [実測結果](phase2-results.md) に記録する。

調査対象は `../mods/oreslimes-1.0.108.jar`。SHA-256 は `d53fd2b38cc485714236f4d8c16b3ba6b6b547af268d0c9284034d9c02298bd0`。`javap -p -c` で対象クラスを確認し、Minecraft 26.1.2 / NeoForge 26.1.2.71 の patched sources と照合した。Phase 1 の詳細は [従来の調査](investigation.md) と [設計判断](design.md) を参照。

## 標準経路の登録

`data/oreslimes/neoforge/biome_modifier/add_ore_slime_spawns.json` は `neoforge:add_spawns` を使い、次の 13 種類を登録する。`ModEntities.slime` がすべてを `MobCategory.CREATURE` として作成するため、牛・羊・豚・ウサギなどと個体数上限を共有する。

| Entity ID | weight | minCount | maxCount |
|---|---:|---:|---:|
| `oreslimes:plant_slime` | 1200 | 2 | 5 |
| `oreslimes:mushroom_slime` | 240 | 1 | 2 |
| `oreslimes:bee_slime` | 220 | 1 | 2 |
| `oreslimes:stone_slime` | 2000 | 2 | 5 |
| `oreslimes:coal_slime` | 1300 | 2 | 4 |
| `oreslimes:copper_slime` | 1200 | 2 | 4 |
| `oreslimes:iron_slime` | 840 | 1 | 3 |
| `oreslimes:redstone_slime` | 640 | 1 | 3 |
| `oreslimes:lapis_slime` | 560 | 1 | 3 |
| `oreslimes:gold_slime` | 440 | 1 | 3 |
| `oreslimes:emerald_slime` | 140 | 1 | 2 |
| `oreslimes:diamond_slime` | 140 | 1 | 2 |
| `oreslimes:magic_slime` | 70 | 1 | 2 |

Ore 13 種の weight 合計は **8990**。同じ modifier は `minecraft:slime` も weight 500、minCount 2、maxCount 4 で追加する。通常 Slime は `MONSTER` なので、この 500 は CREATURE 側の抽選合計に含めない。

対象は `#oreslimes:ore_slime_spawn_biomes`。tag ファイルは `replace: false` で、`#c:is_overworld` と `minecraft:plains`、`minecraft:sunflower_plains`、`minecraft:swamp`、`minecraft:mangrove_swamp` を含む。通常の Overworld と同じバイオームを使う Superflat もこの登録対象になる。最終的な対象集合は、サーバーで読み込む biome tag と datapack によって決まる。

別の `reduce_vanilla_slime_spawns.json` は対象 tag から通常 Slime を削除する。Phase 1 compat はこの削除 modifier の対象を空にして、通常 Slime が標準経路の候補に残るようにしている。Phase 2 でも、標準経路の登録と backup による通常 Slime の追加を区別する。

## 標準経路の成立条件

`ModEntities.registerSpawnPlacements` は全 Ore type に共通の登録を行う。

| 項目 | 確認した条件 |
|---|---|
| SpawnPlacement | `SpawnPlacementTypes.ON_GROUND` |
| Heightmap | `MOTION_BLOCKING_NO_LEAVES` |
| 登録方法 | `RegisterSpawnPlacementsEvent.Operation.REPLACE` |
| 述語 | `OreSlimeEntity.canSpawn` → `OreSlimeSpawnRules.canNaturallySpawn` |
| 自然生成の reason | `NATURAL` と `CHUNK_GENERATION` に床と slime chunk の条件を適用 |
| 床 | grass block、dirt、stone、deepslate、sand、red sand、farmland、mycelium、podzol |
| Slime chunk | `WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, seed, 987234911L).nextInt(10) == 0` |
| Y | Ore 固有の高度条件は設けず、標準の位置選択・地上配置・衝突判定に従う |
| Light | Ore 固有の明るさ条件は設けず、上記の共通述語を適用 |
| Difficulty | Ore の自然スポーン述語は難易度を参照せず、全種に同じ条件を適用 |

Slime chunk の判定には `WorldGenLevel` の seed を使用する。通常 Slime の「Y < 40」や沼地の条件を Ore Slime に転用しない。`NATURAL` と `CHUNK_GENERATION` 以外の reason に対して、Ore の述語は true を返す。ここで扱うのは生成条件であり、生成後の生存・despawn は別の確認項目となる。

標準の `NaturalSpawner` は、候補選択に続いて配置、spawn rule、衝突、個体位置を確認し、NeoForge の spawn event を経て追加する。最近傍プレイヤーから 24 block を超える距離と、ワールドの respawn 地点からの距離条件も適用する。最大距離や実際に処理される chunk は entity type、category、chunk の稼働状態に従う。

### CREATURE の周期と上限

`ServerChunkCache` は `gameTime % 400 == 0` のとき、persistent category の自然スポーンを許可する。`CREATURE` はこの category に属するため、基本の試行周期は **400 game ticks**、20 TPS なら 20 秒となる。さらに gamerule、spawnable chunk、プレイヤー、個体数上限などの条件を満たして初めて試行する。

global cap の条件は `count < 10 * spawnableChunkCount / 289`。整数除算で上限を求める。標準処理は local cap と spawn potential も使用する。`NaturalSpawner.createState` は、永続化の扱いで除外される Mob を除き、読み込んだ個体を category 別に数える。このため backup で追加した Ore Slime も、その後の CREATURE census に入る。

候補地点の Y は、level の minY から地表 heightmap の上までの範囲で選ぶ。地表より低い候補や配置に適さない候補も生じるため、weight の比率だけから単位時間の生成数を計算することはできない。

## Backup 経路の条件

`OreSlimeWorldSpawner.onServerTick` の元実装は、サーバー共通の static counter を進め、全 level の非 spectator player を順番に処理する。

| 項目 | 元実装の処理 |
|---|---|
| 周期 | backup は 200 server ticks、通常 Slime の calm は 10 server ticks |
| 対象 level | `getAllLevels()` の各 level。dimension ID や world type で分岐しない |
| 対象 player | `level.players()` の非 spectator。重なる player もそれぞれ抽選する |
| 近傍判定 | player の AABB を各軸 72 block 膨張し、OreSlimeEntity と subclass を数える |
| 近傍上限 | 8 以上でその player の backup 処理を終了 |
| 抽選 | `nextFloat() <= 0.75F` で探索へ進む |
| 探索回数 | 最大 8 回。Ore helper が true を返したら終了 |
| 座標 | 主軸の差 18–52、従軸 0–34 に符号を付け、軸を交換して地表を探す |
| Y | `MOTION_BLOCKING_NO_LEAVES`、`minY < y < maxY` |
| Ore 条件 | 許可された床と slime chunk。標準経路と同じ床・chunk 述語を使用 |
| 種類 | 上記 Ore 13 種と同じ weight で抽選 |
| 追加 | `new OreSlimeEntity`、サイズ抽選、位置設定、衝突判定、`addFreshEntity` |

backup は個体を直接追加するため、Biome Modifier の候補選択や標準 mob cap、SpawnPlacement、FinalizeSpawn の処理とは独立して動く。追加時の `EntityJoinLevelEvent` は通る。元の helper は `addFreshEntity` の返り値を捨てて true を返すため、helper の成功数とワールドへの加入数を分けて測定する必要がある。

複数 player の位置が重なる場合、先に追加された個体は後の player の近傍数に反映される。それでも確率抽選の回数は player ごとに増える。Phase 1 compat は抽選前に近隣 player の対象範囲をまとめ、CREATURE census に基づく gate と loaded chunk 限定の位置選択を加えている。Phase 2 ではこの制御を fallback 用として維持する。

### 通常 Slime の追加と calm

Ore の近傍上限・確率 gate を通った player について、Peaceful 以外では 14% の確率で通常 Slime の helper を 1 回呼ぶ。これは Ore helper の成功を条件にしない。通常 Slime の helper は、grass block、dirt、stone、deepslate、sand、red sand の床でサイズ 1 / 2 / 3 の個体を直接追加する。slime chunk や標準の通常 Slime 自然スポーン述語は参照しない。

10 tick ごとの calm は別の処理で、近くの通常 Slime の target をサイズと攻撃元に応じて解除する。backup の policy を切り替える際も、この既存の挙動を継続する。

## Superflat で確認すること

Superflat でも対象バイオームのスポーン候補と Ore の床・slime chunk 条件を利用できる。草地の平坦面は許可された床に当たり、Ore 固有の Y 制限もないため、標準 tick による自然スポーンが成立する条件を持つ。

一方、`FlatLevelSource.spawnOriginalMobs` は空であり、通常地形のチャンク生成時の動物配置とは異なる。Superflat ではチャンクを生成した直後の数だけで判断せず、プレイヤーのいる状態で自然スポーンの tick を進めて数える。低い地表は候補 Y の選択範囲にも影響するため、通常地形と同じ生成速度になるとは限らない。

backup OFF・標準重み倍率0.01の Superflat desert を72,000 ticks観測し、同一UUIDの `NATURAL` 生成・加入・後続tickでの生存を照合した。Ore Slime 11体とRabbit 9体を確認し、Rabbitのうち6体は生成試行時にOre Slimeが存在していた。一方、同条件のtaigaでは羊8体と狼3体を確認し、Ore Slimeの自然生成は観測できなかった。

この結果から、Superflatでも補助経路を使わず標準経路でOre Slimeが生成される条件を確認できた。初期値は `DISABLED` とし、地形や既存個体数に応じた追加観測を行ったうえで、必要なら低頻度の補助経路を選ぶ。seed・地点・バイオームごとの差と採用理由は [実測結果](phase2-results.md) にまとめる。

## 動物との共存と weight の評価

元の weight 8990 は、標準の家畜 4 種の合計 40（羊 12、豚 10、鶏 10、牛 8）より大きい。ほかの候補を除いた単純な比較では、家畜側の選択率は `40 / (8990 + 40)`、約 0.44% となる。実際のバイオームには追加候補があり、床や slime chunk などの条件でも成否が変わるため、この比率は観測個体数の予測値ではない。

候補の種類を選んだ後に spawn rule を判定する標準経路では、非 slime chunk で Ore が選ばれた試行も失敗する。この構造は、backup を止めても動物の候補が選ばれにくい原因になり得る。共存を評価するときは、Ore の出現に加え、ウサギ・牛・羊・豚の自然生成と時間ごとの個体数を照合する。

Phase 2では、標準経路のOre各種のweightに倍率0.01を掛け、四捨五入後に正の重みを最低1とする。13種類すべてを含む一覧の合計は8990から88となる。category、群れサイズ、SpawnRulesを維持しながら、動物が候補として選ばれる機会を広げる変更である。

NeoForgeのサーバー起動処理はSERVER configを読み込んだ後にBiome Modifierを実行する。
compatは `MODIFY` フェーズで登録済みOre 13種類のCREATURE候補だけを調整し、起動時の倍率を反映する。
倍率の変更は再起動で適用し、`1.0` では元の重みを使う。

通常地形の同倍率・backup OFFの72,000 tick観測では、Ore Slimeの加入2体を記録したが、由来は未分類であり、`NATURAL` の確認数は0だった。desertで得た結果を通常地形全体の出現頻度へ広げるには、seed・地点・既存個体数を変えた観測が必要になる。

標準経路の成立を調べる制御試験と、地形をそのまま使った継続観測は分けて評価する。通常generator上に草地を整備して個体数をリセットする試験は、生成経路の確認に用いる。出現数や共存が不足した場合は、対象tag、配置条件、400 tickの周期、capに数えられている個体、weightを順に確認する。

### Bee Slimeからのハチの追加

`OreSlimeEntity#maybeSpawnBee` は、周囲8 blocksへ広げた範囲のハチが3体未満の場合にハチを直接追加する。
周期的な能力処理は400–800 ticksの間隔と65%の確率を使い、巣の設置成功時にも同じhelperを呼ぶ。
標準の全体capや `FinalizeSpawn` を通る自然生成とは別経路であり、移動したハチによって再び追加条件が成立し得る。
追加後のハチはCREATURE枠へ影響するため、Ore Slime数に加えてハチの由来と個体数も確認した。
採用値の砂漠観測では呼出元のstackからこの能力による追加1件を識別した。Phase 2はこの能力を維持する。

## 根拠を再確認するコマンド

```sh
rtk proxy sha256sum ../mods/oreslimes-1.0.108.jar
rtk proxy unzip -p ../mods/oreslimes-1.0.108.jar data/oreslimes/neoforge/biome_modifier/add_ore_slime_spawns.json
rtk proxy unzip -p ../mods/oreslimes-1.0.108.jar data/oreslimes/tags/worldgen/biome/ore_slime_spawn_biomes.json
rtk proxy javap -p -c -classpath ../mods/oreslimes-1.0.108.jar com.jeremiah.oreslimes.registry.ModEntities
rtk proxy javap -p -c -classpath ../mods/oreslimes-1.0.108.jar com.jeremiah.oreslimes.entity.OreSlimeEntity
rtk proxy javap -p -c -classpath ../mods/oreslimes-1.0.108.jar com.jeremiah.oreslimes.world.OreSlimeSpawnRules
rtk proxy javap -p -c -classpath ../mods/oreslimes-1.0.108.jar com.jeremiah.oreslimes.world.OreSlimeWorldSpawner
```

Minecraft 側の照合対象は `build/moddev/artifacts/minecraft-patched-26.1.2.71-sources.jar` 内の `NaturalSpawner.java`、`ServerChunkCache.java`、`MobCategory.java`、`FlatLevelSource.java`、`BiomeDefaultFeatures.java`。ここに記載した静的な条件と、実測で得た出現数は別々の根拠として扱う。
