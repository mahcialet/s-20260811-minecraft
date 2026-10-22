package dev.mahcialet.slimetechcompat.safety;

/** Read-only access to per-block contents. Never use aggregate getters for validation. */
public interface TankAccess {
    int slimeTechCompat$getLocalAmount();
    String slimeTechCompat$getStoredFluidId();
    void slimeTechCompat$setLocalAmount(int amount);
    void slimeTechCompat$setStoredFluidId(String id);
}
