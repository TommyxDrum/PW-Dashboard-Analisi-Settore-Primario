package it.floro.dashboard.service;

import it.floro.dashboard.domain.Alert;
import it.floro.dashboard.domain.SampleRecord;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service che gestisce il ciclo di vita degli alert per il dashboard agricolo.
 *
 * Responsabilità:
 * - Mantenere la configurazione degli alert (soglie e condizioni)
 * - Controllare periodicamente se le condizioni di trigger sono soddisfatte
 * - Creare e restituire alert attivati quando le soglie vengono superate
 *
 * Nota: In produzione, gli alert configurati dovrebbero essere persistiti su database
 * anziché in memoria.
 */
@Service
public class AlertService {

    private final KpiService kpiService;

    /**
     * Mappa thread-safe degli alert configurati.
     * Chiave: ID univoco dell'alert
     * Valore: Configurazione dell'alert (soglia, condizione, stato attivo)
     *
     * TODO: Migrare da ConcurrentHashMap a database (es. JPA Repository)
     */
    private final Map<String, Alert> configuredAlerts = new ConcurrentHashMap<>();

    /**
     * Costruttore con injection di KpiService e inizializzazione degli alert di esempio.
     */
    public AlertService(KpiService kpiService) {
        this.kpiService = kpiService;

        // ===== ALERT DI ESEMPIO PRE-CONFIGURATI =====
        // In produzione, questi verrebbero caricati dal database

        configuredAlerts.put("1", new Alert(
                "1",
                "Resa Media",           // KPI monitorato
                5.0,                    // Soglia: tonnellate per ettaro
                "BELOW",                // Trigger se la resa scende sotto 5 t/ha
                "Tutte",
                true,                   // Alert attivo
                ""
        ));

        configuredAlerts.put("2", new Alert(
                "2",
                "RISCHIO",              // KPI monitorato
                0.7,                    // Soglia: indice rischio [0..1]
                "ABOVE",                // Trigger se il rischio supera 0.7
                "Tutte",
                true,                   // Alert attivo
                ""
        ));
    }

    /**
     * Esegue il controllo di tutti gli alert configurati contro i dati attuali.
     *
     * Algoritmo:
     * 1. Itera su tutti gli alert configurati e attivi
     * 2. Calcola il valore KPI attuale dai SampleRecord
     * 3. Valuta se la condizione è soddisfatta (ABOVE/BELOW)
     * 4. Se sì, crea e aggiunge l'alert alla lista degli attivati
     *
     * @param records Lista dei campioni attuali da analizzare
     * @return Lista degli alert attivati (vuota se nessun alert scatta)
     */
    public List<Alert> checkAlerts(List<SampleRecord> records) {
        List<Alert> triggered = new ArrayList<>();

        // Itera su ogni alert configurato
        for (Alert config : configuredAlerts.values()) {
            // Salta gli alert disattivati
            if (!config.active()) continue;

            // Calcola il valore KPI attuale
            double currentValue = getCurrentKpiValue(config.kpiType(), records);

            // Valuta se la condizione di trigger è soddisfatta
            boolean shouldTrigger = evaluateCondition(
                    currentValue,
                    config.threshold(),
                    config.condition()
            );

            // Se la condizione è soddisfatta, crea un alert attivato
            if (shouldTrigger) {
                triggered.add(Alert.createTriggered(
                        config.kpiType(),
                        currentValue,
                        config.threshold(),
                        config.condition()
                ));
            }
        }

        return triggered;
    }

    /**
     * Salva una nuova configurazione di alert.
     *
     * @param alert Configurazione dell'alert da salvare
     * @return L'alert salvato con ID e stato iniziale assegnati
     */
    public Alert saveAlert(Alert alert) {
        String id = UUID.randomUUID().toString();

        // Crea un nuovo alert con ID univoco e stato attivo di default
        Alert saved = new Alert(
                id,
                alert.kpiType(),
                alert.threshold(),
                alert.condition(),
                alert.area(),
                true,           // Nuovo alert attivo per default
                ""
        );

        // Persiste in memoria (TODO: salvare su database)
        configuredAlerts.put(id, saved);
        return saved;
    }

    /**
     * Recupera tutti gli alert configurati.
     *
     * @return Collezione di tutte le configurazioni di alert
     */
    public Collection<Alert> getAllConfigured() {
        return configuredAlerts.values();
    }

    // ========== METODI HELPER PRIVATI ==========

    /**
     * Calcola il valore KPI attuale in base al tipo richiesto.
     *
     * Delega il calcolo a KpiService in base al tipo di KPI.
     *
     * @param kpiType Tipo di KPI: "Resa Media", "EFFICIENZA", "COSTO", "MARGINE", "RISCHIO"
     * @param records Lista dei campioni da cui calcolare il valore
     * @return Valore KPI calcolato (0.0 se tipo non riconosciuto)
     */
    private double getCurrentKpiValue(String kpiType, List<SampleRecord> records) {
        return switch (kpiType) {
            case "Resa Media" -> kpiService.calcolaResaMedia(records);                    // tonnellate/ha
            case "EFFICIENZA" -> kpiService.calcolaEfficienzaIdrica(records);             // kg/m³
            case "COSTO" -> kpiService.calcolaCostoUnitario(records);                     // euro/tonnellata
            case "MARGINE" -> kpiService.calcolaMargineUnitario(records);                 // euro/tonnellata
            case "RISCHIO" -> kpiService.calcolaRischioClimatico(records);                // indice [0..1]
            default -> 0.0;
        };
    }

    /**
     * Valuta se una condizione di trigger è soddisfatta.
     *
     * @param value Valore attuale misurato
     * @param threshold Valore soglia di confronto
     * @param condition Tipo di condizione: "ABOVE" (>) o "BELOW" (<)
     * @return true se la condizione è soddisfatta, false altrimenti
     */
    private boolean evaluateCondition(double value, double threshold, String condition) {
        return switch (condition) {
            case "ABOVE" -> value > threshold;   // Trigger se valore supera la soglia
            case "BELOW" -> value < threshold;   // Trigger se valore scende sotto la soglia
            default -> false;
        };
    }
}