# Slime Tech Compat 実行計画

2026-10-22。対象: Minecraft 26.1.2 / slime_tech 0.1.44 (SHA-256 f8ea80558f7f229314fa026311c194aa956f773bc235a76d7598b0a26d3030bb)。

## 現状
- 親Gitリポジトリのmasterから work/slime-tech-compat-hardening を作成。入力フォルダは未追跡。既存変更を保持する。
- 適用指示はユーザー提供AGENTS.mdとRTK.md。祖先・対象内に追加AGENTS.mdなし。
- INPUT_MANIFEST.json は提供されていない。指定元JARハッシュは引き継ぎ本文と一致。参考ZIPの実測は b5a7d203ff0da06e89af1e1ab2074a48613d6ba5f0784f663757b6efdfe14195（事前期待値未提供）。
- 参考0.3.0を展開。既存検証記録は過去のモデル試験としてのみ扱う。

## 順序・担当・検証
1. 実依存ビルド担当: Java 25 / NeoForge 26.1.2.94 / JEI 29.16.0.46 を確認しwrapper・ビルド・クライアント分離を整備。実APIのコンパイル、JAR内容を確認。
2. データ保全監査担当: 元JARとTank/Pumpを照合し保存・混在・保留・将来schemaの問題を監査。独立した根拠を報告。
3. 統合担当: 実Mixinと専用サーバーを起動し、ST-022を再現。必要な修正と自動試験を追加。採掘タグとJEI導線を照合。
4. 任意設定OFF/ON、保存再読込、正常系と不正データ、JEIなしを検証。環境で実行不能なUI/他MOD試験はNOT_RUNとして理由・手順を残す。
5. 日英文書、問題別状況、移行・復元、検証manifest、JAR/ソースZIP/ハッシュ、CIを整備。最終差分と同梱物を点検。

## 制約と判断
成果物は独立パッチのclass・リソースで構成する。保存内容は原値を保持し、流体を特定できないデータは保留する。バランス設定は既定OFF。実APIコンパイル・実Mixin変換・統合動作・UIの結果をそれぞれ記録する。試験は隔離ワールドで行い、成果物はローカルに準備する。公開・push・本番導入は今回の作業範囲外とする。

## 完了条件
必須BUILD-001、ST-001〜006、PATCH-001と、再現時ST-022の修正・証跡。未検証事項は受入済みとせずpre-releaseに残す。

## 進捗・発見
- 実API build/policy33、実Mixin入りJEIなしserverに到達。baselineでST-022端数増殖を再現、整数個変換で修正。
- Tank/Pump保存保全を強化。独立レビューでfuture schema保留中の充電消失を確認してガード追加。
- 実Capability Transaction.abort試験で元adapterがtransactionを無視する欠陥を検出。ST-004/005の必須データ保全に属するため、実adapterをtransaction対応する範囲へ拡張して修正・再検証する。
- Casting試験の初稿は流体番号4を誤使用、実定数CONDENSED_SLIME=3へ修正。元コードは冷却200tick後の次tickで完成するため、試験を実観測に合わせ修正（製法変更なし）。
- 元バケツ処理は既に1個限定。レビューで冗長redirectを指摘され削除した。
- 元world→patch移行・再起動、配布JAR経由、追加MOD構成、可能なクライアント起動へ進行。

## 最終到達点
実依存clean build成功。baseline97、patched/options271、packaged272、移行/再起動各5、OreSlimes/複数MOD共存各271 assertions PASS。実JEI GelIngot RとMixer U、JEIなしclientの読込・正常終了も確認。独立レビューP2は採用・再試験済み。完全な受入ではなく0.4.0-devとして配布準備し、NOT_RUNと元MOD未修正項目をVERIFICATION/ISSUESに保持。commit/push/本番導入なし。
