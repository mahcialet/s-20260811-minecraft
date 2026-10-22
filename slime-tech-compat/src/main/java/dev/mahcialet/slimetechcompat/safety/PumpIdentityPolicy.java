package dev.mahcialet.slimetechcompat.safety;

import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/** Legacy dynamic IDs must never be resolved using a new session's integer mapping. */
public final class PumpIdentityPolicy {
    public record Result(int runtimeCode, String stableId, int unresolvedCode, boolean quarantined) { }
    private PumpIdentityPolicy() { }
    public static Result restore(int amount, int legacyCode, String stableId, int unresolvedCode,
                                 ToIntFunction<String> resolve, IntFunction<String> builtinId) {
        String id = stableId == null ? "" : stableId;
        int diagnosticCode = unresolvedCode != 0 ? unresolvedCode : legacyCode;
        if (amount == 0) return new Result(0, "", 0, false);
        if (amount < 0 || amount > 4000) return new Result(0, id, diagnosticCode, true);
        if (!id.isBlank()) {
            int mapped = resolve.applyAsInt(id);
            return mapped > 0 ? new Result(mapped, id, 0, false)
                    : new Result(0, id, diagnosticCode, true);
        }
        // Only these four codes have stable meanings in 0.1.44.
        if (legacyCode >= 1 && legacyCode <= 4 && unresolvedCode == 0) {
            id = builtinId.apply(legacyCode);
            if (id != null && !id.isBlank()) return new Result(legacyCode, id, 0, false);
        }
        return new Result(0, "", diagnosticCode, true);
    }
}
