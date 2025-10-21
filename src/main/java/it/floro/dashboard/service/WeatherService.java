package it.floro.dashboard.service;

import it.floro.dashboard.web.dto.WeatherSnapshot;
import it.floro.dashboard.web.dto.WeatherSnapshot.EnvironmentalData;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class WeatherService {

    private final Random random = new Random();

    /**
     * Simula dati meteo in tempo reale per ogni area
     */
    public WeatherSnapshot getCurrentWeather() {
        Map<String, EnvironmentalData> dataPerArea = new HashMap<>();

        // Nord - Clima continentale
        dataPerArea.put("Nord", generateWeatherData(12, 25, 65, 15));

        // Centro - Clima temperato
        dataPerArea.put("Centro", generateWeatherData(15, 28, 55, 12));

        // Sud - Clima mediterraneo
        dataPerArea.put("Sud", generateWeatherData(18, 32, 45, 18));

        return new WeatherSnapshot(dataPerArea, LocalDateTime.now());
    }

    /**
     * Genera dati meteo con variabilità realistica
     */
    private EnvironmentalData generateWeatherData(double baseTempMin, double baseTempMax,
                                                  double baseHumidity, double baseWind) {
        // Temperatura con variazione circadiana
        int hour = LocalDateTime.now().getHour();
        double tempVariation = Math.sin((hour - 6) * Math.PI / 12) * 5; // Picco alle 14:00
        double temp = baseTempMin + (baseTempMax - baseTempMin) * 0.5 + tempVariation + randomNoise(2);

        // Umidità inversamente proporzionale alla temperatura
        double humidity = baseHumidity + (25 - temp) * 1.5 + randomNoise(5);
        humidity = Math.max(30, Math.min(95, humidity));

        // Precipitazioni (10% probabilità pioggia)
        double rain = random.nextDouble() < 0.1 ? random.nextDouble() * 5 : 0;

        // Vento con raffiche
        double wind = baseWind + randomNoise(5);
        wind = Math.max(0, wind);

        // Radiazione solare (0-1000 W/m²)
        double solar = Math.max(0, 800 * Math.sin((hour - 6) * Math.PI / 12) + randomNoise(100));

        // Condizioni meteo
        String conditions;
        if (rain > 1) {
            conditions = "Pioggia";
        } else if (humidity > 70) {
            conditions = "Nuvoloso";
        } else {
            conditions = "Soleggiato";
        }

        return new EnvironmentalData(
                roundTo(temp, 1),
                roundTo(humidity, 0),
                roundTo(rain, 1),
                roundTo(wind, 1),
                roundTo(solar, 0),
                conditions
        );
    }

    private double randomNoise(double amplitude) {
        return (random.nextDouble() - 0.5) * 2 * amplitude;
    }

    private double roundTo(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}