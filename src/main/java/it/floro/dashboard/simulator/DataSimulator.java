package it.floro.dashboard.simulator;

import it.floro.dashboard.domain.SampleRecord;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulatore di dati agricoli realistici con variabilità stagionale e stocastica.
 *
 * Responsabilità:
 * - Generazione di SampleRecord su un intervallo temporale definito
 * - Simulazione realistica di dati meteo (temperatura, pioggia, umidità, radiazione solare)
 * - Calcolo della resa (yield) con dipendenze da fattori ambientali
 * - Stima del fabbisogno idrico (irrigation demand)
 * - Calcolo di costi agricoli (fixed, inputs, water)
 * - Generazione di prezzi di mercato con shock stocastici
 * - Aggiunta di anomalie casuali (es. alluvioni)
 *
 * Architettura:
 * - Seed fisso (per reproducibilità) + Random internalizzato
 * - Profili di campo invarianti: nome, coltura, area, superficie
 * - Parametri stagionali: temperatura, pioggia, radiazione seguono cicli annuali
 * - Anomalie autocorrelate: temperatura e prezzo seguono processi AR(1)
 * - Correlazioni tra parametri: pioggia → umidità, temperatura ↔ resa
 *
 * Modello economico:
 * - Costo = blend tra costo fisso e target percentuale di revenue
 * - Prezzo = prezzo base + shock stocastico per crop type + rumore
 * - Margine = Prezzo - Costo/Prodotto (calcolato downstream in KpiService)
 */
public class DataSimulator {

    /**
     * Generatore di numeri casuali con seed fisso per reproducibilità.
     * Stesso seed produce sempre la stessa sequenza di dati simulati.
     */
    private final Random rnd;
    /**
     * Data di inizio della simulazione.
     */
    private final LocalDate start;
    /**
     * Numero di giorni da simulare.
     */
    private final int days;
    /**
     * Numero di campi agricoli da simulare.
     */
    private final int fields;

    // ========================================================================
    // PARAMETRI DI CONFIGURAZIONE GLOBALI
    // ========================================================================
    /**
     * Catalogo di colture disponibili per assegnazione casuale ai campi.
     */
    private final String[] crops = {"Grano duro", "Mais", "Olivo", "Vite"};

    /**
     * Catalogo di aree geografiche per assegnazione ai campi.
     */
    private final String[] areas = {"Nord", "Centro", "Sud"};

    // ===== PARAMETRI DI CALCOLO COSTO =====
    /**
     * Target di rapporto costo/revenue desiderato nel sistema.
     * Utilizzato per calcolare blended cost: il costo "ideale" dovrebbe essere
     * il 45% del revenue, per garantire un margine commerciale realistico.
     */
    private static final double COST_SHARE_TARGET   = 0.45;

    /**
     * Peso nel blend tra costo fisso calcolato e costo target percentuale.
     * 0.65 = 65% costo fisso, 35% costo target
     * Valori alti → costi reali seguono i costi fissi
     * Valori bassi → costi reali seguono il target percentuale
     */
    private static final double COST_BALANCE_WEIGHT = 0.65;

    /**
     * Cap massimo del costo come percentuale di revenue.
     * Assicura che il costo non superi mai il 49% del revenue,
     * garantendo un margine minimo del 51%.
     */
    private static final double COST_SHARE_CAP      = 0.49;

    // ========================================================================
    // ARRAY DI PROFILI CAMPI (Invarianti)
    // ========================================================================

    /**
     * Nomi univoci dei campi (es. "A01", "A02", ..., "A20").
     */
    private String[] fieldName;

    /**
     * Coltura assegnata a ogni campo (rimane costante per tutta la simulazione).
     */
    private String[] fieldCrop;

    /**
     * Area geografica assegnata a ogni campo (Nord, Centro, Sud).
     */
    private String[] fieldArea;

    /**
     * Superficie di ogni campo in ettari (rimane costante).
     * Generato randomly in [1.0, 6.0] ha durante init.
     */
    private double[] fieldSurface;

    // ========================================================================
    // COSTRUTTORE
    // ========================================================================

    /**
     * Inizializza il simulatore con parametri di configurazione.
     *
     * @param seed Seed per il generatore Random (stesso seed = stessi dati)
     * @param start Data di inizio della simulazione
     * @param days Numero di giorni da simulare
     * @param fields Numero di campi agricoli
     */
    public DataSimulator(long seed, LocalDate start, int days, int fields) {
        this.rnd = new Random(seed);
        this.start = start;
        this.days = days;
        this.fields = fields;
        initFieldProfiles();  // Crea profili invarianti dei campi
    }

