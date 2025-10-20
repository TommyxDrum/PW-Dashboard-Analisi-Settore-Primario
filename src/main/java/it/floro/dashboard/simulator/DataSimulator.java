package it.floro.dashboard.simulator;

import it.floro.dashboard.domain.SampleRecord;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DataSimulator {
    private final Random rnd;
    private final LocalDate start;
    private final int days;
    private final int fields;

    private final String[] crops = {"Grano duro", "Mais", "Olivo", "Vite"};
    private final String[] areas = {"Nord", "Centro", "Sud"};

    private static final double COST_SHARE_TARGET   = 0.45;
    private static final double COST_BALANCE_WEIGHT = 0.65;
    private static final double COST_SHARE_CAP      = 0.49;

    private String[] fieldName;
    private String[] fieldCrop;
    private String[] fieldArea;
    private double[] fieldSurface;

    public DataSimulator(long seed, LocalDate start, int days, int fields) {
        this.rnd = new Random(seed);
        this.start = start;
        this.days = days;
        this.fields = fields;
        initFieldProfiles();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double sinYear(int dayShift, int day, double amp, double base) {
        return base + amp * Math.sin(2 * Math.PI * (day - dayShift) / 365.0);
    }

    private double gauss(double mean, double std) {
        double u1 = Math.max(1e-9, rnd.nextDouble());
        double u2 = Math.max(1e-9, rnd.nextDouble());
        double z0 = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
        return mean + std * z0;
    }

    private void initFieldProfiles() {
        fieldName    = new String[fields];
        fieldCrop    = new String[fields];
        fieldArea    = new String[fields];
        fieldSurface = new double[fields];

        for (int f = 0; f < fields; f++) {
            fieldName[f]    = "A" + String.format("%02d", f + 1);
            fieldCrop[f]    = crops[rnd.nextInt(crops.length)];
            fieldArea[f]    = areas[rnd.nextInt(areas.length)];
            fieldSurface[f] = 1.0 + 5.0 * rnd.nextDouble();
        }
        // garantisci almeno un campo per ogni area
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
        // almeno un "Grano duro"
        if (fields > 0) fieldCrop[0] = "Grano duro";
    }

    private double areaTempBias(String area) {
        return switch (area) {
            case "Nord"   -> -1.5;
            case "Centro" ->  0.0;
            default       ->  1.5;
        };
    }
    private double areaRainProbBias(String area) {
        return switch (area) {
            case "Nord"   -> 0.06;
            case "Centro" -> 0.04;
            default       -> 0.03;
        };
    }

    private int cropPhaseShift(String crop) {
        return switch (crop) {
            case "Grano duro" -> 60;
            case "Mais"       -> 90;
            case "Olivo"      -> 120;
            default /* Vite */-> 100;
        };
    }

    private double baseYieldPerHa(String crop) {
        return switch (crop) {
            case "Grano duro" -> 4.5;
            case "Mais"       -> 7.0;
            case "Olivo"      -> 2.2;
            default /* Vite */-> 9.0;
        };
    }

    private double cropTempOpt(String crop) {
        return switch (crop) {
            case "Grano duro" -> 18;
            case "Mais"       -> 22;
            case "Olivo"      -> 24;
            default /* Vite */-> 20;
        };
    }

    private double tempSuitability(double t, String crop) {
        double mu = cropTempOpt(crop);
        double sigma = 7.0;
        double z = (t - mu) / sigma;
        return Math.exp(-0.5 * z * z);
    }

    private double rainFactor(double rainMm) {
        double f = 0.6 + 0.6 * Math.log1p(rainMm) / Math.log1p(30.0);
        return clamp(f, 0.4, 1.2);
    }

    private int seasonDays(String crop) {
        return switch (crop) {
            case "Grano duro" -> 150;
            case "Mais"       -> 140;
            case "Olivo"      -> 210;
            default /* Vite */-> 200;
        };
    }

    private double fixedCostPerHaSeason(String crop) {
        return switch (crop) {
            case "Grano duro" -> 1150;
            case "Mais"       -> 1260;
            case "Olivo"      -> 1470;
            default /* Vite */-> 2310;
        };
    }
    private double inputsPerHaSeason(String crop) {
        return switch (crop) {
            case "Grano duro" -> 410;
            case "Mais"       -> 485;
            case "Olivo"      -> 378;
            default /* Vite */-> 562;
        };
    }

    private double waterCostPerM3() {
        return 0.20;
    }

    /**
     * Calcolo del rischio termico “smooth” per coltura.
     * 0 circa nella fascia ottimale (minOk..maxOk), cresce verso caldo e freddo.
     */
    private double calculateRiskTemperature(double t, String crop) {
        double minOk, maxOk, maxHot, minCold;
        switch (crop) {
            case "Mais" -> { minOk = 22; maxOk = 28; maxHot = 40; minCold = 0; }
            case "Olivo" -> { minOk = 20; maxOk = 28; maxHot = 42; minCold = 0; }
            case "Vite" -> { minOk = 18; maxOk = 26; maxHot = 40; minCold = 0; }
            default /* Grano duro */ -> { minOk = 16; maxOk = 24; maxHot = 38; minCold = 0; }
        }
        double cold = clamp01((minOk - t) / (minOk - minCold + 1e-9));
        double hot  = clamp01((t - maxOk) / (maxHot - maxOk + 1e-9));
        return clamp01(0.6 * hot + 0.4 * cold);
    }

    public List<SampleRecord> generate() {
        List<SampleRecord> out = new ArrayList<>();

        double tempAnom = 0.0;
        double priceShockWheat = 0.0, priceShockMais = 0.0, priceShockOlivo = 0.0, priceShockVite = 0.0;

        double phiT = 0.6, sigmaT = 1.8;
        double phiP = 0.7, sigmaP = 0.01;

        for (int d = 0; d < days; d++) {
            LocalDate date = start.plusDays(d);

            double tempSeason = sinYear(30, d, 10, 12);
            tempAnom = phiT * tempAnom + gauss(0, sigmaT);

            double seasonRainProb = clamp(0.15 + 0.10 * Math.cos(2 * Math.PI * (d - 100) / 365.0), 0.02, 0.45);
            double solarSeason = clamp(0.45 + 0.35 * Math.sin(2 * Math.PI * (d - 10) / 365.0), 0.1, 1.0);

            for (int f = 0; f < fields; f++) {
                String field   = fieldName[f];
                String crop    = fieldCrop[f];
                String area    = fieldArea[f];
                double surface = fieldSurface[f];

                double temp = tempSeason + tempAnom + areaTempBias(area);

                double pRain = clamp(seasonRainProb + areaRainProbBias(area), 0, 0.75);
                boolean rains = rnd.nextDouble() < pRain;
                double rain = 0.0;
                if (rains) {
                    double mu = Math.log(4.0), sigma = 0.6;
                    rain = Math.max(0, Math.exp(gauss(mu, sigma)) - 1.0);
                }

                double humBase = sinYear(-60, d, 12, 62);
                double hum = clamp(humBase + (rains ? 10 : -3) - 0.3 * (temp - 20), 25, 95);

                double solar = clamp(solarSeason * (1.0 - clamp(rain / 25.0, 0, 0.7)), 0.05, 1.0);

                double baseYield = baseYieldPerHa(crop);
                double seasonBoost = 1 + 0.25 * Math.sin(2 * Math.PI * (d - cropPhaseShift(crop)) / 365.0);
                double fRain = rainFactor(rain);
                double fTemp = 0.7 + 0.3 * tempSuitability(temp, crop);
                double fSolar = 0.85 + 0.15 * solar;
                double yieldPerHa = Math.max(0.0, gauss(baseYield * seasonBoost * fRain * fTemp * fSolar, 0.35));
                double totalYield = yieldPerHa * surface;

                double demand = 70 * (1 + 0.04 * Math.max(temp - 20, 0));
                double irrigationNeed = Math.max(0, demand - 20 * Math.log1p(rain));
                double waterM3 = Math.max(0.0, gauss(irrigationNeed * surface, 18));

                int sDays = seasonDays(crop);
                double fixedSeasonPerHa  = fixedCostPerHaSeason(crop);
                double inputsSeasonPerHa = inputsPerHaSeason(crop);

                double fixedCostDaily  = (fixedSeasonPerHa  / sDays) * surface;
                double inputsCostDaily = (inputsSeasonPerHa / sDays) * surface;
                double waterCost       = waterM3 * waterCostPerM3();
                double baseCost        = Math.max(0.0, fixedCostDaily + inputsCostDaily + waterCost);

                double basePrice = switch (crop) {
                    case "Grano duro" -> 280;
                    case "Mais"       -> 250;
                    case "Olivo"      -> 450;
                    default /* Vite */-> 600;
                };
                switch (crop) {
                    case "Grano duro" -> priceShockWheat = phiP * priceShockWheat + gauss(0, sigmaP);
                    case "Mais"       -> priceShockMais   = phiP * priceShockMais   + gauss(0, sigmaP);
                    case "Olivo"      -> priceShockOlivo  = phiP * priceShockOlivo  + gauss(0, sigmaP);
                    default /* Vite */-> priceShockVite   = phiP * priceShockVite   + gauss(0, sigmaP);
                }
                double shock = switch (crop) {
                    case "Grano duro" -> priceShockWheat;
                    case "Mais"       -> priceShockMais;
                    case "Olivo"      -> priceShockOlivo;
                    default /* Vite */-> priceShockVite;
                };
                double price = basePrice * (1.0 + shock) + gauss(0, 8);

                double revenue     = Math.max(0.0, price * totalYield);
                double targetCost  = COST_SHARE_TARGET * revenue;
                double blendedCost = (1.0 - COST_BALANCE_WEIGHT) * baseCost + COST_BALANCE_WEIGHT * targetCost;

                double costCap = (revenue > 0) ? COST_SHARE_CAP * revenue : blendedCost;
                double minCostFloor = fixedCostDaily + inputsCostDaily;

                double cost = blendedCost;
                cost = Math.max(minCostFloor, cost);
                if (revenue > 0) cost = Math.min(cost, costCap);

                if (rnd.nextDouble() < 0.01) {
                    totalYield *= 1.5;
                    waterM3    *= 1.4;
                    cost       *= 1.2;
                }

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