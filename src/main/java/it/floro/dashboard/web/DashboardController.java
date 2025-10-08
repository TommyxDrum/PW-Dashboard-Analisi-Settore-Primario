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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@Controller
public class DashboardController {

    private List<SampleRecord> cached;
    private final KpiService kpiService;

    public DashboardController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    private void ensureData(long seed, int days, int fields) {
        if (cached == null) {
            cached = new DataSimulator(seed, LocalDate.of(2024, 1, 1), days, fields).generate();
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

        ensureData(42L, 365, 8);

        // Default date range
        LocalDate fromDate = (from != null) ? from : LocalDate.of(2024, 1, 1);
        LocalDate toDate = (to != null) ? to : LocalDate.of(2024, 12, 31);

        // Swap if inverted
        if (toDate.isBefore(fromDate)) {
            LocalDate tmp = fromDate;
            fromDate = toDate;
            toDate = tmp;
        }

        // Normalize text filters
        String cropNorm = (crop == null) ? null : crop.trim();
        cropNorm = (cropNorm != null && cropNorm.isEmpty()) ? null : cropNorm;

        String areaNorm = (area == null) ? null : area.trim();
        areaNorm = (areaNorm != null && areaNorm.isEmpty()) ? null : areaNorm;

        // Make them effectively final for lambdas
        final LocalDate finalFromDate = fromDate;
        final LocalDate finalToDate = toDate;
        final String finalCrop = cropNorm;
        final String finalArea = areaNorm;

        // Filter
        List<SampleRecord> filtered = cached.stream()
                .filter(r -> !r.date().isBefore(finalFromDate) && !r.date().isAfter(finalToDate))
                .filter(r -> finalCrop == null || finalCrop.equalsIgnoreCase(r.crop()))
                .filter(r -> finalArea == null || finalArea.equalsIgnoreCase(r.area()))
                .toList();

        // Series for chart
        List<String> labels = filtered.stream().map(r -> r.date().toString()).toList();
        List<Double> yields = filtered.stream().map(SampleRecord::yieldT).toList();

        // KPIs computed once
        List<Kpi> kpis = filtered.stream().map(kpiService::compute).toList();
        double avgYieldHa = kpis.stream().mapToDouble(Kpi::yieldPerHa).average().orElse(Double.NaN);
        double avgEff = kpis.stream().mapToDouble(Kpi::waterEfficiencyKgPerM3).average().orElse(Double.NaN);
        double avgCost = kpis.stream().mapToDouble(Kpi::unitCostEurPerT).average().orElse(Double.NaN);
        double avgMargin = kpis.stream().mapToDouble(Kpi::unitMarginEurPerT).average().orElse(Double.NaN);
        double avgRisk = kpis.stream().mapToDouble(Kpi::climateRiskIdx).average().orElse(Double.NaN);

        // Model
        model.addAttribute("data", filtered);
        model.addAttribute("labels", labels);
        model.addAttribute("yields", yields);

        model.addAttribute("avgYieldHa", avgYieldHa);
        model.addAttribute("avgEff", avgEff);
        model.addAttribute("avgCost", avgCost);
        model.addAttribute("avgMargin", avgMargin);
        model.addAttribute("avgRisk", avgRisk);

        // Echo back current filters (template usa #temporals.format)
        model.addAttribute("from", fromDate);
        model.addAttribute("to", toDate);
        model.addAttribute("crop", cropNorm);
        model.addAttribute("area", areaNorm);

        return "index";
    }

    @GetMapping("/risorse")
    public String risorse(Model model) {
        ensureData(42L, 365, 8);

        // Media complessiva kg/m³
        var effPerRecord = cached.stream()
                .map(r -> (r.yieldT() * 1000.0) / r.waterM3())
                .toList();
        double meanOverall = effPerRecord.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);

        // Media per coltura
        Map<String, Double> byCrop = cached.stream().collect(
                Collectors.groupingBy(
                        SampleRecord::crop,
                        Collectors.averagingDouble(r -> (r.yieldT() * 1000.0) / r.waterM3())
                )
        );

        var entries = new ArrayList<>(byCrop.entrySet());
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
        ensureData(42L, 365, 8);

        // Media rischio per campo (field), con clamp dei componenti 0..1
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
