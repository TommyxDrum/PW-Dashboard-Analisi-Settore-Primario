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
    private final String[] crops = {"Grano duro","Mais","Olivo","Vite"};
    private final String[] areas = {"Nord","Centro","Sud"};

    public DataSimulator(long seed, LocalDate start, int days, int fields) {
        this.rnd = new Random(seed);
        this.start = start;
        this.days = days;
        this.fields = fields;
    }

    private double sinYear(int dayShift, int day, double amp, double base) {
        return base + amp*Math.sin(2*Math.PI*(day - dayShift)/365.0);
    }

    private double gauss(double mean, double std) {
        // Box-Muller
        double u1 = Math.max(1e-9, rnd.nextDouble());
        double u2 = Math.max(1e-9, rnd.nextDouble());
        double z0 = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2*Math.PI*u2);
        return mean + std*z0;
    }

    public List<SampleRecord> generate() {
        List<SampleRecord> out = new ArrayList<>();
        for (int d=0; d<days; d++) {
            LocalDate date = start.plusDays(d);
            double temp = sinYear(30, d, 10, 12) + gauss(0,2);
            double hum = sinYear(-60, d, 15, 60) + gauss(0,5);
            double rain = Math.max(0, Math.exp(gauss(Math.log(4), 0.5)) - 1);
            double solar = Math.min(1, Math.max(0, 0.4 + 0.3*Math.sin(2*Math.PI*(d-10)/365.0) + gauss(0,0.05)));

            for (int f=0; f<fields; f++) {
                String field = "A" + String.format("%02d", f+1);
                String crop = crops[rnd.nextInt(crops.length)];
                String area = areas[rnd.nextInt(areas.length)];
                double surface = 1.0 + 5.0*rnd.nextDouble();

                double rainFactor = Math.min(rain/5.0, 1.2);
                double baseYieldPerHa = switch (crop) {
                    case "Grano duro" -> 4.5;
                    case "Mais" -> 7.0;
                    case "Olivo" -> 2.2;
                    default -> 9.0; // Vite
                };
                double seasonBoost = 1 + 0.25*Math.sin(2*Math.PI*(d-90)/365.0);
                double yieldPerHa = Math.max(0, gauss(baseYieldPerHa*seasonBoost*rainFactor, 0.4));
                double totalYield = yieldPerHa * surface;
                double waterM3 = Math.max(0, gauss(200*surface*(1+0.02*Math.max(temp-20,0)), 30));
                double cost = 150*surface + gauss(30*surface, 10);
                double price = switch (crop) {
                    case "Grano duro" -> 280;
                    case "Mais" -> 250;
                    case "Olivo" -> 450;
                    default -> 600;
                } + gauss(0, 20);

                // outliers rari
                if (rnd.nextDouble() < 0.01) {
                    totalYield *= 1.8;
                    waterM3 *= 1.5;
                    cost *= 1.6;
                }

                out.add(new SampleRecord(
                        date, area, field, crop, surface, temp, hum, rain, solar,
                        totalYield, waterM3, cost, price
                ));
            }
        }
        return out;
    }
}
