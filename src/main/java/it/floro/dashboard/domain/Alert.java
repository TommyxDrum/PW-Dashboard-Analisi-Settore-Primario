package it.floro.dashboard.domain;

/**
 * Record che rappresenta un sistema di allarme per il monitoraggio di KPI.
 * Utilizzato per tracciare soglie critiche e inviare notifiche quando
 * determinate condizioni vengono raggiunte.
 */
public record Alert(
        String id,                          // Identificatore univoco (UUID)
        String kpiType,                     // Tipo di KPI: "RESA", "EFFICIENZA", "COSTO", "MARGINE", "RISCHIO"
        double threshold,                   // Valore soglia di attivazione
        String condition,                   // Condizione di trigger: "ABOVE" (supera) o "BELOW" (scende sotto)
        String area,                        // Area geografica: "Nord", "Centro", "Sud", "Tutte"
        boolean active,                     // Indica se l'alert è attivo
        String message                      // Messaggio descrittivo dell'alert
) {

    /**
     * Crea e restituisce un Alert attivato con i dati attuali.
     *
     * @param kpiType Tipo di KPI monitorato
     * @param currentValue Valore misurato attuale
     * @param threshold Valore soglia di confronto
     * @param condition Tipo di condizione ("ABOVE" o "BELOW")
     * @return Un nuovo Alert con messaggio formattato e stato attivo
     */
    public static Alert createTriggered(String kpiType, double currentValue,
                                        double threshold, String condition) {

        // Costruisce il messaggio di alert con l'operazione in linguaggio naturale
        String msg = String.format(
                "⚠️ Alert %s valore %.2f t/ha %s soglia %.2f t/ha",
                kpiType, //Tipo di KPI monitorato
                currentValue, //Valore misurato attuale
                condition.equals("ABOVE") ? "supera" : "sotto",  // Traduce la condizione
                threshold //Valore soglia di confronto
        );

        // Istanzia e restituisce un nuovo Alert con ID univoco e stato attivo
        return new Alert(
                java.util.UUID.randomUUID().toString(),  // Genera ID univoco
                kpiType,
                threshold,
                condition,
                "Tutte",    // Area di default: monitoraggio globale
                true,       // Alert attivato per default
                msg
        );
    }
}