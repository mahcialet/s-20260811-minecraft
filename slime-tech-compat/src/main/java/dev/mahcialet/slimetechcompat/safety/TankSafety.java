package dev.mahcialet.slimetechcompat.safety;

import dev.mahcialet.slimetechcompat.CompatConfig;
import dev.wizardjw.slimetech.blockentity.FluidTankBlockEntity;
import dev.wizardjw.slimetech.blockentity.SlimeTechFluidType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class TankSafety {
    private static final System.Logger LOG = System.getLogger("SlimeTechCompat");
    // This tracks warnings only, not validity. Revalidate every access to avoid stale same-tick caches.
    private static final Map<FluidTankBlockEntity, String> WARNED = Collections.synchronizedMap(new WeakHashMap<>());
    private TankSafety() { }
    public static boolean blocked(FluidTankBlockEntity start) {
        TankPolicy.Decision decision = inspect(start);
        Level level = start.getLevel();
        if (level != null && !level.isClientSide()) {
            if (decision.blocked()) {
                String previous = WARNED.put(start, decision.reason());
                if (!decision.reason().equals(previous)) LOG.log(System.Logger.Level.WARNING,
                        "Tank transfers blocked at " + start.getBlockPos() + ": " + decision.reason()
                        + ". Local contents are preserved; separate the tanks and retry.");
            } else if (WARNED.remove(start) != null) {
                LOG.log(System.Logger.Level.INFO, "Tank transfer guard cleared at " + start.getBlockPos());
            }
        }
        return decision.blocked();
    }
    public static TankPolicy.Decision inspect(FluidTankBlockEntity start) {
        Level level = start.getLevel();
        // Only the server has authoritative config and network chunk visibility.
        // Client getters/refresh must retain the upstream synchronized display behavior.
        if (level != null && level.isClientSide()) return TankPolicy.Decision.allow();
        ArrayList<TankPolicy.Contents> contents = new ArrayList<>();
        if (level == null) {
            contents.add(local(start));
            return TankPolicy.assess(contents, TankSafety::canonicalize);
        }
        int limit = CompatConfig.get().maxTankNetworkMembers();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<BlockPos> seen = new HashSet<>();
        queue.add(start.getBlockPos());
        seen.add(start.getBlockPos());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!level.hasChunkAt(pos)) return TankPolicy.Decision.deny("network reaches an unloaded chunk");
            if (!(level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank)) continue;
            contents.add(local(tank));
            if (contents.size() > limit) return TankPolicy.Decision.deny("network exceeds " + limit + " tanks");
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = pos.relative(direction);
                if (seen.contains(adjacent)) continue;
                // Do not ask the original unbounded traversal to load an unseen neighboring chunk.
                if (!level.hasChunkAt(adjacent)) return TankPolicy.Decision.deny("neighboring chunk is not loaded");
                if (level.getBlockEntity(adjacent) instanceof FluidTankBlockEntity) {
                    seen.add(adjacent);
                    if (seen.size() > limit) return TankPolicy.Decision.deny("network exceeds " + limit + " tanks");
                    queue.addLast(adjacent);
                }
            }
        }
        return TankPolicy.assess(contents, TankSafety::canonicalize);
    }
    private static TankPolicy.Contents local(FluidTankBlockEntity tank) {
        if (!(tank instanceof TankAccess access)) {
            throw new IllegalStateException("Slime Tech Compat tank accessor mixin was not applied");
        }
        return new TankPolicy.Contents(access.slimeTechCompat$getLocalAmount(), access.slimeTechCompat$getStoredFluidId());
    }
    private static String canonicalize(String id) {
        int code = SlimeTechFluidType.fromIdentifierString(id);
        return code == 0 ? "" : SlimeTechFluidType.toIdentifierString(code);
    }
}
