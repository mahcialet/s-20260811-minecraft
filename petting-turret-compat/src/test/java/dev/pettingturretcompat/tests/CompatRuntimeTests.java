package dev.pettingturretcompat.tests;

import dev.pettingturretcompat.PettingProtection;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import liedge.ltxindustries.blockentity.turret.*;
import liedge.ltxindustries.entity.LTXIEntityUtil;
import liedge.ltxindustries.entity.TargetPredicate;
import liedge.ltxindustries.entity.TurretRocketEntity;
import liedge.ltxindustries.entity.EquipmentRocketEntity;
import liedge.ltxindustries.entity.UpgradesAwareEntity;
import liedge.ltxindustries.entity.HomingProjectileEntity;
import liedge.ltxindustries.registry.game.LTXIEntities;
import liedge.ltxindustries.lib.TurretTargetTracker;
import liedge.ltxindustries.registry.game.LTXIBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Dedicated test mod: exercises transformed dependency classes in a real server world. */
@Mod("petting_turret_compat_tests")
public final class CompatRuntimeTests {
    private static int assertions;

    public CompatRuntimeTests() {
        NeoForge.EVENT_BUS.addListener(this::started);
    }

    private void started(ServerStartedEvent event) {
        try {
            run(event.getServer().overworld());
            Files.writeString(Path.of("compat-test-result.txt"), "PASS assertions=" + assertions + "\n");
            System.out.println("COMPAT_RUNTIME_TESTS_PASS assertions=" + assertions);
        } catch (Throwable failure) {
            failure.printStackTrace();
            throw new AssertionError("COMPAT_RUNTIME_TESTS_FAILED", failure);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void run(ServerLevel level) throws Exception {
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TurretOwner"));
        var other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "OtherOwner"));
        Mob wild = nineTails(level);
        Mob pet = nineTails(level);
        check(!PettingProtection.isProtected(wild), "wild Nine Tails must not be protected");
        check(!PettingProtection.isProtected(null), "null safety");
        check(!PettingProtection.isProtected(owner), "players are not Petting mobs");

        BlockPos pos = new BlockPos(0, 100, 0);
        TurretBlockEntity[] turrets = {
            new ArcTurretBlockEntity(pos, LTXIBlocks.ARC_TURRET.get().defaultBlockState()),
            new RocketTurretBlockEntity(pos, LTXIBlocks.ROCKET_TURRET.get().defaultBlockState()),
            new RailgunTurretBlockEntity(pos, LTXIBlocks.RAILGUN_TURRET.get().defaultBlockState())
        };
        Method candidate = method("lambda$tickServer$0", Player.class, TurretTargetTracker.class, Entity.class);
        Method active = method("targetStillValid", Entity.class);
        Method tick = method("tickServer", ServerLevel.class, BlockPos.class, BlockState.class);
        Field filter = field("targetFilter");
        Wolf wolf = EntityType.WOLF.create(level, EntitySpawnReason.COMMAND);
        wolf.setTame(true, false);
        wolf.setOwner(owner);
        wolf.setPos(1, 100, 1);
        wild.setPos(1, 100, 1);
        pet.setPos(1, 100, 1);

        for (TurretBlockEntity turret : turrets) {
            turret.setLevel(level);
            turret.setOwnerUUID(owner.getUUID());
            // Constructor initializes the real queue watcher through LimaCore.
            check((boolean) active.invoke(turret, wild), "wild active target remains valid");
            for (UUID petOwner : new UUID[] { owner.getUUID(), other.getUUID(), UUID.randomUUID() }) {
                tame(pet, petOwner);
                check(PettingProtection.isProtected(pet), "same/different/offline owner protected");
                check(!(boolean) candidate.invoke(turret, owner, TurretTargetTracker.EMPTY, pet), "candidate rejected");
                check(!(boolean) active.invoke(turret, pet), "active target rejected");
            }
            // The original context validator is an unmodified baseline: neither global
            // utility nor protocol semantics may be replaced by the compat mixin.
            TargetPredicate original = (TargetPredicate) filter.get(turret);
            if (turret instanceof ArcTurretBlockEntity) {
                check((boolean) candidate.invoke(turret, owner, TurretTargetTracker.EMPTY, wild),
                        "default Ionos filter accepts wild Nine Tails");
            }
            check(!LTXIEntityUtil.isValidContextTarget(wolf, owner, TargetPredicate.ALL),
                    "original LTXI protects owner's vanilla wolf");
            for (TargetPredicate protocol : new TargetPredicate[] { original, TargetPredicate.ALL,
                    (world, target, context) -> false }) {
                filter.set(turret, protocol);
                for (Entity entity : new Entity[] { wild, wolf, owner }) {
                    boolean expected = LTXIEntityUtil.isValidContextTarget(entity, owner, protocol);
                    check((boolean) candidate.invoke(turret, owner, TurretTargetTracker.EMPTY, entity) == expected,
                            "non-Petting candidate delegates to original LTXI validator");
                }
                check(!(boolean) candidate.invoke(turret, owner, TurretTargetTracker.EMPTY, pet),
                        "Petting protection overrides every protocol");
                if (protocol == TargetPredicate.ALL) {
                    check((boolean) candidate.invoke(turret, owner, TurretTargetTracker.EMPTY, wild),
                            "all-entities filter accepts wild Nine Tails");
                }
            }
            check(!PettingProtection.isProtected(wolf), "vanilla tame state not mistaken for Petting state");
            filter.set(turret, original);
            pet.getPersistentData().putBoolean("pettingtamed", false);
            turret.getTargetQueue().add(wild);
            turret.getTargetQueue().add(pet);
            tame(pet, other.getUUID());
            tick.invoke(turret, level, pos, turret.getBlockState());
            check(!turret.getTargetQueue().contains(pet), "newly tamed queue member removed before firing");
            check(turret.getTargetQueue().contains(wild), "queue pruning preserves wild members");
        }
        rockets(level, owner, wild, pet);
    }

