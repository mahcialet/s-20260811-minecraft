# 設計判断

## 対象と介入点

Mixin の対象は `com.jeremiah.oreslimes.world.OreSlimeWorldSpawner` の1クラスに限定する。
公開 tick event は存在するが元 handler のスポーンだけを取り消す API はなく、
EntityJoinLevelEvent では独自追加と通常追加の由来を識別できない。
従って最小範囲の Mixin を採用する。詳細は investigation.md を参照。

`onServerTick` の処理の組み立て（orchestration）を HEAD injection で置き換える。
元の tickCounter を進め、全非 spectator player の `calmNearbyVanillaSlimes` を
従来どおり10 ticksごとに呼ぶ。スポーン処理の周期だけを設定で変更可能にする。
元の Ore/Vanilla spawn helper を shadow 呼び出しし、種の重み、サイズ、
地面の許可、collision 判定を維持する。AI/EntityType 側は元の実装をそのまま使う。

`findSurfaceSpawnPos` は設定可能な円環サンプリングに置き換える。
upstream の矩形状探索は遠い角で約62 blocksに達するが、本実装では設定の
18–52 blocks が直接水平半径を表す。高さを取得する前に、対象が読み込み済みの chunk であることを確認する。
upstream の `trySpawnOreSlimeNearPlayer` helper は、addFreshEntity の呼び出し後、
追加の成否にかかわらず true を返す。そこで addFreshEntity のみを redirect し、
元の追加処理を呼び出した実際の戻り値を保存し、その結果に基づいて成功数を数える。
他 Mod が JoinLevel をキャンセルした場合も、実際の追加結果を反映できる。

## 上限と multiplayer

coverage と追加成功数を保持する期間は、1 pass / level 内に限定する。
この範囲の一時状態で管理を完結させる。
coverage はプレイヤー周辺の抽選範囲を指し、水平距離で重複を判定する。
確率判定の前に予約し、抽選に失敗しても後続プレイヤーによる重複抽選を防ぐ。
デフォルトでは144 blocks以内の後続プレイヤーを、先に予約した coverage にまとめる。
近接した2人が別々の補充と確率抽選を行うことを防ぐ保守的な制限である。
代表プレイヤーが固定され、そのプレイヤーに処理が集中することは許容する。
管理の範囲は各 pass 内とし、世界全域や移動プレイヤーへの永続的な割当枠（quota）は対象外とする。

Minecraft の公開 census getter のみを用い、global cap を
`CREATURE.getMaxInstancesPerChunk() * spawnableChunkCount / 289` として評価する。
289 は26.1.2の標準スポーン範囲17×17に対応し、バージョン依存のため更新時に再検証する。
最新 snapshot の count と同 pass の追加成功数を合計する。
この cap 判定は、独自追加を保守的に見送る条件として使う。
標準の自然湧きには、既存の spawn system の local cap、潜在密度、persistent mob の除外規則が引き続き適用される。
本 Mod の独自補充には、公開 census と同 pass の追加成功数による上記の cap 判定を使う。

## 設定と互換性

実サーバー試験で、upstream の `reduce_vanilla_slime_spawns` biome modifier が
Overworld の通常 Slime を spawn list から削除していることが判明した。
標準 Slime 自然湧きを維持するため、独自追加の制御に加えて、この削除 modifier も調整する。
NeoForge の公開データ機構を使い、同じ resource ID を `neoforge:remove_spawns`（`biomes: []`） で上書きする。
これにより、既存の spawn entry と Vanilla の確率・条件をそのまま使えるようにする。
依存順序 AFTER で Ore Slimes の後に本 Mod をロードする。
この上書きは enabled / spawnVanillaSlimes の値にかかわらず有効とする。
他のデータパックが同じ ID を上書きした場合は pack priority が優先されるため再確認が必要。

NeoForge SERVER config を使用する。26.1.2.71 の標準保存先は instance の `config/` であり、
各ワールドの `serverconfig/` に同名ファイルを置くとワールド専用の上書きになる。
`enabled=false` はこの独自追加経路を停止する。既存個体は残り、
biome spawn、卵・コマンド・繁殖等による生成は引き続き動作する。
`spawnVanillaSlimes=false` でも標準 Slime 自然湧きと既存 temperament 処理は維持する。
enabled / nearby / CREATURE cap / chance を通過した時のみ vanilla の低確率抽選を行う。
通常 Slime の cap は既存のままとし、独自補充を低頻度のオプションとして残す。

対象 Mod の厳密バージョン依存と required injection により、更新で対象が変化した時は
起動時に失敗させ、パッチが適用できない状態を検出する。
成果物は互換 Mod 単体とし、本体 jar と関連 Mod は元のまま別途使用する。
