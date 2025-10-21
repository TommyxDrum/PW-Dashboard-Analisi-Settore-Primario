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
 * StreamController (versione MVC con SseEmitter).
 *
 * Evita l'adapter MVC↔Reactive che usa AsyncContext (fonte del tuo errore).
 * Gestisce esplicitamente lifecycle e cleanup dei task periodici.
 */
@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private static final Logger logger = LoggerFactory.getLogger(StreamController.class);

    private final SampleDataService sampleDataService;
    private final KpiService kpiService;

    @Autowired
    private WeatherService weatherService;

    // Thread-pool per i publisher SSE
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "sse-publisher");
                t.setDaemon(true);
                return t;
            });

    public StreamController(SampleDataService sampleDataService, KpiService kpiService) {
        this.sampleDataService = sampleDataService;
        this.kpiService = kpiService;
    }

    /** SSE KPI: snapshot ~ogni 5s */
    @GetMapping(value = "/kpi", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamKpi() {
        final SseEmitter emitter = new SseEmitter(0L); // no timeout server
        final AtomicBoolean closed = new AtomicBoolean(false);

        Runnable onClose = () -> {
            if (closed.compareAndSet(false, true)) {
                try { emitter.complete(); } catch (Throwable ignored) {}
                logger.info("Client disconnesso dallo stream KPI");
            }
        };
        emitter.onCompletion(onClose);
        emitter.onTimeout(onClose);
        emitter.onError(ex -> onClose.run());

        // opzionale: ping iniziale
        try {
            emitter.send(SseEmitter.event().name("kpi-update")
                    .data(Map.of("hello", "world", "ts", Instant.now().toString())));
        } catch (IOException e) {
            onClose.run();
            return emitter;
        }

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            try {
                List<SampleRecord> records = sampleDataService.getAll();

                double resaMedia        = kpiService.calcolaResaMedia(records);
                double efficienzaIdrica = kpiService.calcolaEfficienzaIdrica(records);
                double costoUnitario    = kpiService.calcolaCostoUnitario(records);
                double margineUnitario  = kpiService.calcolaMargineUnitario(records);
                double rischioClimatico = kpiService.calcolaRischioClimatico(records);

                Map<String, Double> resaPerArea    = calcKpiPerArea(records, kpiService::calcolaResaMedia);
                Map<String, Double> effPerArea     = calcKpiPerArea(records, kpiService::calcolaEfficienzaIdrica);
                Map<String, Double> costoPerArea   = calcKpiPerArea(records, kpiService::calcolaCostoUnitario);
                Map<String, Double> marginePerArea = calcKpiPerArea(records, kpiService::calcolaMargineUnitario);
                Map<String, Double> rischioPerArea = calcKpiPerArea(records, kpiService::calcolaRischioClimatico);

                KpiSnapshot snapshot = new KpiSnapshot(
                        resaMedia, efficienzaIdrica, costoUnitario,
                        margineUnitario, rischioClimatico,
                        resaPerArea, effPerArea, costoPerArea,
                        marginePerArea, rischioPerArea
                );

                emitter.send(SseEmitter.event().name("kpi-update").data(snapshot));
            } catch (IOException io) {
                onClose.run(); // client chiuso
            } catch (Throwable t) {
                logger.warn("Errore nel publisher KPI: {}", t.getMessage(), t);
                onClose.run();
            }
        }, 0, 5, TimeUnit.SECONDS);

        emitter.onCompletion(() -> cancelIfNeeded(task));
        emitter.onTimeout(() -> cancelIfNeeded(task));
        emitter.onError(ex -> cancelIfNeeded(task));

        return emitter;
    }

    /** SSE Meteo: snapshot ~ogni 3s */
    @GetMapping(value = "/weather", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWeather() {
        final SseEmitter emitter = new SseEmitter(0L);
        final AtomicBoolean closed = new AtomicBoolean(false);

        Runnable onClose = () -> {
            if (closed.compareAndSet(false, true)) {
                try { emitter.complete(); } catch (Throwable ignored) {}
                logger.info("Client disconnesso dallo stream Weather");
            }
        };
        emitter.onCompletion(onClose);
        emitter.onTimeout(onClose);
        emitter.onError(ex -> onClose.run());

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            try {
                WeatherSnapshot snapshot = weatherService.getCurrentWeather();
                emitter.send(SseEmitter.event().name("weather-update").data(snapshot));
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

    private void cancelIfNeeded(ScheduledFuture<?> f) {
        if (f != null && !f.isCancelled()) f.cancel(true);
    }

    private Map<String, Double> calcKpiPerArea(List<SampleRecord> records,
                                               java.util.function.Function<List<SampleRecord>, Double> kpiFunc) {
        Map<String, List<SampleRecord>> byArea = records.stream()
                .collect(Collectors.groupingBy(SampleRecord::area));

        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, List<SampleRecord>> entry : byArea.entrySet()) {
            result.put(entry.getKey(), kpiFunc.apply(entry.getValue()));
        }
        return result;
    }
}