    private static void rockets(ServerLevel level, Player owner, Mob wild, Mob pet) throws Exception {
        TurretRocketEntity rocket = LTXIEntities.TURRET_ROCKET.get().create(level, EntitySpawnReason.COMMAND);
        EquipmentRocketEntity handheld = LTXIEntities.DAYBREAK_ROCKET.get().create(level, EntitySpawnReason.COMMAND);
        Method area = UpgradesAwareEntity.class.getDeclaredMethod("lambda$getEntitiesInAOE$0",
                LivingEntity.class, Entity.class, Entity.class);
        area.setAccessible(true);
        Field predicate = UpgradesAwareEntity.class.getDeclaredField("predicate");
        predicate.setAccessible(true);
        predicate.set(rocket, TargetPredicate.ALL);
        predicate.set(handheld, TargetPredicate.ALL);
        check(!(boolean) area.invoke(rocket, owner, null, pet), "turret rocket excludes pet from splash selection");
        check((boolean) area.invoke(rocket, owner, null, wild), "turret rocket still selects wild splash target");
        for (Entity target : new Entity[] { wild, pet }) {
            boolean expected = LTXIEntityUtil.isValidContextTarget(target, owner, TargetPredicate.ALL);
            check((boolean) area.invoke(handheld, owner, null, target) == expected,
                    "handheld rocket splash retains original context validator");
        }
        check(!(boolean) area.invoke(rocket, owner, wild, wild), "excluded direct victim still excluded from splash");
        Method homing = HomingProjectileEntity.class.getDeclaredMethod("tickServer", ServerLevel.class, LivingEntity.class);
        homing.setAccessible(true);
        rocket.setTargetEntity(pet);
        homing.invoke(rocket, level, owner);
        check(rocket.getTargetEntity() == null, "turret rocket releases newly tamed homing target");
        homing.invoke(rocket, level, owner);
        check(rocket.getTargetEntity() == null, "released homing target remains absent next tick");
        Field targetId = HomingProjectileEntity.class.getDeclaredField("targetUUID");
        targetId.setAccessible(true);
        check(targetId.get(rocket) == null, "released homing target UUID cannot reacquire pet");
        rocket.setTargetEntity(wild);
        homing.invoke(rocket, level, owner);
        check(rocket.getTargetEntity() == wild, "turret rocket retains wild homing target");
        handheld.setTargetEntity(pet);
        homing.invoke(handheld, level, owner);
        check(handheld.getTargetEntity() == pet, "handheld homing behavior unchanged");
        Method impact = TurretRocketEntity.class.getDeclaredMethod("hurtTarget", ServerLevel.class,
                Entity.class, LivingEntity.class, Vec3.class, boolean.class);
        impact.setAccessible(true);
        // No launching turret exists: this also covers the orphaned-projectile fallback.
        float health = pet.getHealth();
        impact.invoke(rocket, level, pet, owner, Vec3.ZERO, true);
        impact.invoke(rocket, level, pet, owner, Vec3.ZERO, false);
        check(pet.getHealth() == health, "orphaned turret rocket direct and splash impact cannot damage pet");
    }

    private static Mob nineTails(ServerLevel level) {
        var key = Identifier.parse("grimoireofgaia:nine_tails");
        check(BuiltInRegistries.ENTITY_TYPE.containsKey(key), "Nine Tails registry entry exists");
        return (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(key).create(level, EntitySpawnReason.COMMAND);
    }

    // Test fixture for the exact supplied Petting build's saved state, not production logic.
    private static void tame(Mob mob, UUID owner) {
        mob.getPersistentData().putBoolean("pettingtamed", true);
        mob.getPersistentData().putString("ownerUUID", owner.toString());
    }

    private static Method method(String name, Class<?>... args) throws Exception {
        Method result = TurretBlockEntity.class.getDeclaredMethod(name, args);
        result.setAccessible(true);
        return result;
    }

    private static Field field(String name) throws Exception {
        Field result = TurretBlockEntity.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        assertions++;
    }
}