    // ========================================================================
    // METODI UTILITY PRIVATI - MATH BASICS
    // ========================================================================

    /**
     * Vincola un valore in un range [lo, hi].
     *
     * @param v Valore da vincolare
     * @param lo Limite inferiore
     * @param hi Limite superiore
     * @return v se in [lo, hi], lo se v < lo, hi se v > hi
     */
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Vincola un valore in [0.0, 1.0].
     *
     * @param v Valore da vincolare
     * @return v se in [0, 1], 0 se v < 0, 1 se v > 1
     */
    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * Genera un valore sinusoidale con variazione annuale.
     * Utilizzato per modellare cicli stagionali (temperatura, pioggia, radiazione).
     *
     * Formula: base + amp * sin(2π * (day - dayShift) / 365)
     *
     * Parametri:
     * - dayShift: traslazione iniziale (es. 30 = picco dopo 30 giorni)
     * - day: giorno dell'anno (0-364)
     * - amp: ampiezza dell'oscillazione
     * - base: valore base attorno cui oscilla
     *
     * Esempio:
     * - sinYear(30, 0, 10, 12) con day=0 → 12 + 10*sin(-π/6) ≈ 7°C (inverno)
     * - sinYear(30, 100, 10, 12) con day=100 → 12 + 10*sin(π/2) ≈ 22°C (estate)
     *
     * @param dayShift Spostamento del picco nella stagione (giorni)
     * @param day Giorno dell'anno (0-364)
     * @param amp Ampiezza dell'oscillazione
     * @param base Valore base
     * @return Valore sinusoidale per il giorno specificato
     */
    private static double sinYear(int dayShift, int day, double amp, double base) {
        return base + amp * Math.sin(2 * Math.PI * (day - dayShift) / 365.0);
    }

    /**
     * Genera un numero casuale dalla distribuzione Gaussiana (normale).
     * Utilizza il metodo di Box-Muller con variabili uniformi.
     *
     * Formula Box-Muller:
     * Z0 = sqrt(-2 * ln(U1)) * cos(2π * U2)
     * dove U1, U2 ~ Uniform[0, 1)
     *
     * @param mean Media della distribuzione
     * @param std Deviazione standard
     * @return Numero casuale ~ N(mean, std²)
     */
    private double gauss(double mean, double std) {
        double u1 = Math.max(1e-9, rnd.nextDouble());  // Evita log(0)
        double u2 = Math.max(1e-9, rnd.nextDouble());
        double z0 = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
        return mean + std * z0;
    }

    // ========================================================================
    // INIZIALIZZAZIONE PROFILI CAMPI
    // ========================================================================

    /**
     * Inizializza i profili invarianti dei campi agricoli.
     *
     * Procedura:
     * 1. Crea array per nome, coltura, area, superficie
     * 2. Assegna casualmente coltura e area a ogni campo
     * 3. Genera superficie casuale in [1.0, 6.0] ha
     * 4. Garantisce coverage: almeno un campo per area (Nord, Centro, Sud)
     * 5. Garantisce almeno un campo con "Grano duro" per avere produzione di riferimento
     *
     * Invariante: una volta inizializzati, questi profili rimangono uguali
     * per tutta la simulazione (tutti i giorni).
     */
    private void initFieldProfiles() {
        fieldName    = new String[fields];
        fieldCrop    = new String[fields];
        fieldArea    = new String[fields];
        fieldSurface = new double[fields];

        // Assegna casualmente nome, coltura, area, superficie a ogni campo
        for (int f = 0; f < fields; f++) {
            fieldName[f]    = "A" + String.format("%02d", f + 1);           // A01, A02, ...
            fieldCrop[f]    = crops[rnd.nextInt(crops.length)];             // Random crop
            fieldArea[f]    = areas[rnd.nextInt(areas.length)];             // Random area
            fieldSurface[f] = 1.0 + 5.0 * rnd.nextDouble();                 // [1.0, 6.0] ha
        }

        // Garantisce almeno un campo per ogni area geografica
        // (importante per analisi disaggregate per area)
        if (fields >= 3) {
            fieldArea[0] = "Nord";
            fieldArea[1] = "Centro";
            fieldArea[2] = "Sud";
        } else if (fields == 2) {
            fieldArea[0] = "Nord";
            fieldArea[1] = "Sud";
        } else if (fields == 1) {
            fieldArea[0] = "Sud";
        }

        // Garantisce almeno un campo con "Grano duro" (coltura di riferimento)
        if (fields > 0) fieldCrop[0] = "Grano duro";
    }

