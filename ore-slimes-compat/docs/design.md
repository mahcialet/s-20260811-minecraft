# 設計判断

## 標準スポーンと補助経路

Ore Slime の基本経路は、Ore Slimes 本体の Biome Modifier と SpawnRules による標準自然スポーンとする。
Minecraft の `CREATURE` カテゴリで、他の動物と個体数上限を共有する。
compat は、必要な環境で独自スポーナーを補助的に使えるようにする。

標準経路では、Ore Slime 13種類の選択重みを `standardSpawnWeightScale` で調整する。
初期値は `0.01`、許容範囲は `0.001`–`1.0` とする。
各重みに倍率を掛けて四捨五入し、正の重みは最低1、元の重み0は0に保つ。
全13種類を含む元の一覧では合計8990が88になる。`1.0` では元の重みを使う。
標準経路で動物が選ばれる機会を広げるため、この倍率を採用する。

`OreSlimeWeightModifier` を NeoForge の Biome Modifier として登録し、
`MODIFY` フェーズで `CREATURE` の spawn list を処理する。
対象は `ModEntities.ALL` に含まれる13種類の EntityType と一致する項目とし、
項目の順序、EntityType、最小・最大群れサイズ、対象バイオームを保持する。
ほかの生物の重み、カテゴリ、SpawnRules、AI、ドロップ、レシピ、能力値は元のままとする。
通常 Slime の自然スポーン一覧復元にも、この倍率を適用せず重み500を維持する。

`backupSpawnerMode` は `DISABLED`、`SUPERFLAT_ONLY`、`ALL_WORLDS` の3種類とする。
初期値は `DISABLED` とし、補助経路は管理者が環境に応じて有効にする。
Superflat の desert では補助経路を使わず Ore Slime と Rabbit の自然生成を確認した。
採用値での通常地形と taiga の継続観測では、通常動物等が個体数枠を満たし、Ore Slime の自然生成は0体だった。
通常generatorでの生成経路は、個体数をリセットする別の制御試験で確認した。
出現頻度は地形と既存個体数にも左右されるため、実測条件と合わせて評価する。
調査は [Phase 2 調査](phase2-investigation.md)、測定と採用理由は [Phase 2 結果](phase2-results.md) に記録する。

`SUPERFLAT_ONLY` は、各 `ServerLevel` の実際の chunk generator が `FlatLevelSource` かどうかで判定する。
ワールド名や Overworld の設定をほかのディメンションへ流用せず、Nether なども個別に判定する。
`ALL_WORLDS` は全ディメンションで補助経路を許可し、その後の生成可否は upstream helper の条件に従う。
この policy と Phase 1 の `enabled` を両方満たした場合に、独自 Ore/Vanilla Slime の追加へ進む。

## 対象と介入点

Mixin の対象は `com.jeremiah.oreslimes.world.OreSlimeWorldSpawner` の1クラスに限定する。
公開 tick event は存在するが元 handler のスポーンだけを取り消す API はなく、
EntityJoinLevelEvent では独自追加と通常追加の由来を識別できない。
従って最小範囲の Mixin を採用する。詳細は investigation.md を参照。

`onServerTick` の処理の組み立て（orchestration）を HEAD injection で置き換える。
元の tickCounter を進め、全非 spectator player の `calmNearbyVanillaSlimes` を
従来どおり10 ticksごとに呼ぶ。スポーン処理は policy で対象ディメンションを選び、設定した周期で実行する。
calm 処理は policy によるスポーン判定より先に行うため、標準スポーンだけを使う場合も継続する。
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

Phase 1 の `oreSlimeSpawner` セクションと低頻度の初期値を保持し、
トップレベルに `backupSpawnerMode` を追加する。
標準重みの倍率 `standardSpawnWeightScale` もトップレベルに追加する。
既存の設定ファイルには新しい項目を初期値で補い、既存項目を保持する。
この NeoForge 版では SERVER config のロード後に Biome Modifier が実行されるため、
起動時の倍率を標準 spawn list に反映できる。倍率の変更はサーバー再起動で適用する。
`ALL_WORLDS` を指定すると、Phase 1 の設定で補助経路を利用できる。
元の頻度へ近づける設定例は [README](../README.md#元のスポーナーに近い設定) に示す。
標準スポーンの重みも元へ戻す場合は `standardSpawnWeightScale = 1.0` を指定する。
この場合も円環探索、近接プレイヤーの抽選共有、global cap の確認は compat の処理を使う。

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
