package it.floro.dashboard.web;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.service.KpiService;
import it.floro.dashboard.service.SampleDataService;
import it.floro.dashboard.service.WeatherService;
import it.floro.dashboard.web.dto.KpiSnapshot;
import it.floro.dashboard.web.dto.WeatherSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Controller REST che espone endpoint per streaming dati via Server-Sent Events (SSE).
 *
 * Responsabilità:
 * - Esporre due endpoint SSE: /api/stream/kpi e /api/stream/weather
 * - Gestire connessioni client persistenti (long-lived HTTP)
 * - Inviare snapshot KPI e meteo periodicamente al client
 * - Gestire lifecycle completo: connessione, streaming, disconnessione, cleanup
 * - Disaggregare KPI per area (Nord, Centro, Sud)
 * - Gestire errori di I/O (client disconnessi) e task cancellation
 *
 * Architettura: MVC + SSE (non Reactive)
 * - SSE è un pattern HTTP classico (non WebSocket)
 * - SseEmitter è il componente Spring per gestire SSE
 * - ScheduledExecutorService crea task periodici che inviano dati
 * - Callback onCompletion/onTimeout gestiscono cleanup
 *
 * Pattern SSE (Server-Sent Events):
 * - HTTP 1.1 standard (no upgrade richiesto, diversamente da WebSocket)
 * - Unidirezionale: server → client (no client → server dopo connessione)
 * - Browser supporta nativamente via EventSource API
 * - Riconnessione automatica se client disconnette
 * - Content-Type: text/event-stream
 *
 * Differenza SSE vs WebSocket:
 * - SSE: one-way, simpler, built-in reconnect, browser EventSource API
 * - WebSocket: two-way, requires upgrade, manual reconnect handling
 * - SSE è ideale per: notifiche, data feeds, time-series, real-time dashboards
 *
 * Lifecycle della connessione SSE:
 * 1. Client (browser) apre connessione GET /api/stream/kpi
 * 2. Server crea SseEmitter e ritorna (connessione rimane aperta)
 * 3. Server schedula task che invia periodicamente (ogni 5s per KPI)
 * 4. Client riceve evento via EventSource.onmessage
 * 5. Se client chiude: onCompletion → cancel task → cleanup
 * 6. Se timeout: onTimeout → cancel task → cleanup
 * 7. Se errore: onError → cancel task → cleanup
 *
 * Thread-safety:
 * - AtomicBoolean per flag "closed" (thread-safe)
 * - SseEmitter.send() è thread-safe (internamente sincronizzata)
 * - Task scheduler con ScheduledExecutorService (thread pool)
 *
 * Uso lato client (JavaScript):
 * ```javascript
 * const eventSource = new EventSource('/api/stream/kpi');
 * eventSource.addEventListener('kpi-update', (event) => {
 *     const data = JSON.parse(event.data);
 *     console.log('KPI ricevuti:', data);
 *     updateDashboard(data);
 * });
 * eventSource.onerror = () => {
 *     console.error('Errore nella connessione SSE');
 *     eventSource.close();
 * };
 * ```
 *
 * Performance e considerazioni:
 * - Timeout server: impostato a 0 (nessun timeout, connessione lunga)
 * - Buffer: SseEmitter mantiene buffer interno per riconnessioni
 * - Compression: abilitata automaticamente da Spring (gzip per grandi payload)
 * - Scalabilità: max N client simultanei = thread pool size (2 nel nostro caso)
 *   - Con ScheduledExecutorService limitato, è una bottleneck
 *   - Per scalare, usare pool più grande o Reactive (WebFlux)
 *
 * Logging:
 * - Ogni connessione, disconnessione, errore è loggato
 * - Utile per debug e monitoraggio production
 *
 * Possibili miglioramenti:
 * - Aggiungere autenticazione (verificare user prima SSE)
 * - Aggiungere rate limiting (max N connessioni per user)
 * - Usare WebFlux per scalabilità massiccia (reactive streams)
 * - Implementare heartbeat ping per rilevare disconnessioni silenti
 * - Comprimere payload JSON con brotli/zstd
 */
@RestController
@RequestMapping("/api/stream")
public class StreamController {

    // ========================================================================
    // LOGGING
    // ========================================================================

    private static final Logger logger = LoggerFactory.getLogger(StreamController.class);

    // ========================================================================
    // DIPENDENZE INIETTATE
    // ========================================================================

