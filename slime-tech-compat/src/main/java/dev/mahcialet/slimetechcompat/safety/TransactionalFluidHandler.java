package dev.mahcialet.slimetechcompat.safety;

import dev.wizardjw.slimetech.blockentity.FluidTankBlockEntity;
import dev.wizardjw.slimetech.blockentity.PumpBlockEntity;
import dev.wizardjw.slimetech.blockentity.SlimeTechFluidNode;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** The upstream adapter ignores TransactionContext and immediately commits every operation. */
public final class TransactionalFluidHandler implements ResourceHandler<FluidResource> {
    // A shared journal per block is essential when different sides or network members are used
    // in nested transactions. Weak values' references prevent retaining unloaded worlds.
    private static final Map<BlockEntity, FluidJournal> JOURNALS = new WeakHashMap<>();
    private final BlockEntity entity;
    private final ResourceHandler<FluidResource> delegate;

    public TransactionalFluidHandler(SlimeTechFluidNode node, ResourceHandler<FluidResource> delegate) {
        this.entity = (BlockEntity) node;
        this.delegate = delegate;
    }
    @Override public int size() { return delegate.size(); }
    @Override public FluidResource getResource(int index) { return delegate.getResource(index); }
    @Override public long getAmountAsLong(int index) { return delegate.getAmountAsLong(index); }
    @Override public long getCapacityAsLong(int index, FluidResource resource) { return delegate.getCapacityAsLong(index, resource); }
    @Override public boolean isValid(int index, FluidResource resource) { return delegate.isValid(index, resource); }

    @Override public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (!prepare(index, resource, amount, transaction)) return 0;
        return delegate.insert(index, resource, amount, transaction);
    }
    @Override public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (!prepare(index, resource, amount, transaction)) return 0;
        return delegate.extract(index, resource, amount, transaction);
    }
    private boolean prepare(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (index < 0 || index >= size()) throw new IndexOutOfBoundsException(index);
        if (amount < 0 || resource.isEmpty()) throw new IllegalArgumentException("Expected nonempty fluid and nonnegative amount");
        if (amount == 0) return false;
        // Slime Tech stores registry identity only; it cannot preserve fluid components.
        if (!resource.isComponentsPatchEmpty()) return false;
        if (entity instanceof FluidTankBlockEntity tank) {
            if (TankSafety.blocked(tank)) return false;
            var level = tank.getLevel();
            if (level == null) {
                journal(tank).updateSnapshots(transaction);
                return true;
            }
            var queue = new ArrayDeque<BlockPos>();
            var seen = new HashSet<BlockPos>();
            queue.add(tank.getBlockPos());
            seen.add(tank.getBlockPos());
            // The preceding guard established a loaded, bounded, single-fluid network.
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                if (!(level.getBlockEntity(pos) instanceof FluidTankBlockEntity member)) continue;
                journal(member).updateSnapshots(transaction);
                for (Direction direction : Direction.values()) {
                    BlockPos adjacent = pos.relative(direction);
                    if (level.hasChunkAt(adjacent) && level.getBlockEntity(adjacent) instanceof FluidTankBlockEntity && seen.add(adjacent))
                        queue.addLast(adjacent);
                }
            }
        } else if (entity instanceof PumpBlockEntity pump) {
            if (PumpSafety.quarantined(pump)) return false;
            journal(pump).updateSnapshots(transaction);
        }
        return true;
    }
    private static FluidJournal journal(BlockEntity entity) {
        return JOURNALS.computeIfAbsent(entity, FluidJournal::new);
    }
    private record Contents(int amount, String id, int code, int displayAmount, int displayCode, int displayCapacity) { }
    private static final class FluidJournal extends SnapshotJournal<Contents> {
        private final WeakReference<BlockEntity> reference;
        FluidJournal(BlockEntity entity) { reference = new WeakReference<>(entity); }
        @Override protected Contents createSnapshot() {
            BlockEntity entity = reference.get();
            if (entity instanceof TankAccess tank) {
                var block = (FluidTankBlockEntity)entity;
                block.refreshNetworkData();
                var data = block.getContainerData();
                return new Contents(tank.slimeTechCompat$getLocalAmount(), tank.slimeTechCompat$getStoredFluidId(), 0,
                        data.get(0), data.get(1), data.get(2));
            }
            if (entity instanceof PumpBlockEntity pump)
                return new Contents(pump.getFluidAmount(), "", pump.getFluidTypeCode(), 0, 0, 0);
            throw new IllegalStateException("Fluid entity removed during transaction");
        }
        @Override protected void revertToSnapshot(Contents snapshot) {
            BlockEntity entity = reference.get();
            if (entity instanceof TankAccess tank) {
                tank.slimeTechCompat$setLocalAmount(snapshot.amount());
                tank.slimeTechCompat$setStoredFluidId(snapshot.id());
                // Recomputing here would observe a partially restored network. All members
                // were snapshotted before mutation; restore their original aggregates too.
                var data = ((FluidTankBlockEntity)entity).getContainerData();
                data.set(0, snapshot.displayAmount());
                data.set(1, snapshot.displayCode());
                data.set(2, snapshot.displayCapacity());
            } else if (entity instanceof PumpBlockEntity pump) {
                pump.getContainerData().set(0, snapshot.code());
                pump.getContainerData().set(1, snapshot.amount());
            }
            if (entity != null) entity.setChanged();
        }
    }
}
