# Ore Slimes 1.0.108 調査

対象は `../mods/oreslimes-1.0.108.jar`。SHA-256 は
`d53fd2b38cc485714236f4d8c16b3ba6b6b547af268d0c9284034d9c02298bd0`。
JDK の `javap -p -c` で jar を読み取り、元の jar を維持したまま確認した。Minecraft 26.1.2 / NeoForge 26.1.2.71 のローカルキャッシュにある patched sources で追加経路も照合した。

第1〜12節では元実装の観察結果と、そこから得た互換版の設計上の制約を記録する。
以下の数値を読む際は、元実装の値と互換版の初期値を区別する必要がある。採用した変更とその理由は
[設計判断](design.md) にまとめている。実サーバー試験で追加判明した通常 Slime の制約は、
第12節の後に別節として記載する。

## 1. EntityType 登録

`com.jeremiah.oreslimes.registry.ModEntities.slime(String, OreSlimeType)` が DeferredRegister へ登録する。生成用 lambda は `EntityType.Builder.of(factory, MobCategory.CREATURE)`、サイズ 0.52 × 0.52、clientTrackingRange 10 を使用する。

## 2. MobCategory

上記 helper で登録する Ore Slime は `CREATURE`。通常 Slime の `MONSTER` とは異なる。Rabbit / Cow / Sheep / Pig と同じ category である。互換 Mod はこの登録・category を維持する。

## 3. 独自 spawn flow

実クラスは `com.jeremiah.oreslimes.world.OreSlimeWorldSpawner`。
`onServerTick(ServerTickEvent.Post)` は static counter を increment し、10 tick と 200 tick の剰余を別々に判定する。

1. 両方の周期に該当しなければ return。
2. `event.getServer().getAllLevels()` をループし、各 level で `RandomSource.create()`。
3. `level.players()` をループ。spectator のみ除外。
4. 10 tick 周期なら `calmNearbyVanillaSlimes(level, player)`。
5. 200 tick 周期でなければ次の player へ。
6. player の AABB を 72 膨張させ、`getEntitiesOfClass(OreSlimeEntity.class, box).size()` を取得。8 以上なら次の player へ。
7. `nextFloat() > 0.75F` なら次の player へ。
8. `trySpawnOreSlimeNearPlayer` を最大 8 回呼ぶ。true で探索を終了。
9. difficulty ID > 0 かつ新しい `nextFloat() < 0.14F` なら `trySpawnVanillaSlimeNearPlayer` を 1 回呼ぶ。

Ore helper は surface position を選び、`OreSlimeSpawnRules.canSpawnFromBackupSpawner` を確認する。これは適切な床 **かつ slime chunk** を要求する。床は grass block、dirt、stone、deepslate、sand、red sand、farmland、mycelium、podzol。Biome spawn list を使った抽選ではない。

次に weighted entry を選び `new OreSlimeEntity(type, level, oreType)`、`randomizeSpawnSize(random)`、block 中心へ `setPos(x + 0.5, y, z + 0.5)`、`level.noCollision(entity)`、`level.addFreshEntity(entity)` の順に実行する。`addFreshEntity` の戻り値は捨てて true を返すため、true は必ずしも追加成功を意味しない。

weighted entries は PLANT 1200、MUSHROOM 240、BEE 220、STONE 2000、COAL 1300、COPPER 1200、IRON 840、REDSTONE 640、LAPIS 560、GOLD 440、EMERALD 140、DIAMOND 140、MAGIC 70。互換実装では upstream helper を呼び出してこの抽選・床判定・サイズを維持する。

10 tick の calm 処理は spawn と別の既存挙動。player AABB + 72 内の Slime から OreSlimeEntity を除外する。size <= 1 なら target を解除、size == 2 では target が最後に攻撃した mob でない場合に解除する。spawner handler 全体を無条件に止めるとこの AI 補助まで止まるため、spawn 無効時も元の呼び出しと周期を維持する必要がある。

## 4. Interval

spawn は **200 server ticks**、calm は **10 server ticks**。counter は level 別でなくクラスの static field。lag 時の実時間は tick 数からの換算より長くなる。

## 5. Nearby cap

**8**。数える対象は OreSlimeEntity とその subclass のみで、Rabbit 等や vanilla Slime は対象外。spawn 前の local gate として使い、既存個体を削除する処理は伴わない。

## 6. Chance

`nextFloat() > 0.75F` なら skip。厳密には 0.75 と等しい乱数も通る。各 player の各 spawn pass で 1 回抽選し、探索 8 回で共有する。

## 7. Radius と地点

count は球ではなく **player bounding box を各軸 72 block 膨張した AABB**。

探索は一方の水平軸に `±(18 + nextInt(35))`、もう一方に `±nextInt(35)` を置き、ランダムに軸を交換する。従って「半径 18–52 の円環」ではない。block 座標差で主軸 18–52、従軸 0–34、水平距離は最大 `sqrt(52² + 34²) ≈ 62.13`。player の blockPosition を基準とする。Y は `MOTION_BLOCKING_NO_LEAVES` heightmap。`minY < y < maxY` を確認する。

