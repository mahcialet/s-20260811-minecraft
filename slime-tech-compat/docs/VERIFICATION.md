# 実環境検証 — 0.4.0-dev

2026-10-22。**実依存ビルド・サーバー試験・一部クライアント操作を確認した開発版です。** 下表に実績を、後半に次の受け入れ試験を記載します。 下記は本物の依存物・Mixin・ゲームで実行した範囲です。参考0.3.0のスタブ/独自Weaver結果は合算しません。

## 固定環境
Java Corretto25.0.3、Gradle9.6.1(wrapper SHA固定)、ModDev2.0.148、Minecraft26.1.2、NeoForge26.1.2.94、Sponge Mixin0.8.7、JEI29.16.0.46。元JARは指定SHA-256一致。INPUT_MANIFEST.jsonは未提供だったため今回の実測inventoryとして新規作成し、その由来を明記。reference ZIPの期待hashは事前未提供。

隔離専用サーバーはlocalhost/port0/offline、view2、flat、overworldの(8,100,8)周辺を使用。既存プレイヤーワールドは変更していない。移行fixtureは(10,100,10) Pumpと(11,100,10) Tank。ワールドseedは固定していないがテストは地形と独立した指定座標に設置する。configはrunごとに明示生成する。OFF構成は `blockNonWaterWorldSources=false`、`fixGrinderEnergy=false`、`maxTankNetworkMembers=512`。ON構成は `blockNonWaterWorldSources=true`、`fixGrinderEnergy=true`、`maxTankNetworkMembers=512`。

## 実行済み

|検証|コマンド・証跡|結果|
|---|---|---|
|実API build / ENV-02|`./gradlew clean build`、current-clean-build.txt|PASS（最終クリーンビルド）|
|純粋policy/config|`./gradlew policyTest`（buildのcheckにも接続）|PASS 33 assertions。設定不正値・未知キー・既定値・ファイル非上書き等|
|元MOD比較|`./gradlew runBaselineTestServer`|PASS 97 assertions。パッチ/JEIなし。端数増殖・Pump源変更再ラベルを再現|
|実Mixin・最小server / ENV-04,05|`./gradlew runCompatTestServer`|PASS 271 assertions、JEIなし、保存・終了正常|
|任意設定ON|`./gradlew runOptionsTestServer`|PASS 271 assertions|
|元world移行|`./gradlew runMigrationTestServer`|PASS 5 assertions。元MOD保存worldをコピーし旧動的Pump保留、量1234/FE777/旧番号1007、Tank水4321を確認|
|移行後の再起動|`./gradlew runMigrationReloadTestServer`|PASS 5 assertions。同worldをコピーし直さず再読込|
|配布JARロード / ENV-03|`./gradlew runPackagedTestServer`|PASS 272 assertions。main開発出力を除き生成JARからクラスロードしたことも検査|
|Ore Slimes追加|`./gradlew runCompatTestServer -PcompatExtraMods=oreslimes`|PASS 271 assertions|
|複数MOD共存|`./gradlew runCompatTestServer -PcompatExtraMods=oreslimes,refinedstorage,ftbic,ltxi`|PASS 271 assertions。LimaCore自動追加。共存起動とパッチ回帰を確認。各MOD機器間転送は後半の受け入れ試験で扱う|
|JEIあり実client / ENV-06一部|独立Xvfb、`./gradlew runClient -PcompatQuickPlayWorld=compat-ui`|PASS 世界読込・Gel Ingot検索R→鋳型カテゴリ・1000mB tooltip・Mixer検索U→カテゴリ、正常保存終了|
|JEIなし実client / ENV-07|`./gradlew runClient -PcompatWithoutJei=true -PcompatQuickPlayWorld=compat-ui-nojei`|PASS 別コピーworld読込・inventory表示・正常保存終了|
|JAR/source同梱物|`python3 scripts/check_artifacts.py`|PASS（独立class/assets、スタブ/元class/試験classなし。source中JARはwrapperのみ）|

