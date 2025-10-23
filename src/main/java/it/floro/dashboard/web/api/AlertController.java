package it.floro.dashboard.web.api;

import it.floro.dashboard.domain.Alert;
import it.floro.dashboard.service.AlertService;
import it.floro.dashboard.service.SampleDataService;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

/**
 * Controller REST che espone API per la gestione e il monitoraggio degli alert.
 *
 * Responsabilità:
 * - Esporre endpoint HTTP per consultare alert attivi e configurati
 * - Permettere la creazione di nuove configurazioni di alert via POST
 * - Delegare la logica di business a AlertService e SampleDataService
 *
 * Mapping base: /api/alerts
 *
 * Flusso tipico:
 * 1. Client chiama GET /api/alerts/active → ottiene alert attualmente scattati
 * 2. Client chiama GET /api/alerts/configured → vede tutte le configurazioni
 * 3. Client crea nuovo alert con POST /api/alerts
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    // ========================================================================
    // DIPENDENZE INIETTATE
    // ========================================================================

    /**
     * Service che gestisce il ciclo di vita degli alert.
     * Delegato per: checkAlerts(), getAllConfigured(), saveAlert()
     */
    private final AlertService alertService;

    /**
     * Service che fornisce accesso ai dati campionati.
     * Utilizzato per: recuperare il dataset attuale su cui valutare gli alert.
     */
    private final SampleDataService sampleDataService;

    // ========================================================================
    // COSTRUTTORE
    // ========================================================================

    /**
     * Costruttore con dependency injection di AlertService e SampleDataService.
     *
     * Spring autowira automaticamente le dipendenze durante l'istanziazione
     * del controller.
     *
     * @param alertService Service per la gestione degli alert
     * @param sampleDataService Service per l'accesso ai dati
     */
    public AlertController(AlertService alertService, SampleDataService sampleDataService) {
        this.alertService = alertService;
        this.sampleDataService = sampleDataService;
    }

    // ========================================================================
    // ENDPOINT 1: GET - VERIFICA ALERT ATTIVI
    // ========================================================================

    /**
     * Controlla e restituisce gli alert attualmente attivati.
     *
     * Metodo HTTP: GET
     * Mapping: /api/alerts/active
     * Risposta: application/json
     *
     * Algoritmo:
     * 1. Recupera il dataset completo da SampleDataService
     * 2. Passa il dataset ad AlertService per la valutazione delle condizioni
     * 3. Restituisce la lista degli alert scattati
     *
     * Esempio di risposta (JSON):
     * [
     *   {
     *     "id": "550e8400-e29b-41d4-a716-446655440000",
     *     "kpiType": "RESA",
     *     "threshold": 5.0,
     *     "condition": "BELOW",
     *     "area": "Tutte",
     *     "active": true,
     *     "message": "⚠️ Alert RESA: valore 4.2 t/ha scende sotto soglia 5.0 t/ha"
     *   }
     * ]
     *
     * Casi d'uso:
     * - Dashboard time-sensitive: refreshare la lista di alert ogni 10 secondi
     * - Notifiche: recuperare gli alert per inviare notifiche push
     * - Storico: tracciare quando gli alert sono stati attivati
     *
     * @return Lista di Alert attivati (vuota se nessun alert scatta)
     */
    @GetMapping("/active")
    public List<Alert> getActiveAlerts() {
        return alertService.checkAlerts(sampleDataService.getAll());
    }

    // ========================================================================
    // ENDPOINT 2: GET - LISTA ALERT CONFIGURATI
    // ========================================================================

    /**
     * Restituisce tutte le configurazioni di alert salvate nel sistema.
     *
     * Metodo HTTP: GET
     * Mapping: /api/alerts/configured
     * Risposta: application/json
     *
     * Algoritmo:
     * 1. Delega a AlertService.getAllConfigured()
     * 2. Ritorna la collezione di tutte le configurazioni
     *
     * Esempio di risposta (JSON):
     * [
     *   {
     *     "id": "1",
     *     "kpiType": "Resa Media",
     *     "threshold": 5.0,
     *     "condition": "BELOW",
     *     "area": "Tutte",
     *     "active": true,
     *     "message": ""
     *   },
     *   {
     *     "id": "2",
     *     "kpiType": "RISCHIO",
     *     "threshold": 0.7,
     *     "condition": "ABOVE",
     *     "area": "Tutte",
     *     "active": true,
     *     "message": ""
     *   }
     * ]
     *
     * Casi d'uso:
     * - Admin panel: visualizzare tutte le soglie di alert configurate
     * - Validazione: verificare che i parametri di alert siano corretti
     * - Debugging: controllare le configurazioni durante troubleshooting
     *
     * @return Collezione di tutte le configurazioni di alert
     */
    @GetMapping("/configured")
    public Collection<Alert> getConfigured() {
        return alertService.getAllConfigured();
    }

    // ========================================================================
    // ENDPOINT 3: POST - CREAZIONE NUOVO ALERT
    // ========================================================================

    /**
     * Crea una nuova configurazione di alert.
     *
     * Metodo HTTP: POST
     * Mapping: /api/alerts
     * Content-Type: application/json
     * Risposta: application/json con HTTP 200 OK
     *
     * Richiesta (JSON):
     * {
     *   "kpiType": "COSTO",
     *   "threshold": 200.0,
     *   "condition": "ABOVE",
     *   "area": "Sud"
     * }
     *
     * Risposta (JSON):
     * {
     *   "id": "550e8400-e29b-41d4-a716-446655440001",
     *   "kpiType": "COSTO",
     *   "threshold": 200.0,
     *   "condition": "ABOVE",
     *   "area": "Sud",
     *   "active": true,
     *   "message": ""
     * }
     *
     * Procedura:
     * 1. Deserializza il body JSON in un oggetto Alert
     * 2. Delega a AlertService.saveAlert() per la persistenza
     * 3. Restituisce l'Alert salvato con ID generato e stato iniziale
     *
     * Note di implementazione:
     * - @RequestBody deserializza automaticamente il JSON
     * - AlertService genera l'ID e gli assegna lo stato active=true
     * - TODO: Migrare da ConcurrentHashMap (memoria) a database persistente
     *
     * Casi d'uso:
     * - Admin: configurare nuove soglie di alert via UI/API
     * - Automazione: script che crea alert programmaticamente
     * - Testing: endpoint per test di integrazione
     *
     * @param alert Configurazione dell'alert da creare (deserializzato dal body)
     * @return Alert salvato con ID univoco assegnato
     */
    @PostMapping
    public Alert createAlert(@RequestBody Alert alert) {
        return alertService.saveAlert(alert);
    }
}