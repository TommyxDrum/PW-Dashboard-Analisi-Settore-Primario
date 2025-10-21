package it.floro.dashboard.service;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.simulator.DataSimulator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service che fornisce i dati simulati (SampleRecord) con cache in memoria.
 * Sostituisce la logica ensureData... duplicata nei controller.
 */
@Service
public class SampleDataService {

    private volatile List<SampleRecord> cached;

    private static final long DEFAULT_SEED = 42L;
    private static final int DEFAULT_FIELDS = 20;
    private static final LocalDate MAX_DATE = LocalDate.now();
    private static final LocalDate MIN_DATE = MAX_DATE.minusYears(10).withDayOfYear(1);

    /** Restituisce tutti i record simulati (cache in memoria). */
    public List<SampleRecord> getAll() {
        ensureDataLoaded();
        return cached;
    }

    /** Forza una rigenerazione completa dei dati simulati. */
    public void regenerate() {
        synchronized (this) {
            cached = generate(DEFAULT_SEED, MIN_DATE, MAX_DATE, DEFAULT_FIELDS);
        }
    }

    public LocalDate getMinDate() {
        return MIN_DATE;
    }

    public LocalDate getMaxDate() {
        return MAX_DATE;
    }

    // ================== METODI PRIVATI ==================

    private void ensureDataLoaded() {
        if (cached == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = generate(DEFAULT_SEED, MIN_DATE, MAX_DATE, DEFAULT_FIELDS);
                }
            }
        }
    }

    private List<SampleRecord> generate(long seed, LocalDate start, LocalDate end, int fields) {
        int days = (int) ChronoUnit.DAYS.between(start, end) + 1;
        DataSimulator simulator = new DataSimulator(seed, start, days, fields);
        return simulator.generate();
    }
}