自動検証はcheck→policyTestと、明示した各NeoForge server runで実行する。JUnit試験を持たない`test`タスクの結果はSKIPPED。

各current-*.txtは[evidence](evidence/)の今回ログ抽出。完全ログはローカルbuildと各run/logs。スクリーンショットはcurrent-client-*.png。XvfbではX11カーソル形状65547警告が出たが、世界・JEI描画と操作は継続し正常終了した。

## 主な実アサーション
- ST-001: 全9機械の採掘タグ、diamond pickaxe適正判定、本体1個loot。
- ST-002: 1000mB投入simulation・実投入、199tick未完成、200tick冷却0、次tick Ingot1。JEIの量と一致。
- ST-004/005: 異種Tankをsimulation/commitしても量・ID不変、正常入出力、負/過大量と未知IDを実NBTで複数回保存。実Capabilityのabort/commit、複数Tank・異なるhandlerのnested transaction、全量Pump抽出abortで量・ID復元。停止GUIとrollback後全member表示も比較。
- ST-005: 旧動的ID、schema1、未来99・未知nested key、型衝突の複数回保存。未来schema保留中の充電拒否。
- ST-006/007: 源撤去と溶岩→水変更時のbuffer、通常水100mB/10FE、任意非水拒否。
- ST-008: OFF/ON、新品、0/14/15/119/120/2000FE、完成120FEと保存電力保持。
- ST-022: 7000/7500/7999/8000mB ×素材1/2/64×バケツ0/1/2。元抽出APIで7500到達、同tick再処理、素材消費×1000と保存量/バケツの収支。

<a id="実機で未実施の受け入れ試験"></a>

## 次に実施する受け入れ試験
以下の項目は現在 **NOT_RUN**。コピーしたワールドで次の操作を行い、各項目の結果を記録する。

- ENV-06残り: JEIありclientから専用serverへ参加・退出・再参加。今回UIはsingleplayerの試験world。
- Tankのサーバー判定: clientの`maxTankNetworkMembers`をserverより小さく設定し、clientだけ連結群の一部チャンクが未ロードの状態でも、正常なserver側転送とGUI表示が維持されること。異種流体を混在させた場合はserver側の転送停止とGUI同期を確認する。安全判定はserver側に限定済みだが、このmultiplayer表示試験は未実施。
- ENV-08: 改変同版JAR/版不一致を実際に入れた負の起動試験（build/runtime照合は実装済み、正しいJARでの起動を確認）。
- サバイバル初期資源から初回Gel Ingotまでの全手操作。全機械の採掘速度・不適正ツール・内容入り回収再設置。
- JEI日本語切替、全情報ページの文字はみ出し、Ore SlimesありRadioactive R/Uの描画、全Mixer投入条件の実tick matrix。
- 独自流体の登録順変更、提供MOD削除/復帰を挟む実プロセス試験。既存本番worldそのものは未使用。
- Tank未ロード境界・513台/設定上限・大規模性能。abort時のdirty/redstone通知等の副作用。保存量/ID/表示の復元は確認済み。ここでは通知など、外部から観測できる副作用の有無を確認する。
- RS Importer/Exporter、FTBIC Geothermal、LTX設備間の実配管/電力操作。対象外機械・Energy capabilityのtransaction。
- ST-009/010/013〜023の未修正箇所（ST-022を除く）の全動作、組込OPコマンド、MSPT測定。対象と判断事項は[問題別状況](ISSUES.md)を参照する。
- GitHub CI実行（workflowは追加、ローカル等価コマンドを実行）。

記録手順: [ACCEPTANCE_TESTS.md](../ACCEPTANCE_TESTS.md)の該当項目に従い、ID・config・構成・実測・証跡を追記する。CIでは元JARを用意してfull検証jobを実行する。用意できない場合の結果はNOT_RUNとし、skip理由を記録する。
