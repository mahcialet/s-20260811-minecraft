# 実装対象 0.4.0-dev

元JARはINPUT_MANIFEST.jsonに記載する指定SHA-256に限定。実検証の根拠は、今回の実依存ビルドとNeoForge上のMixin・動作試験。参考0.3.0の独自Weaver・スタブ結果は過去のモデル試験として参照する。

|問題|実装|
|---|---|
|ST-001|mineable/pickaxe replace:falseで9機械追加|
|ST-002/003/011/012|表示専用JEIカテゴリと日英情報。Ore Slimesの両素材が存在する場合にRadioactiveレシピを登録|
|ST-004/015|TankPolicy/TankSafetyで全連結群を毎アクセス検査。FluidTankMixinで異種・不明・不正量・探索上限・未ロードを検出した場合は流体転送を停止し、安全に集計できない外部向けの値を抑止する。local保存内容を保持する|
|ST-004/005 transaction|FluidCapabilityMixinでTank/Pumpのadapterのみwrapper化。BE共有SnapshotJournalで量・ID・ContainerDataをrollback|
|ST-005/006|PumpSafetyとPumpMixinでschema1の安定ID保存、旧不明番号保留、源変更時buffer種類保持。未知schema原入力保存|
|ST-007/008|任意設定OFF維持、ON時水以外源拒否/Grinder初期0・完了必要120FE|
|ST-022|FurnaceMixinはprocessFurnaceOutputの保存insert1箇所だけ整数個単位化|
|PATCH-001|実依存、実Mixin、JEI分離、実行時元JARhash検査、厳密NeoForge版|

Minecraft26.1.2にはJava25を使用。ModDevGradle2.0.148/Gradle9.6.1。変換はNeoForgeが供給する本物のSponge Mixin0.8.7。テストはsrc/testの専用modで実行し、逆アセンブル結果はbuild内で解析に使用する。配布JARは独立パッチのclass/assetsで構成する。

テスト区分と実績はVERIFICATION.md、保留・復元手順はMIGRATION.md、判断理由はADR.md。
