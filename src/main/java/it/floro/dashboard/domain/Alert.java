package it.floro.dashboard.domain;

public record Alert(
        String id,
        String kpiType,      // "RESA", "EFFICIENZA", "COSTO", "MARGINE", "RISCHIO"
        double threshold,    // Valore soglia
        String condition,    // "ABOVE" o "BELOW"
        String area,         // "Nord", "Centro", "Sud", "Tutte"
        boolean active,
        String message
) {
    public static Alert createTriggered(String kpiType, double currentValue,
                                        double threshold, String condition) {
        String msg = String.format(
                "⚠️ Alert %s: valore %.2f %s soglia %.2f",
                kpiType, currentValue,
                condition.equals("ABOVE") ? "supera" : "sotto",
                threshold
        );
        return new Alert(
                java.util.UUID.randomUUID().toString(),
                kpiType, threshold, condition, "Tutte", true, msg
        );
    }
}