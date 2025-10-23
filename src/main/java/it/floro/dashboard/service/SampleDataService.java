package it.floro.dashboard.service;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.simulator.DataSimulator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Service che gestisce il dataset di campioni agricoli.
 *
 * Responsabilità:
 * - Generazione e caricamento lazy del dataset storico (10 anni)
 * - Caching thread-safe dei record in memoria (volatile + sync)
 * - Aggiornamento periodico con dati "live" simulati ogni 10 secondi
 * - Gestione di intervalli temporali (MIN_DATE, MAX_DATE)
 *
 * Architettura:
 * - Lazy initialization: il dataset storico viene generato al primo accesso
 * - Double-checked locking: sincronizzazione ottimizzata per letture frequenti
 * - Live updates schedulati: ogni 10 secondi vengono generati nuovi dati per "oggi"
 * - Cache sostituiva: i dati odierni vecchi vengono rimossi e sostituiti dai nuovi
 */
@Service
public class SampleDataService {

    /**
     * Cache thread-safe dei record simulati.
     *
     * Volatile assicura visibilità tra thread in lettura/scrittura.
     * La sincronizzazione protegge l'operazione di inizializzazione lazy.
     *
     * Nota: In produzione, questa cache potrebbe essere sostituita da
     * una soluzione distribuita (Redis, Hazelcast) per scenari multi-istanza.
     */
    private volatile List<SampleRecord> cached;

    // ========================================================================
    // CONFIGURAZIONE STORICO DATI
    // ========================================================================

    /**
     * Data massima del dataset: il giorno odierno.
     * Usata come riferimento per calcoli di intervalli temporali.
     */
    private static final LocalDate MAX_DATE = LocalDate.now();

    /**
     * Data minima del dataset: 10 anni fa dal primo gennaio.
     * Definisce l'inizio dello storico disponibile per analisi storiche.
     *
     * Esempio: se MAX_DATE = 2025-10-23, MIN_DATE = 2015-01-01
     */
    private static final LocalDate MIN_DATE = MAX_DATE.minusYears(10).withDayOfYear(1);

    /**
     * Numero di campi (aree geografiche) simulati nel dataset.
     * Determina la granularità geografica dei dati (es. 20 zone diverse).
     */
    private static final int DEFAULT_FIELDS = 20;

    // ========================================================================
    // METODI PUBBLICI
    // ========================================================================

    /**
     * Restituisce tutti i record simulati disponibili.
     *
     * Implementa lazy initialization: al primo accesso, genera lo storico completo.
     * I successivi accessi ritornano il dato cachedato.
     *
     * Thread-safe: utilizza double-checked locking per minimizzare sincronizzazione.
     *
     * @return Lista completa di SampleRecord (10 anni × 20 campi ≈ 73.000 record)
     */
    public List<SampleRecord> getAll() {
        ensureDataLoaded();
        return cached;
    }

    /**
     * Forza la rigenerazione completa del dataset storico.
     *
     * Utilità: chiamato da admin/test per resettare i dati oppure
     * per verificare comportamento con seed diversi.
     *
     * Thread-safe: sincronizzato per evitare race condition.
     */
    public synchronized void regenerate() {
        cached = generateHistorical();
    }

