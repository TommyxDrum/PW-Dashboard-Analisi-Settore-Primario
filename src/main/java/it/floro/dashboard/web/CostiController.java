package it.floro.dashboard.web;

import it.floro.dashboard.domain.Kpi;
import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.service.KpiService;
import it.floro.dashboard.simulator.DataSimulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Objects; // Assicurati che questo import sia presente

@Controller
public class CostiController {

    @Autowired
    private KpiService kpiService;

    private List<SampleRecord> cachedRecords;
    private static final LocalDate MAX_DATE = LocalDate.now();
    private static final LocalDate MIN_DATE = MAX_DATE.minusYears(10).withDayOfYear(1);

    private void ensureDataIsLoaded() {
        if (cachedRecords == null) {
            cachedRecords = new DataSimulator(12345L, MIN_DATE, (int) ChronoUnit.DAYS.between(MIN_DATE, MAX_DATE) + 1, 20).generate();
        }
    }

    @GetMapping("/costi")
    public String costiPage(
            Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) String area
    ) {
        ensureDataIsLoaded();

        LocalDate fromDate = (from != null) ? from : MAX_DATE.withDayOfMonth(1);
        LocalDate toDate = (to != null) ? to : MAX_DATE;
        String cropFilter = (crop == null || crop.isBlank()) ? null : crop.trim();
        String areaFilter = (area == null || area.isBlank()) ? null : area.trim();

        List<SampleRecord> filteredRecords = cachedRecords.stream()
                .filter(r -> !r.date().isBefore(fromDate) && !r.date().isAfter(toDate))
                .filter(r -> cropFilter == null || cropFilter.equalsIgnoreCase(r.crop()))
                .filter(r -> areaFilter == null || norm(r.area()).contains(norm(areaFilter)))
                .collect(Collectors.toList());

        Map<SampleRecord, Kpi> kpiMap = filteredRecords.stream().collect(Collectors.toMap(r -> r, kpiService::compute));
        Map<String, List<Kpi>> kpisByArea = kpiMap.entrySet().stream()
                .collect(Collectors.groupingBy(entry -> normArea(entry.getKey().area()), Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        Map<String, Double> avgCost = averageKpiBy(kpisByArea, Kpi::unitCostEurPerT);
        Map<String, Double> avgCostLabor = averageKpiBy(kpisByArea, Kpi::unitCostLabor);
        Map<String, Double> avgCostMaterials = averageKpiBy(kpisByArea, Kpi::unitCostMaterials);

        double totalAvgCost = kpiMap.values().stream().mapToDouble(Kpi::unitCostEurPerT).average().orElse(0);
        double totalAvgCostLabor = kpiMap.values().stream().mapToDouble(Kpi::unitCostLabor).average().orElse(0);
        double totalAvgCostMaterials = kpiMap.values().stream().mapToDouble(Kpi::unitCostMaterials).average().orElse(0);

        List<CostiRow> rows = List.of("Nord", "Centro", "Sud").stream()
                .map(a -> new CostiRow(a, avgCost.getOrDefault(a, 0d), avgCostLabor.getOrDefault(a, 0d), avgCostMaterials.getOrDefault(a, 0d)))
                .collect(Collectors.toList());

        List<SampleRecord> historicalRecords = cachedRecords.stream().filter(r -> cropFilter == null || cropFilter.equalsIgnoreCase(r.crop())).collect(Collectors.toList());
        List<Integer> years = IntStream.rangeClosed(MIN_DATE.getYear(), MAX_DATE.getYear()).boxed().collect(Collectors.toList());
        Map<String, List<Double>> annualCostByArea = calculateAnnualKpiAverage(historicalRecords, years, Kpi::unitCostEurPerT);

        model.addAttribute("from", fromDate).addAttribute("to", toDate).addAttribute("crop", cropFilter).addAttribute("area", areaFilter);
        model.addAttribute("cropsList", cropsFrom(cachedRecords)).addAttribute("areasList", List.of("Nord", "Centro", "Sud"));

        model.addAttribute("totalAvgCost", totalAvgCost).addAttribute("totalAvgCostLabor", totalAvgCostLabor).addAttribute("totalAvgCostMaterials", totalAvgCostMaterials);
        model.addAttribute("costiRows", rows);

        model.addAttribute("avgCostLaborNord", avgCostLabor.getOrDefault("Nord", 0d)).addAttribute("avgCostMaterialsNord", avgCostMaterials.getOrDefault("Nord", 0d));
        model.addAttribute("avgCostLaborCentro", avgCostLabor.getOrDefault("Centro", 0d)).addAttribute("avgCostMaterialsCentro", avgCostMaterials.getOrDefault("Centro", 0d));
        model.addAttribute("avgCostLaborSud", avgCostLabor.getOrDefault("Sud", 0d)).addAttribute("avgCostMaterialsSud", avgCostMaterials.getOrDefault("Sud", 0d));

        model.addAttribute("years", years);
        model.addAttribute("annualCostNord", annualCostByArea.getOrDefault("Nord", Collections.emptyList()));
        model.addAttribute("annualCostCentro", annualCostByArea.getOrDefault("Centro", Collections.emptyList()));
        model.addAttribute("annualCostSud", annualCostByArea.getOrDefault("Sud", Collections.emptyList()));

        return "costi";
    }

    private Map<String, List<Double>> calculateAnnualKpiAverage(List<SampleRecord> records, List<Integer> years, ToDoubleFunction<Kpi> kpiGetter) {
        Map<Integer, Map<String, List<Kpi>>> grouped = records.stream()
                .map(r -> new AbstractMap.SimpleEntry<>(r, kpiService.compute(r)))
                .collect(Collectors.groupingBy(entry -> entry.getKey().date().getYear(), Collectors.groupingBy(entry -> normArea(entry.getKey().area()), Collectors.mapping(Map.Entry::getValue, Collectors.toList()))));

        Map<String, List<Double>> result = new HashMap<>();
        List.of("Nord", "Centro", "Sud").forEach(area -> {
            List<Double> annualValues = years.stream().map(year ->
                    Optional.ofNullable(grouped.get(year))
                            .map(data -> data.get(area))
                            // <-- FIX: Sostituito flatMap con map per gestire correttamente OptionalDouble
                            .map(list -> list.stream().mapToDouble(kpiGetter).average().orElse(0.0))
                            .orElse(0.0)
            ).collect(Collectors.toList());
            result.put(area, annualValues);
        });
        return result;
    }

    private static Map<String, Double> averageKpiBy(Map<String, List<Kpi>> byArea, ToDoubleFunction<Kpi> getter) {
        Map<String, Double> out = new HashMap<>();
        byArea.forEach((area, list) -> out.put(area, list.stream().mapToDouble(getter).average().orElse(0.0)));
        return out;
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    private static String normArea(String area) {
        String a = norm(area);
        if (a.contains("nord")) return "Nord";
        if (a.contains("centro")) return "Centro";
        if (a.contains("sud")) return "Sud";
        return "Altro";
    }

    private static List<String> cropsFrom(List<SampleRecord> all) {
        return all.stream().map(SampleRecord::crop).filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().sorted().collect(Collectors.toList());
    }

    public record CostiRow(String area, double totalCost, double laborCost, double materialsCost) {
    }
}