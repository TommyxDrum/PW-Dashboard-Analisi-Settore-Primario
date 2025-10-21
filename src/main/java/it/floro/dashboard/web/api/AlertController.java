package it.floro.dashboard.web.api;

import it.floro.dashboard.domain.Alert;
import it.floro.dashboard.service.AlertService;
import it.floro.dashboard.service.SampleDataService;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final SampleDataService sampleDataService;

    public AlertController(AlertService alertService, SampleDataService sampleDataService) {
        this.alertService = alertService;
        this.sampleDataService = sampleDataService;
    }

    /**
     * GET /api/alerts/active - Controlla alert attivi
     */
    @GetMapping("/active")
    public List<Alert> getActiveAlerts() {
        return alertService.checkAlerts(sampleDataService.getAll());
    }

    /**
     * GET /api/alerts/configured - Lista alert configurati
     */
    @GetMapping("/configured")
    public Collection<Alert> getConfigured() {
        return alertService.getAllConfigured();
    }

    /**
     * POST /api/alerts - Crea nuovo alert
     */
    @PostMapping
    public Alert createAlert(@RequestBody Alert alert) {
        return alertService.saveAlert(alert);
    }
}