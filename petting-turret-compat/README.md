# Petting Turret Compat

Petting でテイムした Mob を、所有者に関係なく LTX Industries のタレットのターゲットから除外する NeoForge mod です。Ionos / Arc、Atmos / Rocket、Noctis / Railgun に対応します。野生の同種 Mob は除外せず、既存のターゲット判定へ渡します。

## 対象と依存関係

| 項目 | バージョン |
| --- | --- |
| Minecraft / Java | 26.1.2 / 25 |
| NeoForge | 26.1.2.71 以上、26.2 未満 |
| LTX Industries | 1.1.3（必須・固定） |
| LimaCore | 2.4.0（必須・固定） |
| Petting | 4.2.3-beta.3（必須・固定） |
| Grimoire of Gaia 4 | 7.0.0-alpha.2（再現・検証用、互換modの必須依存ではない） |

LTXI / Petting がない場合は依存関係エラーとしてロードを拒否します。内部メソッドに依存するため、別バージョンを黙って許可しません。Mixin は必須で、対象の構造が変わった場合は起動時に失敗します。

## ビルド

Java 25 を選択して、このディレクトリで `./gradlew build` を実行します。指定の4つの依存jarを `../mods` に置いてください。

```text
ltxi-26.1.2-1.1.3.jar
limacore-26.1.2-2.4.0.jar
petting-4.2.3-beta.3-neoforge-26.1.2.jar
GrimoireOfGaia4-26.1.2-7.0.0-alpha.2.jar
```

成果物は `build/libs/petting_turret_compat-1.0.0.jar` です。依存modは同梱しません。専用サーバーと各クライアントの `mods` に互換modと必要な依存modを配置してください。開発環境は `./gradlew runClient` / `./gradlew runServer` で起動できます。

## 判定仕様

- Petting がテイム済みと判定する Mob は、タレット設置者、ペット所有者、所有者のオンライン状態を問わず除外します。
- 野生 Mob、プレイヤー、その他の Entity は LTXI の既存判定を維持します。
- Vanilla の `OwnableEntity` / `TamableAnimal` に対する既存判定は置き換えません。
- All Entities / Neutral Enemy / Hostile Only（Extermination を含む各設定）の意味は、Petting ペット以外について変更しません。
- 取得済みのターゲットと待機キューも再評価します。キュー投入後のテイムは次のタレットサーバーtickで反映されます。

指定 Petting jar には `PetHelper`、`PetData`、`PET_DATA` は存在しません。代わりに、Petting 自身の `GoldenWheatRightclickedProcedure.isCustomPet(Mob)` を Mixin Invoker で呼び出します。UUID や保存構造の独自実装はありません。LTXI 側は `TurretBlockEntity` の候補抽出、継続判定、待機キューの管理と、タレットロケットの範囲選択・追尾・着弾に介入します。[設計理由](docs/design.md)を参照してください。

## 制約と検証

Atmos の範囲攻撃もペットを除外します。発射後にテイムされた場合は次のロケットtickで追尾を解除し、着弾時にも直接・範囲ダメージを防ぎます。ロケット自体を消去するわけではなく、非ペットへの挙動は維持します。他modのダメージ、手持ち武器、Petting のAI・combat mode、タレットの敵対分類は変更しません。Nine Tails に限定したブラックリストもありません。

実機の確認項目と手順は [手動検証手順](docs/manual-test.md) に記載しています。コンパイル成功とマルチプレイでの実射確認は別の検証として扱ってください。

自動の実サーバー検証は `./gradlew runCompatTestServer` で実行します。専用の `run-compat-tests` にローカル検証ワールドを作成し、テスト後に停止します。終了コードだけでなく今回の `PASS assertions=N` を検査し、未実行や失敗はGradleタスクも失敗させます。テスト用modは配布jarに入りません。`build` だけではこの実行時検証は実施されません。

ライセンス: MIT（[LICENSE](LICENSE)）。依存modのライセンスは各配布元に従います。
