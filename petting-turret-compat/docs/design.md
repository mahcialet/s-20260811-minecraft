# 設計判断: Petting ペットのタレットターゲット除外

## 調査対象

`../mods` の LTXI 1.1.3、LimaCore 2.4.0、Petting 4.2.3-beta.3 の実jarを `jar tf` と `javap -p -c` で調査した。仕様書のAPI名を実在するものと仮定しない。

1. **最小の介入点**: LTXI の `TurretBlockEntity.lambda$tickServer$0` は、ターゲット探索時に `LTXIEntityUtil.isValidContextTarget` を呼ぶ。ここでのみ早期 false を返すことで、共通utilを使う手持ち武器に影響しない。非ペットの場合はメソッド本体をそのまま実行する。
2. **テイムAPI**: 指定 Petting jar に `PetHelper` / `PetData` はない。`PettingModAttachments` に存在するのは `PET_INVENTORY` であり、`PET_DATA` ではない。実際の `GoldenWheatRightclickedProcedure.isCustomPet(Mob)` は永続データの `pettingtamed` 真偽値を判定し、UUID を参照しない。private static のため Invoker を使用し、判定そのものは Petting に委譲する。
3. **Mixinなしの可否**: 取得・継続判定は LTXI タレット BlockEntity 独自の処理で、Vanilla Mob のターゲット変更イベントでは扱えない。既存ターゲットフィルターはアップグレード更新で設定され、全タレットへ強制的に合成する公開イベントは該当経路にない。ダメージイベントだけではターゲット選択を防げず、エネルギーや射撃の副作用も残る。最小限の Mixin を採用した。
4. **取得と実射の双方**: Arc と SemiAuto（Rocket / Railgun の共通基底）は発射前に `targetStillValid` を呼ぶ。生存・射程判定だけなので、テイム判定の早期 false を追加する。既存の非ペット判定には一切条件を追加しない。
5. **取得後の状態変化**: Arc は主対象だけでなくキュー全体へ追加攻撃し、その各要素には `targetStillValid` を呼ばない。`tickServer` の先頭でテイム済み要素をキューから除去し、既存 `TurretTargetTracker` の予約を解除、`queueWatcher` を更新する。主対象は既存の継続判定と `setNextTarget` で解放される。

## 境界

所有者は保護判定に使用しない。キュー整理でタレット所有者を参照するのは LTXI の既存ターゲット予約台帳を取得するためだけで、ペット所有者との比較やオンライン確認は行わない。保存データをコピーせず、テイム判定のキャッシュも保持しない。

全対象modを必須依存とし、LTXI / Petting / LimaCore を指定版に固定する。Mixin の `required: true` と各 Inject の `require = 1` により、介入点が消えた場合は失敗を明示する。起動できたが保護されない状態を黙認しない。第三者modの同メソッドへのMixin競合は別途実機確認が必要。

## Atmos の範囲ターゲットと飛翔中の状態変化

独立レビューで、`BaseRocketEntity` の範囲攻撃が `UpgradesAwareEntity.lambda$getEntitiesInAOE$0` → `isValidContextTarget` による追加ターゲット選択であることを確認した。タレット本体だけの修正ではペットがこのターゲット集合に残るため、`this instanceof TurretRocketEntity` の場合だけ候補を除外する。

`HomingProjectileEntity.tickServer` もタレットロケットに限定し、飛翔中にテイムされた対象を次tickで解除する。1.1.3 の `setTargetEntity(null)` は何もしないため、対象参照とUUIDの両フィールドを解除する。着弾時は `TurretRocketEntity.hurtTarget` で再評価する。このメソッドは直接・範囲攻撃の両方、および発射元タレット撤去後のダメージ経路を処理するため、テイムしたペットへの衝突も防げる。いずれも非ペットは元の本体を実行し、手持ち武器のロケットには条件を適用しない。
