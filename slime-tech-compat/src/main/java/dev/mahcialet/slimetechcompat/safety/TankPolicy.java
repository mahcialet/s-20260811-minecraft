package dev.mahcialet.slimetechcompat.safety;

import java.util.List;
import java.util.function.Function;

/** Pure validation: a failure never redistributes or erases contents. */
public final class TankPolicy {
    public record Contents(int amount, String fluidId) { }
    public record Decision(boolean blocked, String reason) {
        public static Decision allow() { return new Decision(false, ""); }
        public static Decision deny(String reason) { return new Decision(true, reason); }
    }
    private TankPolicy() { }
    public static Decision assess(List<Contents> tanks, Function<String, String> canonicalize) {
        String selected = null;
        for (Contents c : tanks) {
            if (c.amount() < 0 || c.amount() > 8000) return Decision.deny("invalid local amount");
            if (c.amount() == 0) continue;
            String id = c.fluidId();
            if (id == null || id.isBlank()) return Decision.deny("nonempty tank without a fluid ID");
            String canonical = canonicalize.apply(id);
            if (canonical == null || canonical.isBlank()) return Decision.deny("unregistered fluid: " + id);
            if (selected != null && !selected.equals(canonical)) return Decision.deny("mixed fluids: " + selected + " / " + canonical);
            selected = canonical;
        }
        return Decision.allow();
    }
}
