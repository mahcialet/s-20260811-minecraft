# 設計判断

## 対象と介入点

Mixin 対象は `com.jeremiah.oreslimes.world.OreSlimeWorldSpawner` 1クラス。
公開 tick event は存在するが元 handler のスポーンだけを取り消す API はなく、
EntityJoinLevelEvent では独自追加と通常追加の由来を識別できない。
従って最小範囲の Mixin を採用する。詳細は investigation.md を参照。

`onServerTick` の orchestration を HEAD injection で置き換える。
元の tickCounter を進め、全非 spectator player の `calmNearbyVanillaSlimes` を
従来どおり10 ticksごとに呼ぶ。spawn のみを configurable interval にする。
元の Ore/Vanilla spawn helper を shadow 呼び出しし、種の重み、サイズ、
地面の許可、collision 判定を維持する。AI/EntityType 側には Mixin をしない。

`findSurfaceSpawnPos` は設定可能な円環サンプリングに置き換える。
upstream の矩形状探索は遠い角で約62 blocksに達するが、本実装では設定の
18–52 blocks が直接水平半径を表す。高さ取得前に loaded chunk を確認する。
`trySpawnOreSlimeNearPlayer` の addFreshEntity のみ redirect し、元の追加処理を
呼び出した実際の戻り値を保存する。upstream helper は addFreshEntity の呼び出し後、
追加の成否にかかわらず true を返す。その値に依存せず、実際の追加結果を使うことで、
他 Mod の JoinLevel キャンセルを成功として数えない。

## 上限と multiplayer

1 pass / level 内で coverage と追加成功数だけを保持する。長寿命の spawn manager は作らない。
coverage の距離判定は水平で、確率判定より前に予約する。
デフォルトでは144 blocks以内の後続プレイヤーは抽選しない。
近接した2人が別々の補充と確率抽選を行うことを防ぐ保守的な制限である。
固定された代表への処理集中は許容し、世界全域や移動プレイヤーの永続 quota は導入しない。

Minecraft の公開 census getter のみを用い、global cap を
`CREATURE.getMaxInstancesPerChunk() * spawnableChunkCount / 289` として評価する。
289 は26.1.2の標準スポーン範囲17×17に対応し、バージョン依存のため更新時に再検証する。
最新 snapshot の count と同 pass の追加成功数を合計する。
local cap、潜在密度、persistent mob の除外規則は再実装しない。
標準 spawn system 自体を変更せず、独自追加を保守的に見送る条件として使う。

## 設定と互換性

実サーバー試験で、upstream の `reduce_vanilla_slime_spawns` biome modifier が
Overworld の通常 Slime を spawn list から削除していることが判明した。
独自追加を止めるだけでは「標準 Slime 自然湧きを維持」という要件を満たせないため、
同じ resource ID を `neoforge:remove_spawns`（`biomes: []`） で上書きする。これは NeoForge の公開データ機構であり、
新しい spawn entry を無条件追加したり Vanilla の確率・条件を変更したりしない。
依存順序 AFTER で Ore Slimes の後に本 Mod をロードする。
この上書きは enabled / spawnVanillaSlimes の値にかかわらず有効とする。
他のデータパックが同じ ID を上書きした場合は pack priority が優先されるため再確認が必要。

NeoForge SERVER config を使用する。26.1.2.71 の標準保存先は instance の `config/` であり、
各ワールドの `serverconfig/` に同名ファイルを置くとワールド専用の上書きになる。
`enabled=false` はこの独自追加経路を停止するが、既存個体や biome spawn、
卵・コマンド・繁殖等による生成は停止しない。
`spawnVanillaSlimes=false` でも標準 Slime 自然湧きと既存 temperament 処理は維持する。
enabled / nearby / CREATURE cap / chance を通過した時のみ vanilla の低確率抽選を行う。
通常 Slime に cap を新設するのではなく、独自補充の低頻度オプションとして残す。

対象 Mod の厳密バージョン依存と required injection により、更新で対象が変化した時は
黙って無効なパッチを配布せず起動時に失敗させる。
本体 jar と関連 Mod は変更せず、成果物に含めない。
