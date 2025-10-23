package it.floro.dashboard.service;

import it.floro.dashboard.web.dto.WeatherSnapshot;
import it.floro.dashboard.web.dto.WeatherSnapshot.EnvironmentalData;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Service che fornisce dati meteo simulati e realistici per il dashboard.
 *
 * Responsabilità:
 * - Generazione di dati meteo "real-time" per le tre aree geografiche (Nord, Centro, Sud)
 * - Simulazione di variabilità circadiana (variazione temperatura nel corso della giornata)
 * - Correlazione tra parametri meteo (es. umidità legata a temperatura)
 * - Aggiunta di rumore realistico (variazioni random) a valori base
 * - Arrotondamento e vincolo dei valori in range validi
 *
 * Caratteristiche della simulazione:
 * - Ogni area ha un profilo climatico distintivo (Nord continentale, Centro temperato, Sud mediterraneo)
 * - Radiazione solare segue il ciclo circadiano del sole (picco a mezzogiorno)
 * - Temperatura varia nel corso della giornata (minimo al mattino, picco pomeridiano)
 * - Umidità è inversamente correlata alla temperatura (minore quando caldo, maggiore quando freddo)
 * - Precipitazioni generate con probabilità del 10%
 *
 * Nota: Questo service fornisce dati simulati per demo/test. In produzione,
 * potrebbe integrare API di dati meteo reali (es. OpenWeatherMap, Meteo.it).
 */
@Service
public class WeatherService {

    /**
     * Generatore di numeri casuali per aggiungere variabilità realistica ai dati.
     * Utilizzato per simulare fluttuazioni naturali dei parametri meteo.
     */
    private final Random random = new Random();

    // ========================================================================
    // METODI PUBBLICI
    // ========================================================================

    /**
     * Genera uno snapshot dei dati meteo correnti per tutte le aree geografiche.
     *
     * Restituisce:
     * - Dati meteo per Nord, Centro, Sud
     * - Timestamp attuale (LocalDateTime.now())
     *
     * Profili climatici simulati:
     * - NORD (Continentale): Intervallo [12°C, 25°C], umidità 65%, vento 15 km/h
     * - CENTRO (Temperato): Intervallo [15°C, 28°C], umidità 55%, vento 12 km/h
     * - SUD (Mediterraneo): Intervallo [18°C, 32°C], umidità 45%, vento 18 km/h
     *
     * @return WeatherSnapshot contenente EnvironmentalData per ogni area e timestamp
     */
    public WeatherSnapshot getCurrentWeather() {
        // Mappa area → dati meteo
        Map<String, EnvironmentalData> dataPerArea = new HashMap<>();

        // ===== NORD: Clima continentale =====
        // Caratterizzato da escursione termica più ampia e umidità moderata
        dataPerArea.put("Nord", generateWeatherData(12, 25, 65, 15));

        // ===== CENTRO: Clima temperato =====
        // Caratterizzato da temperature moderate e umidità intermedia
        dataPerArea.put("Centro", generateWeatherData(15, 28, 55, 12));

        // ===== SUD: Clima mediterraneo =====
        // Caratterizzato da temperature più elevate, bassa umidità e vento più forte
        dataPerArea.put("Sud", generateWeatherData(18, 32, 45, 18));

        return new WeatherSnapshot(dataPerArea, LocalDateTime.now());
    }

    // ========================================================================
    // METODI HELPER PRIVATI
    // ========================================================================

