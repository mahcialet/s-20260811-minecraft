# 導入・移行 / Migration

開発版0.4.0-dev。Minecraft 26.1.2、NeoForge **26.1.2.94のみ**、指定SHA-256のSlime Tech 0.1.44が対象。JEI29.16.0.46はクライアント任意。実行時にも元JAR全体のSHA-256を照合し、不一致ならワールドロード前に停止する。

## 導入前
1. サーバーを正常終了する。ワールド全体（全dimensionとdataを含む）、mods、configをセットで別の場所へコピーする。
2. 導入前バックアップを保持したまま、検証用コピーを用意する。可能なら、そのコピーを更新前のmods・configで起動して他MOD流体を保持するPumpを空にし、再び正常終了する。数字しかない旧動的IDから元の流体名は復元できない。
3. 検証用コピーの停止を確認してから、既存互換パッチJARだけを新JARへ入れ替える。元Slime Techは変更しない。重複パッチを入れない。
4. 最初はコピーしたワールドで起動する。既定設定はblockNonWaterWorldSources=false、fixGrinderEnergy=false、maxTankNetworkMembers=512。任意設定変更には再起動が必要。
5. ログのheld/Quarantined/blocked、ポンプとタンクの残量、通常転送、JEI検索を確認してから運用判断する。

## 保存形式と停止
PumpはSlimeTechCompatSchema=1、SlimeTechCompatFluidId、SlimeTechCompatUnresolvedCodeを保存する。旧固定番号1〜4は移行可能。旧動的番号は種類を推測せず停止し、量・電力・診断番号を保持する。0.3.0のschema1を継続使用する。未登録IDは提供MODを戻して再ロードすると解決できる。流体名のない旧動的番号を管理者の推測で自動割当する処理はない。

Pumpは未来schemaや型の不一致を検出すると、未知キーを含む元データを保持し、稼働・抽出・充電を停止する。対応版に戻す前にコピーを確保する。

Tankは負量・容量超過・未登録ID・混在を検出すると、内容を消去せず流体転送を停止する。空の隣接チャンクが未ロードの場合も、保守的に流体転送を停止する。GUIにはローカル量を渡すが、安全に集計できない場合の外部集計は0になり得る。0表示は保存内容の消去を意味しない。

内容を救出する際は、コピー上で機械を設置したままネットワークを分離する。採掘タグによる回収の対象は本体であり、流体・FE・アップグレード等の保持は追加されないため、内容入り機械を破壊する操作は避ける。

かまど端数修正は残り空き容量が1,000mB未満なら素材変換を待つ。配管抽出の最小量は変更しない。空バケツは元の条件通り燃料枠へ**1個だけ**置く。

## 復元
問題時は正常終了し、導入前のworld・mods・configをセットで戻す。JARだけのダウングレードは、旧版が保留キーを理解しないため安全な復元ではない。新形式を旧MODで開いてから再アップグレードする操作も避ける。

## English
Stop the server and back up the entire world, mods and configuration together. Keep that backup unchanged and prepare a test copy. If possible, start the copy with the old mods and configuration, drain legacy pumps containing third-party fluids, then shut it down normally. Only after shutdown, replace the previous compat JAR in the test copy, retaining the audited original mod. Test the upgrade on that copy before deciding whether to use it in production. Optional balance settings remain false by default.

Unknown legacy numeric identities are quarantined, never guessed. Schema 1 from 0.3.0 is supported. Pumps with future or malformed schemas retain their original data and stop processing, extraction and charging. Restore a missing fluid provider before reloading saved registry IDs. Mining tags do not preserve machine contents. Roll back the complete pre-upgrade world/mods/config snapshot, not just the patch JAR.
