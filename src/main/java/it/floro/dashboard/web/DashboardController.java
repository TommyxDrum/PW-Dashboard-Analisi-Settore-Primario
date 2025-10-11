package it.floro.dashboard.web;

import it.floro.dashboard.domain.Kpi;
import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.service.KpiService;
import it.floro.dashboard.simulator.DataSimulator;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@Controller
public class DashboardController {

    // Cache dati simulati (tutto il periodo)
    private List<SampleRecord> cached;
    private final KpiService kpiService;

    // Limiti temporali globali (10 anni fino a oggi)
    private static final LocalDate MAX_DATE = LocalDate.now();
    private static final LocalDate MIN_DATE = MAX_DATE.minusYears(10).withDayOfYear(1);

    public DashboardController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    /** Carica i dati una sola volta per l’intero intervallo [start, end]. */
    private void ensureData(long seed, LocalDate start, LocalDate end, int fields) {
        if (cached == null) {
            int days = (int) ChronoUnit.DAYS.between(start, end) + 1; // inclusivo
            cached = new DataSimulator(seed, start, days, fields).generate();
        }
    }

    @GetMapping("/")
    public String overview(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(name = "crop", required = false) String crop,
            @RequestParam(name = "area", required = false) String area,
            Model model) {

        // Carica dati per tutto il range (10 anni)
        ensureData(42L, MIN_DATE, MAX_DATE, 8);

        // Default + clamp su range consentito
        LocalDate fromDate = (from != null) ? from : MIN_DATE;
        LocalDate toDate   = (to   != null) ? to   : MAX_DATE;

        if (fromDate.isBefore(MIN_DATE)) fromDate = MIN_DATE;
        if (fromDate.isAfter(MAX_DATE))  fromDate = MAX_DATE;
        if (toDate.isAfter(MAX_DATE))    toDate   = MAX_DATE;
        if (toDate.isBefore(MIN_DATE))   toDate   = MIN_DATE;

        // Swap se invertite
        if (toDate.isBefore(fromDate)) {
            LocalDate tmp = fromDate; fromDate = toDate; toDate = tmp;
        }

        // Normalizza filtri testuali
        String cropNorm = (crop == null || crop.isBlank()) ? null : crop.trim();
        String areaNorm = (area == null || area.isBlank()) ? null : area.trim();

        // Copie finali per lambdas
        final LocalDate fFrom = fromDate;
        final LocalDate fTo   = toDate;
        final String fCrop    = cropNorm;
        final String fArea    = areaNorm;

        // Filtro dati riga/record
        List<SampleRecord> filtered = cached.stream()
                .filter(r -> !r.date().isBefore(fFrom) && !r.date().isAfter(fTo))
                .filter(r -> fCrop == null || fCrop.equalsIgnoreCase(r.crop()))
                .filter(r -> fArea == null || fArea.equalsIgnoreCase(r.area()))
                .toList();

        // === Labels: tutte le date del range (una voce per ogni giorno) ===
        List<LocalDate> dateRange = new ArrayList<>();
        for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
            dateRange.add(d);
        }
        List<String> labels = dateRange.stream().map(LocalDate::toString).toList();

        // === Serie per area: somma delle rese per giorno ===
        Map<String, Map<LocalDate, Double>> sumByAreaDate = filtered.stream()
                .collect(Collectors.groupingBy(
                        SampleRecord::area,
                        Collectors.groupingBy(
                                SampleRecord::date,
                                Collectors.summingDouble(SampleRecord::yieldT)
                        )
                ));

        List<Double> yieldsNord   = seriesForArea("Nord",   dateRange, sumByAreaDate);
        List<Double> yieldsCentro = seriesForArea("Centro", dateRange, sumByAreaDate);
        List<Double> yieldsSud    = seriesForArea("Sud",    dateRange, sumByAreaDate);

        // === KPI calcolati sui record filtrati ===
        List<Kpi> kpis = filtered.stream().map(kpiService::compute).toList();

        double avgYieldHa = kpis.stream().mapToDouble(Kpi::yieldPerHa).filter(Double::isFinite).average().orElse(0.0);
        double avgEff     = kpis.stream().mapToDouble(Kpi::waterEfficiencyKgPerM3).filter(Double::isFinite).average().orElse(0.0);
        double avgCost    = kpis.stream().mapToDouble(Kpi::unitCostEurPerT).filter(Double::isFinite).average().orElse(0.0);
        double avgMargin  = kpis.stream().mapToDouble(Kpi::unitMarginEurPerT).filter(Double::isFinite).average().orElse(0.0);
        double avgRisk    = kpis.stream().mapToDouble(Kpi::climateRiskIdx).filter(Double::isFinite).average().orElse(0.0);

        // Efficienza media per coltura (per lista laterale)
        Map<String, Double> byCropEff = filtered.stream().collect(
                Collectors.groupingBy(
                        SampleRecord::crop,
                        Collectors.averagingDouble(r ->
                                (r.waterM3() == 0) ? Double.NaN : (r.yieldT() * 1000.0) / r.waterM3())
                )
        );

        List<Map.Entry<String, Double>> effEntries = new ArrayList<>(byCropEff.entrySet());
        effEntries.removeIf(e -> e.getValue()==null || !Double.isFinite(e.getValue()) || e.getValue() == 0.0);
        effEntries.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // desc

        if (!effEntries.isEmpty()) {
            model.addAttribute("bestEffCrop",  effEntries.get(0).getKey());
            model.addAttribute("bestEffValue", effEntries.get(0).getValue());
            model.addAttribute("allCropEfficiencies", effEntries.subList(0, Math.min(effEntries.size(), 3)));
        } else {
            model.addAttribute("bestEffCrop", "Nessun dato");
            model.addAttribute("bestEffValue", 0.0);
            model.addAttribute("allCropEfficiencies", Collections.emptyList());
        }