    // ========================================================================
    // LOOKUP FUNCTIONS - Bias/Parametri per Area Geografica
    // ========================================================================

    /**
     * Ritorna il bias di temperatura per un'area geografica.
     * Modella le differenze climatiche Nord-Sud Italia.
     *
     * Bias:
     * - Nord: -1.5°C (più freddo)
     * - Centro: 0°C (riferimento)
     * - Sud: +1.5°C (più caldo)
     *
     * @param area Area geografica ("Nord", "Centro", "Sud")
     * @return Bias di temperatura in °C
     */
    private double areaTempBias(String area) {
        return switch (area) {
            case "Nord"   -> -1.5;
            case "Centro" ->  0.0;
            default       ->  1.5;  // Sud
        };
    }

    /**
     * Ritorna il bias di probabilità di pioggia per un'area geografica.
     * Modella i diversi pattern pluviometrici Nord-Sud.
     *
     * Bias:
     * - Nord: +0.06 (più umido, più pioggia)
     * - Centro: +0.04
     * - Sud: +0.03 (più secco, meno pioggia)
     *
     * @param area Area geografica ("Nord", "Centro", "Sud")
     * @return Bias additivo sulla probabilità di pioggia
     */
    private double areaRainProbBias(String area) {
        return switch (area) {
            case "Nord"   -> 0.06;
            case "Centro" -> 0.04;
            default       -> 0.03;  // Sud
        };
    }

    // ========================================================================
    // LOOKUP FUNCTIONS - Parametri per Coltura
    // ========================================================================

    /**
     * Ritorna lo spostamento di fase della coltura nel ciclo annuale.
     * Modella il timing biologico della crescita per ogni coltura.
     *
     * Shift (in giorni) da inizio anno al picco di crescita:
     * - Grano duro: 60 (picco crescita a marzo-aprile)
     * - Mais: 90 (picco crescita a aprile-maggio)
     * - Olivo: 120 (ciclo più lento, picco a fine primavera)
     * - Vite: 100 (ciclo intermedio)
     *
     * @param crop Coltura ("Grano duro", "Mais", "Olivo", "Vite")
     * @return Spostamento di fase in giorni
     */
    private int cropPhaseShift(String crop) {
        return switch (crop) {
            case "Grano duro" -> 60;
            case "Mais"       -> 90;
            case "Olivo"      -> 120;
            default           -> 100;  // Vite
        };
    }

    /**
     * Ritorna la resa base della coltura in assenza di stress ambientali.
     * Modella il potenziale produttivo intrinseco di ogni coltura.
     *
     * Resa (t/ha) in condizioni ottimali:
     * - Grano duro: 4.5 t/ha (moderata)
     * - Mais: 7.0 t/ha (alta)
     * - Olivo: 2.2 t/ha (bassa, ciclo più lungo)
     * - Vite: 9.0 t/ha (elevata)
     *
     * @param crop Coltura
     * @return Resa potenziale in t/ha
     */
    private double baseYieldPerHa(String crop) {
        return switch (crop) {
            case "Grano duro" -> 4.5;
            case "Mais"       -> 7.0;
            case "Olivo"      -> 2.2;
            default           -> 9.0;  // Vite
        };
    }

    /**
     * Ritorna la temperatura ottimale per la resa della coltura.
     * Modella la dipendenza di ogni coltura da specifiche condizioni termiche.
     *
     * Temperatura ottimale (°C):
     * - Grano duro: 18°C (coltura fredda)
     * - Mais: 22°C (coltura calda)
     * - Olivo: 24°C (clima mediterraneo)
     * - Vite: 20°C (clima temperato)
     *
     * @param crop Coltura
     * @return Temperatura ottimale in °C
     */
    private double cropTempOpt(String crop) {
        return switch (crop) {
            case "Grano duro" -> 18;
            case "Mais"       -> 22;
            case "Olivo"      -> 24;
            default           -> 20;  // Vite
        };
    }