    /**
     * 🔴 AGGIORNAMENTO LIVE: Aggiorna il dataset con dati simulati "real-time".
     *
     * Schedulazione: Eseguito automaticamente ogni 10 secondi da Spring.
     *
     * Algoritmo:
     * 1. Se cache non caricata, esce (nulla da aggiornare)
     * 2. Genera nuovi record per "oggi" con seed variabile (basato su currentTimeMillis)
     * 3. Rimuove dalla cache tutti i record odierni precedenti
     * 4. Aggiunge i nuovi record "live" generati
     * 5. Sostituisce atomicamente la cache aggiornata
     *
     * Effetto:
     * - Il dashboard vede dati sempre aggiornati per il giorno corrente
     * - I record storici rimangono intatti
     * - Simula un flusso di dati real-time da stazioni meteorologiche
     *
     * Nota: Il seed variabile (System.currentTimeMillis()) assicura
     * che ogni aggiornamento produca valori leggermente diversi,
     * simulando così sensori che forniscono misurazioni continuamente aggiornate.
     */
    @Scheduled(fixedRate = 10000) // Ogni 10 secondi (10.000 ms)
    public void updateLiveData() {
        // Guard: se la cache non è stata ancora caricata, non c'è nulla da aggiornare
        if (cached == null) return;

        synchronized (this) {
            // Definisce il giorno corrente (sempre "oggi")
            LocalDate today = LocalDate.now();

            // Seed variabile per generare valori sempre diversi
            long liveSeed = System.currentTimeMillis();

            // Crea un simulatore per generare nuovi dati odierni
            DataSimulator liveSimulator = new DataSimulator(
                    liveSeed,           // Seed variabile → valori sempre diversi
                    today,              // Data di generazione: oggi
                    1,                  // Numero di giorni: solo 1 (oggi)
                    DEFAULT_FIELDS      // Numero di campi: 20
            );
            List<SampleRecord> newRecords = liveSimulator.generate();

            // Aggiorna la cache: rimuovi vecchi dati odierni + aggiungi nuovi
            List<SampleRecord> updated = new ArrayList<>(cached);
            updated.removeIf(r -> r.date().equals(today));  // Rimuove record odierni vecchi
            updated.addAll(newRecords);                      // Aggiunge record appena generati

            // Sostituisce la cache atomicamente (volatile write)
            cached = updated;

            System.out.println("✅ Dati aggiornati in tempo reale: " + today);
        }
    }

    /**
     * Ritorna la data minima del dataset disponibile per analisi.
     *
     * @return Data inizio storico (10 anni fa dal 1 gennaio)
     */
    public LocalDate getMinDate() {
        return MIN_DATE;
    }

    /**
     * Ritorna la data massima del dataset disponibile per analisi.
     *
     * @return Data fine storico (giorno odierno)
     */
    public LocalDate getMaxDate() {
        return MAX_DATE;
    }

    // ========================================================================
    // METODI HELPER PRIVATI
    // ========================================================================

    /**
     * Implementa lazy initialization con double-checked locking.
     *
     * Pattern:
     * 1. First check (non sincronizzato): se cached != null, esce
     * 2. Se null, entra nel blocco sincronizzato
     * 3. Second check (sincronizzato): verifica ancora se null (altra thread ha potuto caricarlo)
     * 4. Se ancora null, inizializza il dataset
     *
     * Vantaggio: minimizza lock contention per operazioni di lettura frequenti.
     * Solo il primo accesso ha overhead sincronizzazione; i successivi no.
     *
     * Thread-safety: combinazione di volatile + synchronized garantisce
     * visibilità (volatile) e mutua esclusione (synchronized).
     */
    private void ensureDataLoaded() {
        // First check (non sincronizzato)
        if (cached == null) {
            // Entra sincronizzato solo se effettivamente null
            synchronized (this) {
                // Second check (sincronizzato)
                if (cached == null) {
                    cached = generateHistorical();
                }
            }
        }
    }

    /**
     * Genera il dataset storico completo per i 10 anni.
     *
     * Procedura:
     * 1. Usa seed fisso (42L) per reproducibilità
     * 2. Calcola il numero di giorni tra MIN_DATE e MAX_DATE
     * 3. Crea un DataSimulator per generare record simulati
     * 4. Ritorna la lista di record generati
     *
     * Carico computazionale: generazione singola all'avvio (cache lazy).
     * Una volta generato, i dati rimangono in memoria e vengono solo aggiornati
     * dai nuovi record live ogni 10 secondi.
     *
     * @return Lista di SampleRecord storici (≈ 73.000 record per 10 anni × 20 campi)
     */
    private List<SampleRecord> generateHistorical() {
        // Seed fisso assicura la stessa sequenza di dati a ogni avvio
        long seed = 42L;

        // Calcola giorni da MIN_DATE a MAX_DATE (inclusivo)
        int days = (int) ChronoUnit.DAYS.between(MIN_DATE, MAX_DATE) + 1;

        // Crea simulatore con i parametri della configurazione
        DataSimulator simulator = new DataSimulator(
                seed,               // Seed fisso → sempre stessi dati
                MIN_DATE,           // Inizio: 10 anni fa
                days,               // Numero di giorni: 10 anni ≈ 3.650 giorni
                DEFAULT_FIELDS      // Numero di campi: 20
        );

        return simulator.generate();
    }
}