元実装は heightmap 取得前に loaded chunk を確認しない。
この調査から、互換版で円環距離と loaded chunk 限定を採用する場合には、元実装との挙動差を説明する必要があると判断した。
採用した位置選択と挙動差は [設計判断の「対象と介入点」](design.md#対象と介入点) を参照。

## 8. Search attempts

Ore helper は最大 **8 回**。最初の true で打ち切る。Vanilla helper は追加で **1 回**のみ。

## 9. Vanilla slime injection

Ore nearby cap と Ore chance の gate を通った player に対してのみ、Peaceful 以外で **14%**。Ore spawn の成否は条件になっていない。surface helper を共用し、床を grass block / dirt / stone / deepslate / sand / red sand に限定する。slime chunk、標準 swamp/slime 自然湧き条件、mob cap は調べない。

`new Slime(EntityType.SLIME, level)`、`setSize(1 + nextInt(3), true)`、位置設定、collision 判定、`addFreshEntity` の順。size は 1 / 2 / 3。互換版は標準の Slime spawn と calm 処理を維持したまま、この helper の呼び出しだけをデフォルトで停止できる。

## 10. Multiplayer

同 level の全非 spectator player を順番に処理し、coverage/chunk の重複排除はない。同じ AABB に入る追加済み個体は後続 player の count に反映されるため、完全に重なる位置で上限が必ず 8 × player 数になるわけではない。ただし chance の試行回数は増え、離れた AABB の非共有領域では別々の上限まで増える。

helper が成功した後に同 tick の二重追加を止めるだけでは、先の player で失敗した場合に、次の player で再抽選できる。そのため chance の試行回数が人数分増える。これを抑えるには、近隣 player の coverage を **chance 判定前** にまとめる必要がある。全 player の calm 呼び出しは別途維持する。

## 11. 標準 mob cap

独自 handler / helper / backup rules は NaturalSpawner、CREATURE population、local mob cap を参照しない。`addFreshEntity` 自体にも標準 mob cap の判定はない。

一方、標準 `NaturalSpawner.createState` は loaded entity を分類し、persistence-required / custom-persistent Mob を除外したうえで category ごとの個体数へ加算する。従って独自経路で追加した Ore Slime も、標準 count の対象になる限り CREATURE population を圧迫する。追加経路が cap を守ることと、追加後に cap へ数えられることは別である。

26.1.2 の `ServerChunkCache.getLastSpawnState()` は public / nullable / `@VisibleForDebug`。`SpawnState.getMobCategoryCounts()` と `getSpawnableChunkCount()` も public。global gate 本体は private であり、式は `count < category.getMaxInstancesPerChunk() * spawnableChunkCount / 289`。

公開 snapshot を読み、同じ snapshot 後の独自追加成功数を加味する保守的な gate は可能。ただし、これは標準 local cap・spawn potential の完全な再実装ではない。実装では snapshot を読み取り専用で扱い、null の場合の扱いを明示すること。version 更新時にはこの式と snapshot 更新時点を再確認する。

## 12. Mixin なしの介入点

`OreSlimes` constructor と spawner bytecode に spawn 調整 config / 専用 API は見当たらない。spawner は非 cancellable の `ServerTickEvent.Post` listener。

独自 helper は SpawnPlacements / checkSpawnRules / finalizeSpawn を経由しないため、NeoForge `MobSpawnEvent.SpawnPlacementCheck`、`PositionCheck`、`FinalizeSpawn` によってこの flow の rate を制御できない。

ただし `ServerLevel.addFreshEntity` → `addEntity` → `PersistentEntitySectionManager.addNewEntity` → `addEntity` では cancellable **EntityJoinLevelEvent** が発火する。従って「event が全くない」は誤り。ただしこの event に独自 spawner 由来であることを識別する情報はなく、自然湧き・spawn egg・他 Mod の追加と安全に区別できない。また interval / 探索回数を制御できず、生成後の reject にとどまる。stack trace による判別や全 Ore Slime の一律 reject は採用しない。

対象クラス 1 個に限定した Mixin で scheduling と位置選択を変更し、upstream の spawn helpers と calm helper を維持する方針とする。entity / AI / drops / recipes / category のクラスは Mixin の対象外とする。

## 実サーバー試験で判明した通常 Slime の追加制約

`data/oreslimes/neoforge/biome_modifier/reduce_vanilla_slime_spawns.json` は
`neoforge:remove_spawns` で `#oreslimes:ore_slime_spawn_biomes` から `minecraft:slime` を削除する。
対象 biome tag には `#c:is_overworld` と plains / sunflower_plains / swamp / mangrove_swamp が含まれる。
従って独自 vanilla injection を止めるだけでは Overworld の標準 Slime 自然湧きは復活しない。
実際、Slime の placement rule が通る slime chunk でも、biome list に Slime がないため
NaturalSpawner の試験が失敗した。この制約に対し、compat は当該削除 modifier だけを公開データ機構の
`neoforge:remove_spawns`（`biomes: []`） で上書きし、標準の spawn list・条件を使用可能にする。
Ore Slime の追加には、別の `add_ore_slime_spawns.json` をそのまま使用する。

## 再現用の読み取りコマンド

```sh
rtk proxy sha256sum ../mods/oreslimes-1.0.108.jar
rtk proxy javap -p -c -classpath ../mods/oreslimes-1.0.108.jar com.jeremiah.oreslimes.registry.ModEntities
rtk proxy javap -p -c -classpath ../mods/oreslimes-1.0.108.jar com.jeremiah.oreslimes.world.OreSlimeWorldSpawner
rtk proxy javap -p -c -classpath ../mods/oreslimes-1.0.108.jar com.jeremiah.oreslimes.world.OreSlimeSpawnRules
rtk proxy javap -p -c -classpath ../mods/oreslimes-1.0.108.jar com.jeremiah.oreslimes.OreSlimes
```

NeoForge sources の照合対象は `EntityJoinLevelEvent.java` / `MobSpawnEvent.java`、patched Minecraft sources は `NaturalSpawner.java` / `ServerChunkCache.java` / `ServerLevel.java` / `PersistentEntitySectionManager.java`。
第1〜12節の根拠は静的解析であり、追加制約の節では実サーバー上の制御試験も扱っている。
ここで示した根拠が確認するのは、静的解析と制御試験の範囲である。長時間自然湧き・複数 client での実測結果とは区別し、検証範囲は [検証手順](testing.md) を参照。
