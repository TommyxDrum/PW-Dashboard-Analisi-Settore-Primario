package it.floro.dashboard.web.dto;

import java.util.Map;

/**
 * DTO (Data Transfer Object) che rappresenta uno snapshot dei KPI aggregati
 * da esporre al frontend del dashboard.
 *
 * Responsabilità:
 * - Incapsulare metriche KPI aggregate (medie globali)
 * - Incapsulare metriche KPI disaggregate per area geografica
 * - Fornire timestamp per tracciare la freschezza dei dati
 * - Strutturare i dati in formato JSON-serializable per REST API
 *
 * Struttura:
 * - Sezione "Aggregate": KPI calcolati su tutto il dataset
 * - Sezione "Per Area": stesse metriche disaggregate per Nord/Centro/Sud
 * - Timestamp: millisecondi Unix per client-side caching/staleness check
 *
 * Nota: Questo record utilizza un costruttore ausiliario che genera
 * automaticamente il timestamp se non fornito esplicitamente.
 */
public record KpiSnapshot(
        // ====================================================================
        // SEZIONE 1: METRICHE AGGREGATE (Globali)
        // ====================================================================

        /**
         * Resa media aggregata su tutto il dataset in tonnellate per ettaro (t/ha).
         *
         * Calcolata come media della resa per ettaro su tutti i campi e giorni.
         * Rappresenta la produttività aggregata dell'intera azienda/regione.
         *
         * Range tipico: [2, 9] t/ha a seconda delle colture
         */
        double resaMedia,

        /**
         * Efficienza idrica media aggregata in kg per metro cubo (kg/m³).
         *
         * Rappresenta quanti kg di prodotto vengono generati per ogni m³ d'acqua utilizzata.
         * KPI di sostenibilità: valori più alti indicano minor consumo idrico.
         *
         * Range tipico: [2, 10] kg/m³
         */
        double efficienzaIdrica,

        /**
         * Costo unitario medio aggregato in euro per tonnellata (€/t).
         *
         * Rappresenta il costo operativo medio per unità di prodotto.
         * Include: costi fissi, input, acqua, ammortamenti.
         *
         * Range tipico: [150, 400] €/t a seconda delle colture
         */
        double costoUnitario,

        /**
         * Margine unitario medio aggregato in euro per tonnellata (€/t).
         *
         * Formula: prezzo unitario - costo unitario
         * Rappresenta il profitto netto per tonnellata di prodotto.
         *
         * Interpretazione:
         * - Positivo: lucro, prezzo supera costo
         * - Negativo: perdita, costo supera prezzo
         *
         * Range tipico: [-50, 200] €/t
         */
        double margineUnitario,

        /**
         * Indice di rischio climatico medio aggregato in scala [0, 1].
         *
         * Aggregazione di fattori meteorologici:
         * - Anomalie di temperatura
         * - Stress idrico (siccità/allagamenti)
         * - Radiazione solare estrema
         * - Gelate/brinate
         *
         * 0.0 = nessun rischio climatico
         * 1.0 = rischio massimo
         *
         * Range tipico: [0.2, 0.8]
         */
        double rischioClimatico,

        // ====================================================================
        // SEZIONE 2: METRICHE PER AREA GEOGRAFICA (Disaggregate)
        // ====================================================================

        /**
         * Mappa area → resa media per quella area.
         *
         * Chiavi: "Nord", "Centro", "Sud"
         * Valori: resa media in t/ha per l'area specifica
         *
         * Utilizzo: grafico a barre/radar chart nel dashboard per confronti geografici
         *
         * Esempio:
         * {"Nord": 4.8, "Centro": 5.2, "Sud": 5.5}
         */
        Map<String, Double> resaPerArea,

        /**
         * Mappa area → efficienza idrica media per quella area.
         *
         * Chiavi: "Nord", "Centro", "Sud"
         * Valori: efficienza idrica in kg/m³ per l'area specifica
         *
         * Utilizzo: analizzare disparità nella gestione irrigua tra aree
         *
         * Esempio:
         * {"Nord": 3.2, "Centro": 4.1, "Sud": 4.8}
         */
        Map<String, Double> efficienzaPerArea,

        /**
         * Mappa area → costo unitario medio per quella area.
         *
         * Chiavi: "Nord", "Centro", "Sud"
         * Valori: costo unitario in €/t per l'area specifica
         *
         * Utilizzo: confrontare efficienze di costo tra aree (logistica, manodopera)
         *
         * Esempio:
         * {"Nord": 220, "Centro": 240, "Sud": 210}
         */
        Map<String, Double> costoPerArea,

        /**
         * Mappa area → margine unitario medio per quella area.
         *
         * Chiavi: "Nord", "Centro", "Sud"
         * Valori: margine unitario in €/t per l'area specifica
         *
         * Utilizzo: identificare aree con migliore profittabilità
         *
         * Esempio:
         * {"Nord": 60, "Centro": 40, "Sud": 80}
         */
        Map<String, Double> marginePerArea,

        /**
         * Mappa area → indice di rischio climatico medio per quella area.
         *
         * Chiavi: "Nord", "Centro", "Sud"
         * Valori: rischio climatico [0, 1] per l'area specifica
         *
         * Utilizzo: heat map del rischio climatico per aree geografiche
         *
         * Esempio:
         * {"Nord": 0.45, "Centro": 0.52, "Sud": 0.38}
         */
        Map<String, Double> rischioPerArea,

        // ====================================================================
        // SEZIONE 3: METADATA
        // ====================================================================

        /**
         * Timestamp di generazione dello snapshot in millisecondi Unix.
         *
         * Utilizzo:
         * - Client-side: calcolare freschezza dei dati ("ultimo aggiornamento: 5 minuti fa")
         * - Caching: validare se il dato è ancora fresco prima di aggiornare UI
         * - Logging: tracciare quando è stato generato lo snapshot
         *
         * Formato: System.currentTimeMillis() (ms da epoch)
         */
        long timestamp
) {

    // ========================================================================
    // COSTRUTTORE AUSILIARIO (Compact Constructor Equivalente)
    // ========================================================================

    /**
     * Costruttore ausiliario che genera automaticamente il timestamp.
     *
     * Utilizzo comune: quando si crea uno snapshot, è conveniente non
     * dovere specificare manualmente il timestamp. Questo costruttore
     * lo genera automaticamente usando System.currentTimeMillis().
     *
     * Invoca il costruttore canonico del record (generato da Java) passando
     * System.currentTimeMillis() come 11º parametro.
     *
     * Esempio di utilizzo:
     * ```
     * KpiSnapshot snapshot = new KpiSnapshot(
     *     5.2,                    // resaMedia
     *     4.1,                    // efficienzaIdrica
     *     230.0,                  // costoUnitario
     *     70.0,                   // margineUnitario
     *     0.45,                   // rischioClimatico
     *     resaMap,                // resaPerArea
     *     efficienzaMap,          // efficienzaPerArea
     *     costoMap,               // costoPerArea
     *     margineMap,             // marginePerArea
     *     rischioMap              // rischioPerArea
     *     // timestamp generato automaticamente!
     * );
     * ```
     *
     * @param resaMedia Resa media aggregata (t/ha)
     * @param efficienzaIdrica Efficienza idrica media (kg/m³)
     * @param costoUnitario Costo unitario medio (€/t)
     * @param margineUnitario Margine unitario medio (€/t)
     * @param rischioClimatico Rischio climatico medio [0, 1]
     * @param resaPerArea Resa media per area geografica
     * @param efficienzaPerArea Efficienza idrica per area
     * @param costoPerArea Costo per area
     * @param marginePerArea Margine per area
     * @param rischioPerArea Rischio climatico per area
     */
    public KpiSnapshot(double resaMedia, double efficienzaIdrica,
                       double costoUnitario, double margineUnitario,
                       double rischioClimatico,
                       Map<String, Double> resaPerArea,
                       Map<String, Double> efficienzaPerArea,
                       Map<String, Double> costoPerArea,
                       Map<String, Double> marginePerArea,
                       Map<String, Double> rischioPerArea) {
        // Delega al costruttore canonico aggiungendo timestamp attuale
        this(resaMedia, efficienzaIdrica, costoUnitario, margineUnitario,
                rischioClimatico, resaPerArea, efficienzaPerArea, costoPerArea,
                marginePerArea, rischioPerArea, System.currentTimeMillis());
    }
}