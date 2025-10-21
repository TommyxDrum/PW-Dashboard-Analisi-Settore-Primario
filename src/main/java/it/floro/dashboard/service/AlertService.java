package it.floro.dashboard.service;

import it.floro.dashboard.domain.Alert;
import it.floro.dashboard.domain.SampleRecord;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertService {

    private final KpiService kpiService;

    // Memoria degli alert configurati (in produzione: database)
    private final Map<String, Alert> configuredAlerts = new ConcurrentHashMap<>();

    public AlertService(KpiService kpiService) {
        this.kpiService = kpiService;

        // 🔴 Alert di esempio pre-configurati
        configuredAlerts.put("1", new Alert(
                "1", "RESA", 5.0, "BELOW", "Tutte", true, ""
        ));
        configuredAlerts.put("2", new Alert(
                "2", "RISCHIO", 0.7, "ABOVE", "Tutte", true, ""
        ));
    }

    /**
     * Controlla se ci sono alert da triggerare
     */
    public List<Alert> checkAlerts(List<SampleRecord> records) {
        List<Alert> triggered = new ArrayList<>();

        for (Alert config : configuredAlerts.values()) {
            if (!config.active()) continue;

            double currentValue = getCurrentKpiValue(config.kpiType(), records);
            boolean shouldTrigger = evaluateCondition(
                    currentValue, config.threshold(), config.condition()
            );

            if (shouldTrigger) {
                triggered.add(Alert.createTriggered(
                        config.kpiType(), currentValue,
                        config.threshold(), config.condition()
                ));
            }
        }

        return triggered;
    }

    /**
     * Salva un nuovo alert
     */
    public Alert saveAlert(Alert alert) {
        String id = UUID.randomUUID().toString();
        Alert saved = new Alert(
                id, alert.kpiType(), alert.threshold(),
                alert.condition(), alert.area(), true, ""
        );
        configuredAlerts.put(id, saved);
        return saved;
    }

    /**
     * Lista tutti gli alert configurati
     */
    public Collection<Alert> getAllConfigured() {
        return configuredAlerts.values();
    }

    // ========== HELPER PRIVATI ==========

    private double getCurrentKpiValue(String kpiType, List<SampleRecord> records) {
        return switch (kpiType) {
            case "RESA" -> kpiService.calcolaResaMedia(records);
            case "EFFICIENZA" -> kpiService.calcolaEfficienzaIdrica(records);
            case "COSTO" -> kpiService.calcolaCostoUnitario(records);
            case "MARGINE" -> kpiService.calcolaMargineUnitario(records);
            case "RISCHIO" -> kpiService.calcolaRischioClimatico(records);
            default -> 0.0;
        };
    }

    private boolean evaluateCondition(double value, double threshold, String condition) {
        return switch (condition) {
            case "ABOVE" -> value > threshold;
            case "BELOW" -> value < threshold;
            default -> false;
        };
    }
}