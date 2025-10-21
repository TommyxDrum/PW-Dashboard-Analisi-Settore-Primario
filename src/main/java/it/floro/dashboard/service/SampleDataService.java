package it.floro.dashboard.service;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.simulator.DataSimulator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class SampleDataService {

    private volatile List<SampleRecord> cached;

    // Parametri storico (10 anni)
    private static final LocalDate MAX_DATE = LocalDate.now();
    private static final LocalDate MIN_DATE = MAX_DATE.minusYears(10).withDayOfYear(1);
    private static final int DEFAULT_FIELDS = 20;

    /** Restituisce tutti i record simulati */
    public List<SampleRecord> getAll() {
        ensureDataLoaded();
        return cached;
    }

    /** Forza rigenerazione completa */
    public synchronized void regenerate() {
        cached = generateHistorical();
    }

    /** 🔴 NUOVO: Aggiorna dati "live" ogni 10 secondi */
    @Scheduled(fixedRate = 10000) // 10 secondi
    public void updateLiveData() {
        if (cached == null) return;

        synchronized (this) {
            // Simula che arrivino nuovi dati "oggi"
            LocalDate today = LocalDate.now();
            long liveSeed = System.currentTimeMillis(); // Seed variabile

            DataSimulator liveSimulator = new DataSimulator(
                    liveSeed, today, 1, DEFAULT_FIELDS
            );
            List<SampleRecord> newRecords = liveSimulator.generate();

            // Rimuovi dati vecchi di oggi e aggiungi i nuovi
            List<SampleRecord> updated = new ArrayList<>(cached);
            updated.removeIf(r -> r.date().equals(today));
            updated.addAll(newRecords);

            cached = updated;

            System.out.println("✅ Dati aggiornati in tempo reale: " + today);
        }
    }

    public LocalDate getMinDate() {
        return MIN_DATE;
    }

    public LocalDate getMaxDate() {
        return MAX_DATE;
    }

    // ========== PRIVATI ==========

    private void ensureDataLoaded() {
        if (cached == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = generateHistorical();
                }
            }
        }
    }

    private List<SampleRecord> generateHistorical() {
        long seed = 42L;
        int days = (int) ChronoUnit.DAYS.between(MIN_DATE, MAX_DATE) + 1;
        DataSimulator simulator = new DataSimulator(seed, MIN_DATE, days, DEFAULT_FIELDS);
        return simulator.generate();
    }
}