        // Scala robusta per tachimetro/indicatori (P95)
        List<Double> effSeries = filtered.stream()
                .map(r -> (r.waterM3() == 0) ? Double.NaN : (r.yieldT() * 1000.0) / r.waterM3())
                .filter(Double::isFinite)
                .sorted()
                .toList();

        double effMaxScale = 50.0; // fallback
        if (!effSeries.isEmpty()) {
            int idx = (int) Math.floor(0.95 * (effSeries.size() - 1));
            double p95 = effSeries.get(idx);
            effMaxScale = Math.max(10.0, Math.ceil((p95 * 1.10) / 5.0) * 5.0);
        }

        // ===== NUOVO: Efficienza media (Kg/m³) per area, per il Polar Area =====
        Map<String, List<Double>> effListByArea = filtered.stream().collect(
                Collectors.groupingBy(
                        SampleRecord::area,
                        Collectors.mapping(r -> {
                            double v = (r.waterM3() == 0) ? Double.NaN : (r.yieldT() * 1000.0) / r.waterM3();
                            return v;
                        }, Collectors.toList())
                )
        );

        double effNordKgM3   = avgFinite(effListByArea.get("Nord"));
        double effCentroKgM3 = avgFinite(effListByArea.get("Centro"));
        double effSudKgM3    = avgFinite(effListByArea.get("Sud"));

        // ===== Model attributes per la view =====
        model.addAttribute("labels", labels);
        model.addAttribute("yieldsNord",   yieldsNord);
        model.addAttribute("yieldsCentro", yieldsCentro);
        model.addAttribute("yieldsSud",    yieldsSud);

        model.addAttribute("avgYieldHa", avgYieldHa);
        model.addAttribute("avgEff", avgEff);
        model.addAttribute("avgCost", avgCost);
        model.addAttribute("avgMargin", avgMargin);
        model.addAttribute("avgRisk", avgRisk);
        model.addAttribute("effMaxScale", effMaxScale);

        // NUOVI attributi per il Polar Area (con unità Kg/m³)
        model.addAttribute("effNordKgM3",   effNordKgM3);
        model.addAttribute("effCentroKgM3", effCentroKgM3);
        model.addAttribute("effSudKgM3",    effSudKgM3);

        model.addAttribute("minDate", MIN_DATE);
        model.addAttribute("maxDate", MAX_DATE);

        model.addAttribute("from", fromDate);
        model.addAttribute("to", toDate);
        model.addAttribute("crop", cropNorm);
        model.addAttribute("area", areaNorm);

        return "index";
    }

    /** Media dei soli valori finiti; 0.0 se lista nulla/vuota o senza valori validi. */
    private static double avgFinite(Collection<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        return values.stream()
                .filter(Objects::nonNull)
                .filter(Double::isFinite)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    /** Helper: crea la serie giornaliera allineata ai labels per una specifica area. */
    private List<Double> seriesForArea(String areaKey,
                                       List<LocalDate> dateRange,
                                       Map<String, Map<LocalDate, Double>> sumByAreaDate) {
        Map<LocalDate, Double> map = sumByAreaDate.getOrDefault(areaKey, Collections.emptyMap());
        List<Double> out = new ArrayList<>(dateRange.size());
        for (LocalDate d : dateRange) {
            out.add(map.getOrDefault(d, 0.0));
        }
        return out;
    }

    @GetMapping("/risorse")
    public String risorse(Model model) {
        ensureData(42L, MIN_DATE, MAX_DATE, 8);

        var effPerRecord = cached.stream()
                .map(r -> (r.waterM3() == 0) ? Double.NaN : (r.yieldT() * 1000.0) / r.waterM3())
                .filter(Double::isFinite)
                .toList();
        double meanOverall = effPerRecord.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        Map<String, Double> byCrop = cached.stream().collect(
                Collectors.groupingBy(
                        SampleRecord::crop,
                        Collectors.averagingDouble(r ->
                                (r.waterM3() == 0) ? Double.NaN : (r.yieldT() * 1000.0) / r.waterM3())
                )
        );

        var entries = new ArrayList<>(byCrop.entrySet());
        entries.removeIf(e -> e.getValue()==null || !Double.isFinite(e.getValue()) || e.getValue() == 0.0);
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // desc

        List<String> labels = entries.stream().map(Map.Entry::getKey).toList();
        List<Double> eff = entries.stream().map(Map.Entry::getValue).toList();

        model.addAttribute("labels", labels);
        model.addAttribute("eff", eff);
        model.addAttribute("meanOverall", meanOverall);

        return "risorse";
    }

    @GetMapping("/rischio")
    public String rischio(Model model) {
        ensureData(42L, MIN_DATE, MAX_DATE, 8);

        // Media rischio per campo (0..1), normalizzando componenti
        Map<String, Double> riskByField = cached.stream().collect(
                Collectors.groupingBy(
                        SampleRecord::field,
                        Collectors.averagingDouble(r -> {
                            double t = Math.min(1.0, Math.max(0.0, (r.tempC() + 5.0) / 40.0));
                            double h = Math.min(1.0, Math.max(0.0, (r.humidityPct() - 20.0) / 70.0));
                            double p = Math.min(1.0, Math.max(0.0, r.rainMm() / 30.0));
                            return 0.4 * t + 0.3 * h + 0.3 * p;
                        })
                )
        );

        var entries = new ArrayList<>(riskByField.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // desc

        List<String> labels = entries.stream().map(Map.Entry::getKey).toList();
        List<Double> risk = entries.stream().map(Map.Entry::getValue).toList();

        model.addAttribute("labels", labels);
        model.addAttribute("risk", risk);

        return "rischio";
    }
}