    /**
     * Service per accesso ai dati campionati.
     * Utilizzato per calcolare KPI.
     */
    private final SampleDataService sampleDataService;

    /**
     * Service per calcolo KPI.
     * Utilizzato per: resa, efficienza, costo, margine, rischio.
     */
    private final KpiService kpiService;

    /**
     * Service per dati meteo attuali.
     * Utilizzato per snapshot meteo real-time.
     */
    @Autowired
    private WeatherService weatherService;

    // ========================================================================
    // THREAD POOL SCHEDULATO
    // ========================================================================

    /**
     * Thread pool per eseguire task periodici che inviano SSE.
     *
     * Configurazione:
     * - Dimensione: 2 thread (uno per stream KPI, uno per stream Weather)
     * - Tipo: ScheduledExecutorService (consente scheduleAtFixedRate)
     * - Daemon threads: sì (si fermano quando JVM termina)
     * - Nome thread: "sse-publisher" (per logging/debug)
     *
     * Ciclo di vita:
     * - Creato al startup (quando Spring istanzia il bean)
     * - Task schedulati su questo pool dal metodo streamKpi/streamWeather
     * - Task cancellato quando client disconnette (onCompletion/onTimeout)
     * - Pool continua a vivere per nuovi client
     *
     * Thread-safety: ScheduledExecutorService è thread-safe internamente.
     *
     * Nota: con 2 thread, supportiamo massimo 2 client simultanei
     * per scalare: aumentare pool size o migrare a Reactive (WebFlux)
     */
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "sse-publisher");
                t.setDaemon(true);
                return t;
            });

    // ========================================================================
    // COSTRUTTORE
    // ========================================================================

    /**
     * Costruttore con dependency injection.
     *
     * @param sampleDataService Service per dati
     * @param kpiService Service per calcolo KPI
     */
    public StreamController(SampleDataService sampleDataService, KpiService kpiService) {
        this.sampleDataService = sampleDataService;
        this.kpiService = kpiService;
    }

    // ========================================================================
    // ENDPOINT SSE - KPI
    // ========================================================================

    /**
     * Endpoint SSE: stream KPI in tempo reale ogni 5 secondi.
     *
     * Metodo HTTP: GET
     * Mapping: /api/stream/kpi
     * Content-Type: text/event-stream
     * Timeout: nessuno (0 = infinito, connessione long-lived)
     *
     * Flusso:
     * 1. Client apre GET /api/stream/kpi
     * 2. Server crea SseEmitter (HTTP connection rimane aperta)
     * 3. Server schedula task che ogni 5s:
     *    a. Recupera tutti i record
     *    b. Calcola i 5 KPI principali
     *    c. Disaggrega KPI per area
     *    d. Crea KpiSnapshot
     *    e. Invia evento SSE al client
     * 4. Se client chiude: onCompletion callback → cancel task
     * 5. Se timeout: onTimeout callback → cancel task
     * 6. Se errore (IOException): catch → cancel task
     *
     * Evento SSE:
     * ```
     * event: kpi-update
     * data: {"resaMedia":4.5,"efficienzaIdrica":3.2,"costoUnitario":250.0,...}
     *
     * event: kpi-update
     * data: {"resaMedia":4.52,"efficienzaIdrica":3.21,...}
     * ```
     *
     * Lato client (JavaScript):
     * ```javascript
     * const source = new EventSource('/api/stream/kpi');
     * source.addEventListener('kpi-update', (e) => {
     *     const data = JSON.parse(e.data);
     *     console.log('KPI:', data.resaMedia, data.efficienzaIdrica);
     *     updateCharts(data);
     * });
     * source.onerror = () => source.close();
     * ```
     *
     * Utilizzo UI:
     * - Aggiornare gauge, card, grafici ogni 5 secondi
     * - Smooth animation delle metriche
     * - Alert se KPI supera soglie critiche
     *
     * @return SseEmitter che mantiene la connessione aperta
     */
    @GetMapping(value = "/kpi", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamKpi() {
        // ===== STEP 1: CREATE EMITTER =====
        // SseEmitter(0L) = timeout infinito (no timeout server)
        // Se timeout (es 60000), server chiude dopo 60s di inattività client
        final SseEmitter emitter = new SseEmitter(0L);

        // Flag atomico per tracciare se connessione è chiusa
        // Utilizzato per prevenire doppi cleanup e evitare race conditions
        final AtomicBoolean closed = new AtomicBoolean(false);

        // ===== STEP 2: DEFINE CLOSE HANDLER =====
        // Quando client disconnette (completamento, timeout, errore),
        // questo handler garantisce che il task sia cancellato
        Runnable onClose = () -> {
            // compareAndSet atomico: imposta closed=true solo se era false
            // Evita multiple invocazioni del cleanup
            if (closed.compareAndSet(false, true)) {
                try {
                    emitter.complete();
                } catch (Throwable ignored) {}
                logger.info("Client disconnesso dallo stream KPI");
            }
        };

        // ===== STEP 3: REGISTER CALLBACKS =====
        // Spring chiama questi callback quando accadono vari eventi
        emitter.onCompletion(onClose);     // Client chiuse connessione (normale)
        emitter.onTimeout(onClose);        // Timeout server (raro, timeout=0)
        emitter.onError(ex -> onClose.run()); // Errore I/O

        // ===== STEP 4: SEND INITIAL PING =====
        // Opzionale: ping iniziale per confermare connessione OK
        try {
            emitter.send(SseEmitter.event()
                    .name("kpi-update")
                    .data(Map.of("hello", "world", "ts", Instant.now().toString())));
        } catch (IOException e) {
            // Se già chiuso, esci subito
            onClose.run();
            return emitter;
        }

        // ===== STEP 5: SCHEDULE PERIODIC TASK =====
        // scheduleAtFixedRate: esegui task ogni 5 secondi, partendo subito (delay=0)
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            // Early return se già chiuso (non mandare dati a connessione morta)
            if (closed.get()) return;

            try {
                // Recupera dataset
                List<SampleRecord> records = sampleDataService.getAll();

                // Calcola i 5 KPI principali
                double resaMedia        = kpiService.calcolaResaMedia(records);
                double efficienzaIdrica = kpiService.calcolaEfficienzaIdrica(records);
                double costoUnitario    = kpiService.calcolaCostoUnitario(records);
                double margineUnitario  = kpiService.calcolaMargineUnitario(records);
                double rischioClimatico = kpiService.calcolaRischioClimatico(records);

                // Disaggrega per area (Nord, Centro, Sud)
                Map<String, Double> resaPerArea    = calcKpiPerArea(records, kpiService::calcolaResaMedia);
                Map<String, Double> effPerArea     = calcKpiPerArea(records, kpiService::calcolaEfficienzaIdrica);
                Map<String, Double> costoPerArea   = calcKpiPerArea(records, kpiService::calcolaCostoUnitario);
                Map<String, Double> marginePerArea = calcKpiPerArea(records, kpiService::calcolaMargineUnitario);
                Map<String, Double> rischioPerArea = calcKpiPerArea(records, kpiService::calcolaRischioClimatico);

                // Crea snapshot
                KpiSnapshot snapshot = new KpiSnapshot(
                        resaMedia, efficienzaIdrica, costoUnitario,
                        margineUnitario, rischioClimatico,
                        resaPerArea, effPerArea, costoPerArea,
                        marginePerArea, rischioPerArea
                );

                // Invia evento SSE
                emitter.send(SseEmitter.event()
                        .name("kpi-update")
                        .data(snapshot));

            } catch (IOException io) {
                // IOException = client disconnesso, esci
                onClose.run();
            } catch (Throwable t) {
                // Errore generico: log e cleanup
                logger.warn("Errore nel publisher KPI: {}", t.getMessage(), t);
                onClose.run();
            }
        }, 0, 5, TimeUnit.SECONDS); // delay=0 (subito), period=5s

        // ===== STEP 6: REGISTER TASK CANCELLATION =====
        // Quando connessione chiude, cancella il task periodico
        // Evita waste di risorse (thread continua a correre se non cancellato)
        emitter.onCompletion(() -> cancelIfNeeded(task));
        emitter.onTimeout(() -> cancelIfNeeded(task));
        emitter.onError(ex -> cancelIfNeeded(task));

        return emitter;
    }

    // ========================================================================
    // ENDPOINT SSE - METEO
    // ========================================================================

    /**
     * Endpoint SSE: stream meteo in tempo reale ogni 3 secondi.
     *
     * Metodo HTTP: GET
     * Mapping: /api/stream/weather
     * Content-Type: text/event-stream
     * Timeout: nessuno (0 = infinito)
     *
     * Frequenza: ogni 3 secondi (più frequente di KPI per riflettere cambi meteo)
     *
     * Flusso:
     * 1. Client apre GET /api/stream/weather
     * 2. Server crea SseEmitter
     * 3. Server schedula task che ogni 3s:
     *    a. Chiama weatherService.getCurrentWeather()
     *    b. Crea WeatherSnapshot con dati attuali
     *    c. Invia evento SSE
     * 4. Client disconnette → cancel task
     *
     * Evento SSE:
     * ```
     * event: weather-update
     * data: {"tempNord":18.5,"tempCentro":20.2,"tempSud":25.1,...}
     *
     * event: weather-update
     * data: {"tempNord":18.6,"tempCentro":20.3,"tempSud":25.2,...}
     * ```
     *
     * Lato client (JavaScript):
     * ```javascript
     * const source = new EventSource('/api/stream/weather');
     * source.addEventListener('weather-update', (e) => {
     *     const weather = JSON.parse(e.data);
     *     console.log('Temp Nord:', weather.tempNord);
     *     updateWeatherCards(weather);
     * });
     * ```
     *
     * @return SseEmitter che mantiene la connessione aperta
     */
    @GetMapping(value = "/weather", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWeather() {
        final SseEmitter emitter = new SseEmitter(0L);
        final AtomicBoolean closed = new AtomicBoolean(false);

        Runnable onClose = () -> {
            if (closed.compareAndSet(false, true)) {
                try {
                    emitter.complete();
                } catch (Throwable ignored) {}
                logger.info("Client disconnesso dallo stream Weather");
            }
        };
        emitter.onCompletion(onClose);
        emitter.onTimeout(onClose);
        emitter.onError(ex -> onClose.run());

        // Schedula task ogni 3 secondi
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            try {
                // Recupera dati meteo attuali
                WeatherSnapshot snapshot = weatherService.getCurrentWeather();

                // Invia evento SSE
                emitter.send(SseEmitter.event()
                        .name("weather-update")
                        .data(snapshot));

            } catch (IOException io) {
                onClose.run();
            } catch (Throwable t) {
                logger.warn("Errore nel publisher Weather: {}", t.getMessage(), t);
                onClose.run();
            }
        }, 0, 3, TimeUnit.SECONDS);

        emitter.onCompletion(() -> cancelIfNeeded(task));
        emitter.onTimeout(() -> cancelIfNeeded(task));
        emitter.onError(ex -> cancelIfNeeded(task));

        return emitter;
    }

    // ========================================================================
    // METODI HELPER PRIVATI
    // ========================================================================

    /**
     * Cancella un task schedulato se non è già stato cancellato.
     *
     * Utilizzo: pulire task quando client SSE disconnette.
     *
     * @param f ScheduledFuture del task da cancellare
     */
    private void cancelIfNeeded(ScheduledFuture<?> f) {
        if (f != null && !f.isCancelled()) {
            f.cancel(true); // true = interrupting allowed (interrompe thread se possibile)
        }
    }

    /**
     * Disaggrega KPI per area (Nord, Centro, Sud).
     *
     * Algoritmo:
     * 1. Raggruppa record per area (groupingBy)
     * 2. Per ogni area, applica funzione KPI
     * 3. Ritorna mappa area → valore KPI
     *
     * Esempio:
     * - records = [R1 Nord, R2 Nord, R3 Centro, R4 Sud]
     * - kpiFunc = calcolaResaMedia
     * - result = {"Nord": 4.5, "Centro": 4.2, "Sud": 3.8}
     *
     * Utilizzo: popolare snapshot con disaggregazione per area
     *
     * @param records Dataset completo
     * @param kpiFunc Funzione che calcola KPI da list di record
     * @return Mappa area → valore KPI
     */
    private Map<String, Double> calcKpiPerArea(
            List<SampleRecord> records,
            java.util.function.Function<List<SampleRecord>, Double> kpiFunc) {

        // Raggruppa record per area
        Map<String, List<SampleRecord>> byArea = records.stream()
                .collect(Collectors.groupingBy(SampleRecord::area));

        // Per ogni area, calcola KPI e aggiungi a result
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, List<SampleRecord>> entry : byArea.entrySet()) {
            result.put(entry.getKey(), kpiFunc.apply(entry.getValue()));
        }
        return result;
    }
}