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
public class RischioClimaticoController {

    private final KpiService kpiService;

    public RischioClimaticoController(KpiService kpiService) {
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

    // Accetta sia camelCase che kebab-case, con/ senza slash finale
    @GetMapping({"/rischioClimatico", "/rischioClimatico/", "/rischio-climatico", "/rischio-climatico/"})
    public String rischioClimaticoPage(
            Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) String area
    ) {
        ensureDataIsLoaded();

        // 1) Filtri (default server-side robusti)
        LocalDate fromDate = (from != null) ? from : MAX_DATE.withDayOfMonth(1);
        LocalDate toDate   = (to   != null) ? to   : MAX_DATE;
        String cropFilter  = (crop == null || crop.isBlank()) ? null : crop.trim();
        String areaFilter  = (area == null || area.isBlank()) ? null : area.trim();

        // 2) Records filtrati
        List<SampleRecord> filteredRecords = cachedRecords.stream()
                .filter(r -> !r.date().isBefore(fromDate) && !r.date().isAfter(toDate))
                .filter(r -> cropFilter == null || cropFilter.equalsIgnoreCase(r.crop()))
                .filter(r -> areaFilter == null || norm(r.area()).contains(norm(areaFilter)))
                .toList();

        // KPI per ogni record (tollerante ai duplicati)
        Map<SampleRecord, Kpi> kpiMap = filteredRecords.stream()
                .collect(Collectors.toMap(r -> r, kpiService::compute, (a, b) -> a));

        // Raggruppo KPI per area normalizzata
        Map<String, List<Kpi>> kpisByArea = kpiMap.entrySet().stream()
                .collect(Collectors.groupingBy(
                        e -> normArea(e.getKey().area()),
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));

        // Medie per area
        Map<String, Double> avgRiskIndex = averageKpiBy(kpisByArea, Kpi::climateRiskIdx);
        Map<String, Double> avgRiskTemp  = averageKpiBy(kpisByArea, Kpi::riskTemperature);
        Map<String, Double> avgRiskWater = averageKpiBy(kpisByArea, Kpi::riskWaterStress);
        Map<String, Double> avgRiskFrost = averageKpiBy(kpisByArea, Kpi::riskFrost);

        // Medie totali periodo filtrato
        double totalAvgRiskIndex = kpiMap.values().stream().mapToDouble(Kpi::climateRiskIdx).average().orElse(0.0);
        double totalAvgRiskTemp  = kpiMap.values().stream().mapToDouble(Kpi::riskTemperature).average().orElse(0.0);
        double totalAvgRiskWater = kpiMap.values().stream().mapToDouble(Kpi::riskWaterStress).average().orElse(0.0);
        double totalAvgRiskFrost = kpiMap.values().stream().mapToDouble(Kpi::riskFrost).average().orElse(0.0);

        // Righe tabella (Nord/Centro/Sud)
        List<RischioRow> rows = List.of("Nord", "Centro", "Sud").stream()
                .map(a -> new RischioRow(
                        a,
                        avgRiskIndex.getOrDefault(a, 0d),
                        avgRiskTemp.getOrDefault(a, 0d),
                        avgRiskWater.getOrDefault(a, 0d),
                        avgRiskFrost.getOrDefault(a, 0d)
                ))
                .toList();

        // 3) Andamento annuale (storico intero, filtra coltura se richiesta)
        List<SampleRecord> historicalRecords = cachedRecords.stream()
                .filter(r -> cropFilter == null || cropFilter.equalsIgnoreCase(r.crop()))
                .toList();

        List<Integer> years = IntStream.rangeClosed(MIN_DATE.getYear(), MAX_DATE.getYear()).boxed().toList();
        Map<String, List<Double>> annualRiskByArea =
                calculateAnnualKpiAverage(historicalRecords, years, Kpi::climateRiskIdx);

        // 4) Model
        model.addAttribute("from", fromDate);
        model.addAttribute("to", toDate);
        model.addAttribute("crop", cropFilter);
        model.addAttribute("area", areaFilter);
        model.addAttribute("cropsList", cropsFrom(cachedRecords));
        model.addAttribute("areasList", List.of("Nord", "Centro", "Sud"));

        // KPI Cards e Tabella
        model.addAttribute("totalAvgRiskIndex", round2(totalAvgRiskIndex));
        model.addAttribute("totalAvgRiskTemp",  round2(totalAvgRiskTemp));
        model.addAttribute("totalAvgRiskWater", round2(totalAvgRiskWater));
        model.addAttribute("totalAvgRiskFrost", round2(totalAvgRiskFrost));
        model.addAttribute("rischioRows", rows);

        // Radar (medie per area)
        model.addAttribute("riskTempNord",    avgRiskTemp.getOrDefault("Nord", 0d));
        model.addAttribute("riskWaterNord",   avgRiskWater.getOrDefault("Nord", 0d));
        model.addAttribute("riskFrostNord",   avgRiskFrost.getOrDefault("Nord", 0d));
        model.addAttribute("riskTempCentro",  avgRiskTemp.getOrDefault("Centro", 0d));
        model.addAttribute("riskWaterCentro", avgRiskWater.getOrDefault("Centro", 0d));
        model.addAttribute("riskFrostCentro", avgRiskFrost.getOrDefault("Centro", 0d));
        model.addAttribute("riskTempSud",     avgRiskTemp.getOrDefault("Sud", 0d));
        model.addAttribute("riskWaterSud",    avgRiskWater.getOrDefault("Sud", 0d));
        model.addAttribute("riskFrostSud",    avgRiskFrost.getOrDefault("Sud", 0d));

        // Linea annuale
        model.addAttribute("years", years);
        model.addAttribute("annualRiskNord",   annualRiskByArea.getOrDefault("Nord",   Collections.emptyList()));
        model.addAttribute("annualRiskCentro", annualRiskByArea.getOrDefault("Centro", Collections.emptyList()));
        model.addAttribute("annualRiskSud",    annualRiskByArea.getOrDefault("Sud",    Collections.emptyList()));

        // Il template si chiama rischioClimatico.html
        return "rischioClimatico";
    }

    // --- Helpers KPI ---
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
                        .filter(list -> list != null && !list.isEmpty())
                        .map(list -> list.stream().mapToDouble(kpiGetter).average().orElse(0.0))
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

    // --- Helpers generali ---
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

    public record RischioRow(String area, double riskIndex, double riskTemp, double riskWater, double riskFrost) {}
}