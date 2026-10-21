# Petting Turret Compat

Petting でテイムした Mob を、所有者に関係なく LTX Industries のタレットのターゲットから除外する NeoForge mod です。Ionos / Arc、Atmos / Rocket、Noctis / Railgun に対応します。野生の同種 Mob は、既存のターゲット判定で扱います。

## 対象と依存関係

| 項目 | バージョン |
| --- | --- |
| Minecraft / Java | 26.1.2 / 25 |
| NeoForge | 26.1.2.71 以上、26.2 未満 |
| LTX Industries | 1.1.3（必須・固定） |
| LimaCore | 2.4.0（必須・固定） |
| Petting | 4.2.3-beta.3（必須・固定） |
| Grimoire of Gaia 4 | 7.0.0-alpha.2（再現・検証用、互換modの必須依存ではない） |

LTXI / Petting がない場合は依存関係エラーとしてロードを拒否します。内部メソッドに依存するため、対応する依存modのバージョンを固定しています。Mixin は必須で、対象の構造が変わった場合は起動時に失敗します。

## ビルド

Java 25 を選択し、次の4つの依存jarを `../mods` に配置してください。

```text
ltxi-26.1.2-1.1.3.jar
limacore-26.1.2-2.4.0.jar
petting-4.2.3-beta.3-neoforge-26.1.2.jar
GrimoireOfGaia4-26.1.2-7.0.0-alpha.2.jar
```

配置後、このディレクトリで `./gradlew build` を実行します。成果物は `build/libs/petting_turret_compat-1.0.0.jar` です。依存modは別途用意し、専用サーバーと各クライアントの `mods` に互換modと合わせて配置してください。開発環境は `./gradlew runClient` / `./gradlew runServer` で起動できます。

## 判定仕様

- Petting がテイム済みと判定する Mob は、タレット設置者、ペット所有者、所有者のオンライン状態を問わず除外します。
- 野生 Mob、プレイヤー、その他の Entity は LTXI の既存判定を維持します。
- Vanilla の `OwnableEntity` / `TamableAnimal` に対する既存判定を維持します。
- All Entities / Neutral Enemy / Hostile Only（Extermination を含む各設定）は、Petting ペット以外には既存の判定を適用します。
- 取得済みのターゲットと待機キューも再評価します。キュー投入後のテイムは次のタレットサーバーtickで反映されます。

Atmos の範囲攻撃でもペットを除外します。発射後にテイムされた場合は次のロケットtickで追尾を解除し、着弾時にも直接・範囲ダメージを防ぎます。ロケット自体は残り、非ペットへの挙動は維持します。

テイム判定は Petting 自身の処理に委譲します。使用する API と Mixin の介入箇所は [設計理由](docs/design.md) に記載しています。

## 制約と検証

保護対象は Mob の種類ではなく、Petting のテイム判定で決まります。保護は LTX Industries のタレットによる対象選択と攻撃に適用し、他modのダメージ、手持ち武器、Petting のAI・combat mode、タレットの敵対分類は従来の動作を維持します。

### 自動テスト

`./gradlew runCompatTestServer` で実サーバー上の自動テストを実行します。`build` とは別に実行する必要があります。

専用の `run-compat-tests` にローカル検証ワールドを作成し、テスト後にサーバーを停止します。Gradleタスクは終了コードに加えて今回の `PASS assertions=N` を検査し、テストが未実行または失敗した場合も失敗します。テスト用modは検証時にのみロードします。配布jarには本modを収録します。

### 手動テスト

ゲーム内での操作とマルチプレイでの実射は、[手動検証手順](docs/manual-test.md) に沿って確認してください。

ライセンス: MIT（[LICENSE](LICENSE)）。依存modのライセンスは各配布元に従います。
