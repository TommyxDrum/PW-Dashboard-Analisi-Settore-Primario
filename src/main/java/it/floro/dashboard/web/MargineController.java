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

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@Controller
public class MargineController {

    private final KpiService kpiService;

    public MargineController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    private List<SampleRecord> cachedRecords;
    private static final LocalDate MAX_DATE = LocalDate.now();
    private static final LocalDate MIN_DATE = MAX_DATE.minusYears(10).withDayOfYear(1);

    private void ensureDataIsLoaded() {
        if (cachedRecords == null) {
            long seed = 12345L;
            int days = (int) ChronoUnit.DAYS.between(MIN_DATE, MAX_DATE) + 1;
            cachedRecords = new DataSimulator(seed, MIN_DATE, days, 20).generate();
        }
    }

    @GetMapping("/margine")
    public String marginePage(
            Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) String area
    ) {
        ensureDataIsLoaded();

        LocalDate fromDate = (from != null) ? from : MAX_DATE.withDayOfMonth(1);
        LocalDate toDate   = (to   != null) ? to   : MAX_DATE;
        String cropFilter  = (crop == null || crop.isBlank()) ? null : crop.trim();
        String areaFilter  = (area == null || area.isBlank()) ? null : area.trim();

        List<SampleRecord> filteredRecords = cachedRecords.stream()
                .filter(r -> !r.date().isBefore(fromDate) && !r.date().isAfter(toDate))
                .filter(r -> cropFilter == null || (r.crop() != null && cropFilter.equalsIgnoreCase(r.crop())))
                .filter(r -> areaFilter == null || (r.area() != null && norm(r.area()).contains(norm(areaFilter))))
                .toList();

        List<Kpi> kpis = filteredRecords.stream().map(kpiService::compute).toList();

        Map<String, List<Kpi>> kpisByArea = filteredRecords.stream()
                .collect(Collectors.groupingBy(
                        r -> normArea(r.area()),
                        Collectors.mapping(kpiService::compute, Collectors.toList())
                ));

        Map<String, Double> avgMargin = averageKpiBy(kpisByArea, Kpi::unitMarginEurPerT);
        Map<String, Double> avgCost   = averageKpiBy(kpisByArea, Kpi::unitCostEurPerT);

        double totalAvgMargin = kpis.stream().mapToDouble(Kpi::unitMarginEurPerT).average().orElse(0.0);
        double totalAvgCost   = kpis.stream().mapToDouble(Kpi::unitCostEurPerT).average().orElse(0.0);
        double totalAvgPrice  = totalAvgMargin + totalAvgCost;

        List<MargineRow> rows = List.of("Nord", "Centro", "Sud").stream()
                .map(a -> {
                    double cost   = avgCost.getOrDefault(a, 0d);
                    double margin = avgMargin.getOrDefault(a, 0d);
                    return new MargineRow(a, cost + margin, cost, margin);
                })
                .toList();

        List<SampleRecord> historicalRecords = cachedRecords.stream()
                .filter(r -> cropFilter == null || (r.crop() != null && cropFilter.equalsIgnoreCase(r.crop())))
                .toList();

        List<Integer> years = IntStream.rangeClosed(MIN_DATE.getYear(), MAX_DATE.getYear()).boxed().toList();
        Map<String, List<Double>> annualMarginByArea =
                calculateAnnualKpiAverage(historicalRecords, years, Kpi::unitMarginEurPerT);

        model.addAttribute("from", fromDate);
        model.addAttribute("to", toDate);
        model.addAttribute("crop", cropFilter);
        model.addAttribute("area", areaFilter);
        model.addAttribute("cropsList", cropsFrom(cachedRecords));
        model.addAttribute("areasList", List.of("Nord", "Centro", "Sud"));

        model.addAttribute("totalAvgPrice",  round2(totalAvgPrice));
        model.addAttribute("totalAvgCost",   round2(totalAvgCost));
        model.addAttribute("totalAvgMargin", round2(totalAvgMargin));
        model.addAttribute("margineRows", rows);

        model.addAttribute("avgCostNord",     avgCost.getOrDefault("Nord", 0d));
        model.addAttribute("avgMarginNord",   avgMargin.getOrDefault("Nord", 0d));
        model.addAttribute("avgCostCentro",   avgCost.getOrDefault("Centro", 0d));
        model.addAttribute("avgMarginCentro", avgMargin.getOrDefault("Centro", 0d));
        model.addAttribute("avgCostSud",      avgCost.getOrDefault("Sud", 0d));
        model.addAttribute("avgMarginSud",    avgMargin.getOrDefault("Sud", 0d));

        model.addAttribute("years", years);
        model.addAttribute("annualMarginNord",   annualMarginByArea.getOrDefault("Nord",   Collections.emptyList()));
        model.addAttribute("annualMarginCentro", annualMarginByArea.getOrDefault("Centro", Collections.emptyList()));
        model.addAttribute("annualMarginSud",    annualMarginByArea.getOrDefault("Sud",    Collections.emptyList()));

        return "margine";
    }

    private Map<String, List<Double>> calculateAnnualKpiAverage(
            List<SampleRecord> records, List<Integer> years, ToDoubleFunction<Kpi> kpiGetter) {

        Map<SampleRecord, Kpi> kpiMap = records.stream()
                .collect(Collectors.toMap(r -> r, kpiService::compute, (a, b) -> a));

        Map<Integer, Map<String, List<Kpi>>> grouped = kpiMap.entrySet().stream()
                .collect(Collectors.groupingBy(
                        e -> e.getKey().date().getYear(),
                        Collectors.groupingBy(
                                e -> normArea(e.getKey().area()),
                                Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                        )
                ));

        Map<String, List<Double>> result = new HashMap<>();
        for (String area : List.of("Nord", "Centro", "Sud")) {
            List<Double> annualValues = new ArrayList<>(years.size());
            for (Integer year : years) {
                double avg = Optional.ofNullable(grouped.get(year))
                        .map(m -> m.get(area))
                        .map(list -> (list == null || list.isEmpty())
                                ? 0.0
                                : list.stream().mapToDouble(kpiGetter).average().orElse(0.0))
                        .orElse(0.0);
                annualValues.add(avg);
            }
            result.put(area, annualValues);
        }
        return result;
    }

    private static Map<String, Double> averageKpiBy(Map<String, List<Kpi>> byArea, ToDoubleFunction<Kpi> getter) {
        Map<String, Double> out = new HashMap<>();
        byArea.forEach((area, list) ->
                out.put(area, list.stream().mapToDouble(getter).average().orElse(0.0))
        );
        return out;
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private static String normArea(String area) {
        String a = norm(area);
        if (a.contains("nord"))   return "Nord";
        if (a.contains("centro")) return "Centro";
        if (a.contains("sud"))    return "Sud";
        return "Altro";
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static List<String> cropsFrom(List<SampleRecord> all) {
        return all.stream()
                .map(SampleRecord::crop)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    public record MargineRow(String area, double price, double cost, double margin) {}
}