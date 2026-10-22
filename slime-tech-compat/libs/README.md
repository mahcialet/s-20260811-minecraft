# 実依存物を使うビルド / Genuine dependency build

元MOD・JEIのJARは利用者が用意します。Java 25と同梱Gradle wrapper（9.6.1）を使用し、次のJARをプロジェクトルートから見た `../mods/` に配置してください。

- `slime_tech-0.1.44.jar`（[README](../README.md)記載のSHA-256）
- `jei-26.1.2-neoforge-29.16.0.46.jar`

NeoForge **26.1.2.94** はModDevGradleが取得します。プロジェクトルートで `./gradlew build policyTest` を実行します。この版での実依存ビルドと本物のMixin適用は検証済みです。クライアント・サーバー試験の実施範囲と未検証事項は[検証記録](../docs/VERIFICATION.md)を参照し、導入前にコピーしたワールドで確認してください。NeoForge版を変更する場合は、依存設定と厳密な版チェックを見直したうえで再検証が必要です。

参考0.3.0はAPIスタブによる暫定ビルドで、実依存ビルドは未実施でした。この過去の状態と、現在の0.4.0-devの検証結果を区別してください。

Supply your own original mod and JEI JARs. Use Java 25 and the bundled Gradle 9.6.1 wrapper. Place the two JARs above in `../mods/`, relative to the project root. ModDevGradle downloads exactly NeoForge **26.1.2.94**. Run `./gradlew build policyTest` from the project root. Genuine-dependency compilation and real Mixin application have passed for this version; see the [verification record](../docs/VERIFICATION.md) for the tested scope and outstanding checks. Test a world copy before deployment. Changing NeoForge requires reviewing the dependency settings and strict version checks, followed by revalidation.

The reference 0.3.0 used API stubs and had not been built against genuine dependencies. That historical limitation does not describe the current 0.4.0-dev build results.
