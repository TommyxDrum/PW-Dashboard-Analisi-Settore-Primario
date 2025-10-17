package it.floro.dashboard.web;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.simulator.DataSimulator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
public class EfficienzaIdricaController {

    private volatile List<SampleRecord> cachedRecords;
    private static final LocalDate MAX_DATE = LocalDate.now();
    private static final LocalDate MIN_DATE = MAX_DATE.minusYears(10).withDayOfYear(1);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private void ensureDataIsLoaded() {
        if (cachedRecords == null) {
            synchronized (this) {
                if (cachedRecords == null) {
                    long seed = 12345L;
                    int days = (int) ChronoUnit.DAYS.between(MIN_DATE, MAX_DATE) + 1;
                    int fields = 20;
                    cachedRecords = new DataSimulator(seed, MIN_DATE, days, fields).generate();
                }
            }
        }
    }

    // --- Utility parsing robusto ---
    private static LocalDate parseIsoOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try { return LocalDate.parse(s, ISO); } catch (Exception e) { return null; }
    }

    private static LocalDate coalesce(LocalDate value, LocalDate fallback) {
        return (value != null) ? value : fallback;
    }

    // accetta sia camelCase che kebab-case
    @GetMapping({"/efficienzaIdrica", "/efficienza-idrica"})
    public String efficienzaIdricaPage(
            Model model,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) String area
    ) {
        ensureDataIsLoaded();

        // 1) Normalizzazione periodo con default sicuri
        LocalDate fromParam = parseIsoOrNull(from);
        LocalDate toParam   = parseIsoOrNull(to);

        LocalDate fromDate = coalesce(fromParam, MAX_DATE.withDayOfMonth(1));
        LocalDate toDate   = coalesce(toParam,   MAX_DATE);

        // Clamping ai limiti consentiti
        if (fromDate.isBefore(MIN_DATE)) fromDate = MIN_DATE;
        if (toDate.isAfter(MAX_DATE))    toDate   = MAX_DATE;

        // Sanity: se from > to li scambiamo
        if (fromDate.isAfter(toDate)) {
            LocalDate tmp = fromDate;
            fromDate = toDate;
            toDate = tmp;
        }

        // copie finali per le lambda
        final LocalDate f = fromDate;
        final LocalDate t = toDate;

        String cropFilter = (crop == null || crop.isBlank()) ? null : crop.trim();
        String areaFilter = (area == null || area.isBlank()) ? null : area.trim();

        // 2) Dati filtrati per periodo (KPI/scatter/tabella)
        List<SampleRecord> filteredByPeriod = cachedRecords.stream()
                .filter((SampleRecord r) -> !r.date().isBefore(f) && !r.date().isAfter(t))
                .filter((SampleRecord r) -> cropFilter == null || cropFilter.equalsIgnoreCase(r.crop()))
                .filter((SampleRecord r) -> areaFilter == null || norm(r.area()).contains(norm(areaFilter)))
                .collect(Collectors.toList());

        Map<String, List<SampleRecord>> byArea = groupRecordsByArea(filteredByPeriod);
        Map<String, Double> sumYield = sumBy(byArea, SampleRecord::yieldT);
        Map<String, Double> sumWater = sumBy(byArea, SampleRecord::waterM3);

        Map<String, Double> efficiency = new HashMap<>();
        for (String a : List.of("Nord", "Centro", "Sud")) {
            double yKg = sumYield.getOrDefault(a, 0d) * 1000; // t -> kg
            double wM3 = sumWater.getOrDefault(a, 0d);
            efficiency.put(a, wM3 > 0 ? yKg / wM3 : 0d);
        }

        double totalYield = sumYield.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalWater = sumWater.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalEfficiency = totalWater > 0 ? (totalYield * 1000) / totalWater : 0d;

        List<EfficienzaIdricaRow> rows = List.of("Nord", "Centro", "Sud").stream()
                .map(a -> new EfficienzaIdricaRow(
                        a,
                        round2(sumYield.getOrDefault(a, 0d)),
                        round2(sumWater.getOrDefault(a, 0d)),
                        round2(efficiency.getOrDefault(a, 0d))
                ))
                .collect(Collectors.toList());

        // 3) Storico annuale (rispetta crop/area, non il periodo)
        List<SampleRecord> filteredForHistory = cachedRecords.stream()
                .filter(r -> cropFilter == null || cropFilter.equalsIgnoreCase(r.crop()))
                .filter(r -> areaFilter == null || norm(r.area()).contains(norm(areaFilter)))
                .collect(Collectors.toList());

        List<Integer> years = IntStream.rangeClosed(MIN_DATE.getYear(), MAX_DATE.getYear())
                .boxed().collect(Collectors.toList());
        Map<String, List<Double>> annualEfficiencyByArea = calculateAnnualEfficiency(filteredForHistory, years);

        // 4) Model
        model.addAttribute("from", fromDate);
        model.addAttribute("to", toDate);
        model.addAttribute("crop", cropFilter);
        model.addAttribute("area", areaFilter);
        model.addAttribute("cropsList", cropsFrom(cachedRecords));
        model.addAttribute("areasList", List.of("Nord", "Centro", "Sud"));

        model.addAttribute("totalYieldT", round2(totalYield));
        model.addAttribute("totalWaterM3", round2(totalWater));
        model.addAttribute("totalEfficiency", round2(totalEfficiency));
        model.addAttribute("efficienzaRows", rows);

        model.addAttribute("yieldNordT",   sumYield.getOrDefault("Nord", 0d));
        model.addAttribute("yieldCentroT", sumYield.getOrDefault("Centro", 0d));
        model.addAttribute("yieldSudT",    sumYield.getOrDefault("Sud", 0d));
        model.addAttribute("waterNordM3",   sumWater.getOrDefault("Nord", 0d));
        model.addAttribute("waterCentroM3", sumWater.getOrDefault("Centro", 0d));
        model.addAttribute("waterSudM3",    sumWater.getOrDefault("Sud", 0d));

        model.addAttribute("years", years);
        model.addAttribute("annualEfficiencyNord",   annualEfficiencyByArea.getOrDefault("Nord", Collections.emptyList()));
        model.addAttribute("annualEfficiencyCentro", annualEfficiencyByArea.getOrDefault("Centro", Collections.emptyList()));
        model.addAttribute("annualEfficiencySud",    annualEfficiencyByArea.getOrDefault("Sud", Collections.emptyList()));

        return "efficienzaIdrica";
    }

    // Export CSV (stessa strategia di parsing robusto)
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) String area
    ) {
        ensureDataIsLoaded();

        LocalDate fromParam = parseIsoOrNull(from);
        LocalDate toParam   = parseIsoOrNull(to);

        LocalDate start = coalesce(fromParam, MAX_DATE.withDayOfMonth(1));
        LocalDate end   = coalesce(toParam,   MAX_DATE);

        if (start.isBefore(MIN_DATE)) start = MIN_DATE;
        if (end.isAfter(MAX_DATE))    end   = MAX_DATE;
        if (start.isAfter(end)) { LocalDate t2 = start; start = end; end = t2; }

        // copie finali per le lambda
        final LocalDate f = start;
        final LocalDate t = end;

        String cropFilter = (crop == null || crop.isBlank()) ? null : crop.trim();
        String areaFilter = (area == null || area.isBlank()) ? null : area.trim();

        List<SampleRecord> filteredByPeriod = cachedRecords.stream()
                .filter(r -> !r.date().isBefore(f) && !r.date().isAfter(t))
                .filter(r -> cropFilter == null || cropFilter.equalsIgnoreCase(r.crop()))
                .filter(r -> areaFilter == null || norm(r.area()).contains(norm(areaFilter)))
                .collect(Collectors.toList());

        Map<String, List<SampleRecord>> byArea = groupRecordsByArea(filteredByPeriod);
        Map<String, Double> sumYield = sumBy(byArea, SampleRecord::yieldT);
        Map<String, Double> sumWater = sumBy(byArea, SampleRecord::waterM3);

        String[] areas = {"Nord", "Centro", "Sud"};

        StringBuilder sb = new StringBuilder();
        sb.append("Area;Produzione (t);Consumo Idrico (m3);Efficienza (Kg/m3)\n");
        for (String a : areas) {
            double yT = sumYield.getOrDefault(a, 0d);
            double wM3 = sumWater.getOrDefault(a, 0d);
            double eff = (wM3 > 0) ? (yT * 1000.0) / wM3 : 0.0;
            sb.append(a).append(';')
                    .append(format2(yT)).append(';')
                    .append(format2(wM3)).append(';')
                    .append(format2(eff)).append('\n');
        }

        double totY = Arrays.stream(areas).mapToDouble(a -> sumYield.getOrDefault(a, 0d)).sum();
        double totW = Arrays.stream(areas).mapToDouble(a -> sumWater.getOrDefault(a, 0d)).sum();
        double totE = (totW > 0) ? (totY * 1000.0) / totW : 0.0;

        sb.append("Totale;")
                .append(format2(totY)).append(';')
                .append(format2(totW)).append(';')
                .append(format2(totE)).append('\n');

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        String fn = "efficienza-idrica_" + f.format(ISO) + "_" + t.format(ISO) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fn)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    // ---- Calcoli e helper ----
    private Map<String, List<Double>> calculateAnnualEfficiency(List<SampleRecord> records, List<Integer> years) {
        Map<Integer, Map<String, List<SampleRecord>>> groupedByYearAndArea = records.stream()
                .collect(Collectors.groupingBy(r -> r.date().getYear(),
                        Collectors.groupingBy(r -> normArea(r.area()))));

        Map<String, List<Double>> result = new HashMap<>();
        for (String area : List.of("Nord", "Centro", "Sud")) {
            List<Double> annualValues = years.stream().map(year -> {
                Map<String, List<SampleRecord>> yearData = groupedByYearAndArea.get(year);
                if (yearData == null || !yearData.containsKey(area)) return 0.0;
                List<SampleRecord> areaRecords = yearData.get(area);
                double totalYieldKg = areaRecords.stream().mapToDouble(SampleRecord::yieldT).sum() * 1000;
                double totalWaterM3 = areaRecords.stream().mapToDouble(SampleRecord::waterM3).sum();
                return totalWaterM3 > 0 ? totalYieldKg / totalWaterM3 : 0.0;
            }).collect(Collectors.toList());
            result.put(area, annualValues);
        }
        return result;
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String normArea(String area) {
        String a = norm(area);
        if (a.contains("nord")) return "Nord";
        if (a.contains("centro")) return "Centro";
        if (a.contains("sud")) return "Sud";
        return "Altro";
    }

    private Map<String, List<SampleRecord>> groupRecordsByArea(List<SampleRecord> records) {
        return records.stream().collect(Collectors.groupingBy(r -> normArea(r.area())));
    }

    private static Map<String, Double> sumBy(Map<String, List<SampleRecord>> byArea, Function<SampleRecord, Double> getter) {
        Map<String, Double> out = new HashMap<>();
        byArea.forEach((area, list) -> out.put(area, list.stream().mapToDouble(getter::apply).sum()));
        return out;
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static String format2(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    private static List<String> cropsFrom(List<SampleRecord> all) {
        return all.stream()
                .map(SampleRecord::crop)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public record EfficienzaIdricaRow(String area, double yieldT, double waterM3, double efficiencyKgM3) {}
}