    /**
     * Calcola il fattore di adattabilità della coltura a una temperatura.
     * Modella come la resa cala se la temperatura si allontana dall'ottimale.
     *
     * Formula: Distribuzione normale (Gaussiana)
     * S(T) = exp(-0.5 * ((T - T_opt) / sigma)²)
     *
     * Proprietà:
     * - S(T_opt) = 1.0 (massima adattabilità a temperatura ottimale)
     * - Cala quadraticamente allontanandosi da T_opt
     * - sigma = 7°C determina la "tolleranza termica"
     *
     * Esempio per Grano duro (T_opt = 18°C):
     * - T = 18°C → S = 1.0 (100%)
     * - T = 25°C → S = 0.73 (73%)
     * - T = 32°C → S = 0.29 (29%, molto inadatto)
     *
     * @param t Temperatura attuale in °C
     * @param crop Coltura
     * @return Fattore di adattabilità [0, 1]
     */
    private double tempSuitability(double t, String crop) {
        double mu = cropTempOpt(crop);  // Temperatura ottimale
        double sigma = 7.0;             // Tolleranza termica (std dev)
        double z = (t - mu) / sigma;
        return Math.exp(-0.5 * z * z);  // Campana Gaussiana
    }

    /**
     * Calcola il fattore di moltiplicazione della resa in base alla pioggia.
     * Modella come la disponibilità d'acqua influenza la produttività.
     *
     * Formula: f(rain) = 0.6 + 0.6 * ln(1 + rain) / ln(1 + 30)
     * Intervallo: [0.4, 1.2]
     *
     * Proprietà:
     * - rain = 0 mm → f ≈ 0.6 (secco: 60% della resa)
     * - rain = 10 mm → f ≈ 0.88 (moderato)
     * - rain = 30 mm → f ≈ 1.0 (ottimale: 100%)
     * - rain > 30 mm → f → 1.2 (capped a 1.2, eccesso d'acqua riduce resa)
     *
     * Interpretazione: pioggia aumenta la resa fino a ~30 mm, oltre il quale
     * l'eccesso d'acqua causa ristagni e riduce i benefici.
     *
     * @param rainMm Precipitazioni in mm
     * @return Fattore moltiplicativo della resa [0.4, 1.2]
     */
    private double rainFactor(double rainMm) {
        double f = 0.6 + 0.6 * Math.log1p(rainMm) / Math.log1p(30.0);
        return clamp(f, 0.4, 1.2);
    }

    /**
     * Ritorna la lunghezza della stagione di coltivazione (ciclo biologico).
     * Utilizzato per ammortizzare i costi fissi su un numero di giorni realistico.
     *
     * Lunghezza stagione (giorni):
     * - Grano duro: 150 giorni (ciclo breve)
     * - Mais: 140 giorni (ciclo breve)
     * - Olivo: 210 giorni (ciclo lungo, coltivazione intensiva)
     * - Vite: 200 giorni (ciclo lungo)
     *
     * @param crop Coltura
     * @return Lunghezza della stagione in giorni
     */
    private int seasonDays(String crop) {
        return switch (crop) {
            case "Grano duro" -> 150;
            case "Mais"       -> 140;
            case "Olivo"      -> 210;
            default           -> 200;  // Vite
        };
    }

    /**
     * Ritorna il costo fisso per ettaro per l'intera stagione di coltivazione.
     * Comprende: preparazione terreno, semina, manutenzione, machinery depreciation.
     *
     * Costi fissi (€/ha per stagione):
     * - Grano duro: 1150 (coltura poco intensiva)
     * - Mais: 1260 (meccanizzazione più spinta)
     * - Olivo: 1470 (irrigazione, potature)
     * - Vite: 2310 (alta intensività di manodopera e irrigazione)
     *
     * @param crop Coltura
     * @return Costo fisso in €/ha per la stagione
     */
    private double fixedCostPerHaSeason(String crop) {
        return switch (crop) {
            case "Grano duro" -> 1150;
            case "Mais"       -> 1260;
            case "Olivo"      -> 1470;
            default           -> 2310;  // Vite
        };
    }

