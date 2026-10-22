# 継続用引き継ぎ 0.4.0-dev

実行計画はdocs/exec-plans/slime-tech-compat-hardening.md。親repo作業ブランチwork/slime-tech-compat-hardening。commit/pushなし。依頼時の原文はルートISSUESとCHATGPT_HANDOFF_slime_tech_compatに保存。ACCEPTANCE_TESTSは試験条件を維持し、冒頭の案内とENV-09試験名を明確化した。INPUT_MANIFESTは欠落していたため実測値から作成したもの。

実Java25/NeoForge26.1.2.94/JEI29.16.0.46でビルド済み。サーバー試験は以下のassertionがすべてPASSした（JEIはクライアントのみ）。

- 元server: 97、patched: 271、options: 271、packaged: 272。
- 旧world移行と移行後の再起動: 各5。
- 追加MODありの2構成: 各271。

クライアントは、JEIありで限定したR/U導線、JEIなしを含むsingleplayer描画と保存終了を確認した。詳細と未検証範囲はdocs/VERIFICATION.mdを参照。確認済みの範囲を示したdev版として利用する。

新発見: ST-022端数増殖、元fluid adapterのTransactionContext無視。整数個変換とTank/Pump共通snapshot journalで修正。未来schema保留中充電、停止Tank表示slot、rollback表示途中集計も独立レビューを経て修正。

次はdocs/VERIFICATION.mdのNOT_RUN項目とdocs/ISSUES.mdに沿って試験を進める。主な確認対象はdirty/redstone通知副作用、大規模/未ロードネットワーク、RS/FTBIC/LTX機器間の操作、その他機械のtransaction、ST-009座標残量、ST-010塊移動。導入構成に合わせて試験結果を追加する。

再生成: ./gradlew clean build; ./gradlew runBaselineTestServer runCompatTestServer runOptionsTestServer runMigrationTestServer runMigrationReloadTestServer runPackagedTestServer; ./gradlew artifactHashes; python3 scripts/check_artifacts.py。配布はbuild/libs内のJAR・source ZIP・SHA256SUMS。本番未導入。