    /**
     * Genera dati ambientali realistici per un'area con variabilità circadiana.
     *
     * Parametri di input (valori base):
     * @param baseTempMin Temperatura minima della giornata (ore mattutine)
     * @param baseTempMax Temperatura massima della giornata (ore pomeridiane)
     * @param baseHumidity Umidità media (viene poi correlata alla temperatura)
     * @param baseWind Velocità del vento media
     *
     * Algoritmi di generazione:
     *
     * 1. TEMPERATURA (°C)
     *    Formula: midpoint + sin(variazione circadiana) + rumore
     *    - Midpoint = (min + max) / 2
     *    - sin() segue il ciclo solare: minimo alle 6:00, massimo alle 14:00, minimo alle 22:00
     *    - Ampiezza circadiana: ±5°C
     *    - Rumore aggiunto: ±2°C (variabilità casuale)
     *
     * 2. UMIDITÀ RELATIVA (%)
     *    Formula: base + (25 - temp) * 1.5 + rumore, vincolo [30%, 95%]
     *    - Inversa rispetto alla temperatura (quando sale temp, umidità cala)
     *    - Coefficiente 1.5 rappresenta la correlazione psicrometrica
     *    - Rumore: ±5%
     *    - Range fisico: [30%, 95%] per evitar valori fisicamente impossibili
     *
     * 3. PRECIPITAZIONI (mm)
     *    - Probabilità pioggia: 10% (random < 0.1)
     *    - Se piogge: 0-5 mm casuali
     *    - Se no piogge: 0 mm
     *
     * 4. VENTO (km/h)
     *    Formula: base + rumore, vincolo [0, ∞)
     *    - Rumore: ±5 km/h (raffiche)
     *    - Minimo fisico: 0 km/h (calma)
     *
     * 5. RADIAZIONE SOLARE (W/m²)
     *    Formula: 800 * sin(ciclo giornaliero) + rumore, vincolo [0, ∞)
     *    - Ciclo giornaliero: sin segue il sole (0 W/m² all'alba/tramonto, picco 800 W/m² a mezzogiorno)
     *    - Picco massimo: 800 W/m² (radiazione solare medio-alta)
     *    - Rumore: ±100 W/m²
     *    - Minimo fisico: 0 W/m²
     *
     * 6. CONDIZIONI METEO (categoria)
     *    - Pioggia: se precipitazioni > 1 mm
     *    - Nuvoloso: se umidità > 70%
     *    - Soleggiato: altrimenti
     *
     * @return EnvironmentalData con tutti i parametri meteo calcolati
     */
    private EnvironmentalData generateWeatherData(double baseTempMin, double baseTempMax,
                                                  double baseHumidity, double baseWind) {

        // ===== 1. CALCOLO TEMPERATURA CON VARIAZIONE CIRCADIANA =====
        // L'ora attuale (0-23)
        int hour = LocalDateTime.now().getHour();

        // Variazione circadiana: sin wave che ha:
        // - Minimo (-5°C) alle 6:00 e alle 22:00
        // - Zero (0°C) alle 12:00 e alle 6:00/18:00
        // - Massimo (+5°C) alle 14:00
        double tempVariation = Math.sin((hour - 6) * Math.PI / 12) * 5;

        // Temperatura = media + variazione circadiana + rumore
        double midpoint = (baseTempMin + baseTempMax) * 0.5;  // Media tra min e max
        double temp = midpoint + tempVariation + randomNoise(2);

        // ===== 2. CALCOLO UMIDITÀ (INVERSAMENTE PROPORZIONALE A TEMPERATURA) =====
        // Relazione psicrometrica: umidità maggiore quando fa freddo, minore quando fa caldo
        // Coefficiente 1.5 rappresenta la pendenza della correlazione
        double humidity = baseHumidity + (25 - temp) * 1.5 + randomNoise(5);
        // Vincolo: umidità deve stare in range fisicamente valido [30%, 95%]
        humidity = Math.max(30, Math.min(95, humidity));

        // ===== 3. CALCOLO PRECIPITAZIONI (PROBABILISTICA) =====
        // 10% di probabilità di pioggia in un qualsiasi momento
        double rain = random.nextDouble() < 0.1 ? random.nextDouble() * 5 : 0;

        // ===== 4. CALCOLO VENTO =====
        // Vento base + variabilità (raffiche)
        double wind = baseWind + randomNoise(5);
        // Vincolo: il vento non può essere negativo
        wind = Math.max(0, wind);

        // ===== 5. CALCOLO RADIAZIONE SOLARE (CICLO GIORNALIERO) =====
        // La radiazione solare segue il sole: max a mezzogiorno, min all'alba/tramonto
        // Funzione sin: picco 800 W/m² a mezzanotte (hour=12)
        double solar = Math.max(0, 800 * Math.sin((hour - 6) * Math.PI / 12) + randomNoise(100));

        // ===== 6. CLASSIFICAZIONE CONDIZIONI METEO =====
        String conditions;
        if (rain > 1) {
            conditions = "Pioggia";           // Pioggia significativa (> 1 mm)
        } else if (humidity > 70) {
            conditions = "Nuvoloso";          // Alta umidità → cielo coperto
        } else {
            conditions = "Soleggiato";        // Bassa umidità + bassa pioggia → sole
        }

        // ===== COSTRUZIONE RECORD CON VALORI ARROTONDATI =====
        return new EnvironmentalData(
                roundTo(temp, 1),             // Temperatura: 1 decimale (es. 22.5°C)
                roundTo(humidity, 0),         // Umidità: interi (es. 65%)
                roundTo(rain, 1),             // Pioggia: 1 decimale (es. 2.3 mm)
                roundTo(wind, 1),             // Vento: 1 decimale (es. 12.5 km/h)
                roundTo(solar, 0),            // Radiazione: interi (es. 650 W/m²)
                conditions
        );
    }

    /**
     * Genera rumore casuale in range [-amplitude, +amplitude].
     *
     * Algoritmo:
     * - random.nextDouble() ritorna [0, 1)
     * - (random - 0.5) ritorna [-0.5, 0.5)
     * - * 2 ritorna [-1, 1)
     * - * amplitude ritorna [-amplitude, +amplitude]
     *
     * Usato per aggiungere variabilità realistica ai parametri calcolati.
     *
     * @param amplitude Ampiezza massima del rumore (es. 2 per ±2°C)
     * @return Valore casuale in [-amplitude, +amplitude]
     */
    private double randomNoise(double amplitude) {
        return (random.nextDouble() - 0.5) * 2 * amplitude;
    }

    /**
     * Arrotonda un valore double a un numero specificato di decimali.
     *
     * Formula:
     * 1. Moltiplica per 10^decimals (es. 10 per 1 decimale)
     * 2. Arrotonda all'intero più vicino con Math.round()
     * 3. Divide per 10^decimals
     *
     * Esempi:
     * - roundTo(22.456, 1) → 22.5
     * - roundTo(65.789, 0) → 66
     * - roundTo(12.3456, 2) → 12.35
     *
     * @param value Valore da arrotondare
     * @param decimals Numero di decimali desiderati (0 = intero)
     * @return Valore arrotondato
     */
    private double roundTo(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}