    /**
     * Ritorna il costo dei prodotti/input per ettaro per l'intera stagione.
     * Comprende: sementi, fertilizzanti, pesticidi, fungicidi.
     *
     * Costi input (€/ha per stagione):
     * - Grano duro: 410 (input minimi, clima secco-tollerante)
     * - Mais: 485 (input più alti, coltura esigente)
     * - Olivo: 378 (input moderati, coltura resiliente)
     * - Vite: 562 (input elevati, vigneto gestito intensivamente)
     *
     * @param crop Coltura
     * @return Costo input in €/ha per la stagione
     */
    private double inputsPerHaSeason(String crop) {
        return switch (crop) {
            case "Grano duro" -> 410;
            case "Mais"       -> 485;
            case "Olivo"      -> 378;
            default           -> 562;  // Vite
        };
    }

    /**
     * Ritorna il costo dell'acqua per metro cubo.
     * Comprende: costo di approvvigionamento + distribuzione + energia di pompaggio.
     *
     * @return Costo acqua in €/m³
     */
    private double waterCostPerM3() {
        return 0.20;
    }

    // ========================================================================
    // METODO PRINCIPALE: GENERAZIONE DATASET
    // ========================================================================

    /**
     * Genera l'intero dataset simulato per il periodo e i campi definiti.
     *
     * Struttura:
     * 1. INIZIALIZZAZIONE PARAMETRI STAGIONALI E STOCASTICI
     *    - Anomalia di temperatura (AR(1) process)
     *    - Shock di prezzo per ogni coltura (AR(1) process separato)
     *    - Probabilità stagionale di pioggia, radiazione solare
     *
     * 2. LOOP TEMPORALE: Per ogni giorno della simulazione
     *    - Calcola ciclo stagionale base (temperatura, pioggia, sole)
     *    - Aggiorna anomalie autocorrelate
     *
     * 3. LOOP CAMPI: Per ogni campo agricolo
     *    - PARAMETRI METEO: temperatura, pioggia, umidità, radiazione
     *    - RESA: applica fattori meteo, stagionalità biologica
     *    - FABBISOGNO IDRICO: calcola domanda evapotraspirazione
     *    - COSTI: fissi, input, acqua; blending con revenue target
     *    - PREZZO: prezzo base + shock + rumore
     *    - ANOMALIE: 1% probabilità di evento (alluvione/parassiti)
     *
     * Parametri AR(1) per autocorrelazione:
     * - phiT = 0.6: temperature consecutive correlate al 60%
     * - phiP = 0.7: prezzi consecutivi correlati al 70%
     * - sigmaT = 1.8: volatilità della temperatura (~°C)
     * - sigmaP = 0.01: volatilità del prezzo (1%)
     *
     * @return Lista di SampleRecord generati (giorni × campi record totali)
     */
    public List<SampleRecord> generate() {
        List<SampleRecord> out = new ArrayList<>();

        // ===== VARIABILI DI STATO TEMPORALE (Autocorrelate) =====
        double tempAnom = 0.0;  // Anomalia temperatura persistente
        double priceShockWheat = 0.0, priceShockMais = 0.0, priceShockOlivo = 0.0, priceShockVite = 0.0;

        // ===== PARAMETRI AR(1) =====
        // Processo AR(1): X_t = phi * X_{t-1} + epsilon_t, epsilon ~ N(0, sigma²)
        double phiT = 0.6, sigmaT = 1.8;  // Temperature autocorrelation
        double phiP = 0.7, sigmaP = 0.01; // Price shock autocorrelation

        // ========== LOOP TEMPORALE: Per ogni giorno ===========
        for (int d = 0; d < days; d++) {
            LocalDate date = start.plusDays(d);

            // ===== PARAMETRI STAGIONALI (dipendono solo dal giorno dell'anno) =====

            // Temperatura stagionale: sinusoide con picco a fine giugno
            // Range tipico: [2°C in inverno, 22°C in estate]
            double tempSeason = sinYear(30, d, 10, 12);

            // Anomalia di temperatura con autocorrelazione (processo AR(1))
            // Rappresenta "onde di caldo/freddo" che durano più giorni
            tempAnom = phiT * tempAnom + gauss(0, sigmaT);

            // Probabilità stagionale di pioggia: picco in autunno/inverno
            // Range: [0.02, 0.45] per evitare estremi
            double seasonRainProb = clamp(
                    0.15 + 0.10 * Math.cos(2 * Math.PI * (d - 100) / 365.0),
                    0.02, 0.45
            );

            // Radiazione solare stagionale: massima in estate, minima in inverno
            // Range normalizzato: [0.1, 1.0]
            double solarSeason = clamp(
                    0.45 + 0.35 * Math.sin(2 * Math.PI * (d - 10) / 365.0),
                    0.1, 1.0
            );

            // ========== LOOP SU CAMPI: Per ogni campo agricolo ===========
            for (int f = 0; f < fields; f++) {
                String field   = fieldName[f];
                String crop    = fieldCrop[f];
                String area    = fieldArea[f];
                double surface = fieldSurface[f];

                // ===== SEZIONE 1: PARAMETRI METEO =====

                // TEMPERATURA (°C): stagionale + anomalia + bias areale
                double temp = tempSeason + tempAnom + areaTempBias(area);

                // PIOGGIA (mm):
                // 1. Calcola probabilità locale di pioggia
                double pRain = clamp(
                        seasonRainProb + areaRainProbBias(area),
                        0, 0.75
                );
                // 2. Decidi se piove (Bernoulli trial)
                boolean rains = rnd.nextDouble() < pRain;
                // 3. Se piove, genera ammontare da lognormale (code pesante, eventi rari)
                double rain = 0.0;
                if (rains) {
                    double mu = Math.log(4.0), sigma = 0.6;
                    rain = Math.max(0, Math.exp(gauss(mu, sigma)) - 1.0);
                }

                // UMIDITÀ (%): sinusoide annuale + effetto pioggia + correlazione temperatura
                // Relazione: pioggia → +10%, secco → -3%, caldo → -0.3% per °C sopra 20°C
                double humBase = sinYear(-60, d, 12, 62);
                double hum = clamp(
                        humBase + (rains ? 10 : -3) - 0.3 * (temp - 20),
                        25, 95
                );

                // RADIAZIONE SOLARE (normalizzata 0-1):
                // - Base stagionale, ridotta dal nuvolamento causato da pioggia
                // - Effetto pioggia: ogni 25 mm di pioggia riduce radiazione del 70%
                double solar = clamp(
                        solarSeason * (1.0 - clamp(rain / 25.0, 0, 0.7)),
                        0.05, 1.0
                );

                // ===== SEZIONE 2: CALCOLO DELLA RESA (t/ha) =====
                // Formula: Yield = BaseYield * SeasonalBoost * RainFactor * TempFactor * SolarFactor + rumore

                double baseYield = baseYieldPerHa(crop);

                // Boost stagionale: la coltura ha resa diversa a diversi momenti dell'anno
                // Picco nel periodo di crescita (fase biologica determinata da cropPhaseShift)
                double seasonBoost = 1 + 0.25 * Math.sin(
                        2 * Math.PI * (d - cropPhaseShift(crop)) / 365.0
                );

                // Fattore pioggia: [0.4, 1.2] con picco a ~30 mm
                double fRain = rainFactor(rain);

                // Fattore temperatura: blending tra 0.7 (temperature pessime) e 1.0 (ottimale)
                double fTemp = 0.7 + 0.3 * tempSuitability(temp, crop);

                // Fattore radiazione solare: blending tra 0.85 (soleggiato minimo) e 1.0 (massima luce)
                double fSolar = 0.85 + 0.15 * solar;

                // Resa finale con rumore Gaussiano (±0.35 t/ha variabilità casuale)
                double yieldPerHa = Math.max(0.0,
                        gauss(baseYield * seasonBoost * fRain * fTemp * fSolar, 0.35)
                );

                // Resa totale: per ettaro × superficie
                double totalYield = yieldPerHa * surface;

                // ===== SEZIONE 3: FABBISOGNO IDRICO (m³) =====
                // Modello semplificato di evapotraspirazione potenziale

                // Domanda base: 70 mm/gg, aumenta di 4 mm per ogni °C sopra 20°C
                double demand = 70 * (1 + 0.04 * Math.max(temp - 20, 0));

                // Pioggia riduce il fabbisogno irriguo (log curve)
                double irrigationNeed = Math.max(0, demand - 20 * Math.log1p(rain));

                // Acqua effettiva da irrigare: irrigationNeed × superficie + rumore
                double waterM3 = Math.max(0.0, gauss(irrigationNeed * surface, 18));

                // ===== SEZIONE 4: CALCOLO COSTI =====
                // Componenti: fissi, input, acqua; poi blending con target revenue

                int sDays = seasonDays(crop);
                double fixedSeasonPerHa  = fixedCostPerHaSeason(crop);
                double inputsSeasonPerHa = inputsPerHaSeason(crop);

                // Costo fisso giornaliero = (costo stagione / giorni stagione) × superficie
                double fixedCostDaily  = (fixedSeasonPerHa  / sDays) * surface;

                // Costo input giornaliero = (costo stagione / giorni stagione) × superficie
                double inputsCostDaily = (inputsSeasonPerHa / sDays) * surface;

                // Costo acqua = volume irrigato × prezzo unitario
                double waterCost       = waterM3 * waterCostPerM3();

                // Costo base giornaliero: somma di tutte le componenti
                double baseCost        = Math.max(0.0, fixedCostDaily + inputsCostDaily + waterCost);

                // ===== SEZIONE 5: CALCOLO PREZZO DI MERCATO =====

                // Prezzo base per la coltura (€/t)
                double basePrice = switch (crop) {
                    case "Grano duro" -> 280;
                    case "Mais"       -> 250;
                    case "Olivo"      -> 450;
                    default           -> 600;  // Vite
                };

                // AGGIORNAMENTO SHOCK DI PREZZO con autocorrelazione AR(1)
                // Ogni coltura ha il suo shock persistente indipendente
                // Rappresenta shocks macroeconomici (crisi commerciale, calamità globali)
                switch (crop) {
                    case "Grano duro" -> priceShockWheat = phiP * priceShockWheat + gauss(0, sigmaP);
                    case "Mais"       -> priceShockMais  = phiP * priceShockMais  + gauss(0, sigmaP);
                    case "Olivo"      -> priceShockOlivo = phiP * priceShockOlivo + gauss(0, sigmaP);
                    case "Vite"       -> priceShockVite  = phiP * priceShockVite  + gauss(0, sigmaP);
                }

                // Recupera lo shock applicabile a questa coltura
                double shock = switch (crop) {
                    case "Grano duro" -> priceShockWheat;
                    case "Mais"       -> priceShockMais;
                    case "Olivo"      -> priceShockOlivo;
                    default           -> priceShockVite;
                };

                // Prezzo finale = prezzo base × (1 + shock) + rumore giornaliero
                // rumore ~ N(0, 8) rappresenta variabilità spot market
                double price = basePrice * (1.0 + shock) + gauss(0, 8);

                // ===== SEZIONE 6: BLENDING COSTO VERSO TARGET ECONOMICO =====
                // Modello: il costo "reale" è blending tra:
                // - Costo operativo calcolato (baseCost)
                // - Costo target come percentuale di revenue (targetCost)

                double revenue     = Math.max(0.0, price * totalYield);

                // Costo target: COST_SHARE_TARGET (45%) del revenue
                // Rappresenta il margine commerciale desiderato nel sistema
                double targetCost  = COST_SHARE_TARGET * revenue;

                // Blending: 65% peso a baseCost, 35% a targetCost
                // Valori alti di COST_BALANCE_WEIGHT → segui costi reali
                // Valori bassi → segui target percentuale di revenue
                double blendedCost = (1.0 - COST_BALANCE_WEIGHT) * baseCost + COST_BALANCE_WEIGHT * targetCost;

                // Cap massimo: il costo non può superare COST_SHARE_CAP (49%) del revenue
                // Garantisce margine minimo del 51%
                double costCap = (revenue > 0) ? COST_SHARE_CAP * revenue : blendedCost;

                // Floor minimo: il costo deve coprire almeno i costi fissi + input
                // Non può scendere sotto i costi effettivamente sostenuti
                double minCostFloor = fixedCostDaily + inputsCostDaily;

                // Applica vincoli
                double cost = blendedCost;
                cost = Math.max(minCostFloor, cost);      // Minimo floor
                if (revenue > 0) cost = Math.min(cost, costCap);  // Massimo cap

                // ===== SEZIONE 7: ANOMALIE CASUALI (1% probabilità) =====
                // Simula eventi rari: alluvioni, infestazioni parassitarie, gelate tardive
                // Effetti: +50% resa, +40% acqua, +20% costo
                if (rnd.nextDouble() < 0.01) {
                    totalYield *= 1.5;
                    waterM3    *= 1.4;
                    cost       *= 1.2;
                }

                // ===== CREAZIONE E AGGIUNTA RECORD =====
                out.add(new SampleRecord(
                        date, area, field, crop, surface,
                        temp, hum, rain, solar,
                        totalYield, waterM3, cost, price
                ));
            }
        }

        return out;
    }
}