package it.floro.dashboard.web;

import it.floro.dashboard.domain.SampleRecord;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@Controller
public class ResaController {

    private List<SampleRecord> cachedRecords;
    private static final LocalDate MAX_DATE = LocalDate.now();
    private static final LocalDate MIN_DATE = MAX_DATE.minusYears(10).withDayOfYear(1);

    private void ensureDataIsLoaded() {
        if (cachedRecords == null) {
            long seed = 12345L;
            int days = (int) ChronoUnit.DAYS.between(MIN_DATE, MAX_DATE) + 1;
            int fields = 20;
            cachedRecords = new DataSimulator(seed, MIN_DATE, days, fields).generate();
        }
    }

    @GetMapping("/resa")
    public String resaPage(
            Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) String area
    ) {

        ensureDataIsLoaded();

        LocalDate fromDate = (from != null) ? from : MAX_DATE.withDayOfMonth(1);
        LocalDate toDate   = (to   != null) ? to   : MAX_DATE;

        if (fromDate.isBefore(MIN_DATE)) fromDate = MIN_DATE;
        if (toDate.isAfter(MAX_DATE))    toDate   = MAX_DATE;
        if (toDate.isBefore(fromDate)) {
            LocalDate tmp = fromDate; fromDate = toDate; toDate = tmp;
        }

        String cropFilter = (crop == null || crop.isBlank()) ? null : crop.trim();
        String areaFilter = (area == null || area.isBlank()) ? null : area.trim();

        final LocalDate finalFrom = fromDate;
        final LocalDate finalTo = toDate;

        List<SampleRecord> filtered = cachedRecords.stream()
                .filter(r -> r.date() != null && !r.date().isBefore(finalFrom) && !r.date().isAfter(finalTo))
                .filter(r -> cropFilter == null || cropFilter.equalsIgnoreCase(r.crop()))
                .filter(r -> areaFilter == null || norm(r.area()).contains(norm(areaFilter)))
                .collect(Collectors.toList());

        List<String> areas = List.of("Nord", "Centro", "Sud");

        Map<String, List<SampleRecord>> byArea = filtered.stream()
                .collect(Collectors.groupingBy(r -> normArea(r.area())));

        Map<String, Double> sumYield   = sumBy(byArea, SampleRecord::yieldT);
        Map<String, Double> sumSurface = sumBy(byArea, SampleRecord::surfaceHa);

        Map<String, Double> resa = new HashMap<>();
        for (String a : areas) {
            double y = sumYield.getOrDefault(a, 0d);
            double s = sumSurface.getOrDefault(a, 0d);
            resa.put(a, s > 0 ? y / s : 0d);
        }

        double totalYield   = sumYield.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalSurface = sumSurface.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalResa    = totalSurface > 0 ? totalYield / totalSurface : 0d;

        List<ResaRow> rows = areas.stream()
                .map(a -> new ResaRow(
                        a,
                        round2(sumYield.getOrDefault(a, 0d)),
                        round2(sumSurface.getOrDefault(a, 0d)),
                        round2(resa.getOrDefault(a, 0d))
                ))
                .collect(Collectors.toList());

        // === DATI STORICI PER IL GRAFICO ANNUALE ===
        List<SampleRecord> historicalRecords = cachedRecords.stream()
                .filter(r -> r.date() != null)
                .filter(r -> cropFilter == null || cropFilter.equalsIgnoreCase(r.crop()))
                .collect(Collectors.toList());

        List<Integer> years = IntStream.rangeClosed(MIN_DATE.getYear(), MAX_DATE.getYear())
                .boxed()
                .collect(Collectors.toList());

        Map<String, List<Double>> annualResaByArea = calculateAnnualResaAverage(historicalRecords, years);

        List<String> cropsList = cropsFrom(cachedRecords);
        List<String> areasList = List.of("Nord", "Centro", "Sud");

        model.addAttribute("from", fromDate);
        model.addAttribute("to", toDate);
        model.addAttribute("crop", cropFilter != null ? cropFilter : "");
        model.addAttribute("area", areaFilter != null ? areaFilter : "");

        model.addAttribute("cropsList", cropsList);
        model.addAttribute("areasList", areasList);

        model.addAttribute("yieldNordT",   round2(sumYield.getOrDefault("Nord", 0d)));
        model.addAttribute("yieldCentroT", round2(sumYield.getOrDefault("Centro", 0d)));
        model.addAttribute("yieldSudT",    round2(sumYield.getOrDefault("Sud", 0d)));
        model.addAttribute("surfNordHa",   round2(sumSurface.getOrDefault("Nord", 0d)));
        model.addAttribute("surfCentroHa", round2(sumSurface.getOrDefault("Centro", 0d)));
        model.addAttribute("surfSudHa",    round2(sumSurface.getOrDefault("Sud", 0d)));

        model.addAttribute("resaRows", rows);
        model.addAttribute("totalYieldT", round2(totalYield));
        model.addAttribute("totalSurfaceHa", round2(totalSurface));
        model.addAttribute("totalResa", round2(totalResa));

        // Dati annuali per il line chart
        model.addAttribute("years", years);
        model.addAttribute("annualResaNord", annualResaByArea.getOrDefault("Nord", Collections.emptyList()));
        model.addAttribute("annualResaCentro", annualResaByArea.getOrDefault("Centro", Collections.emptyList()));
        model.addAttribute("annualResaSud", annualResaByArea.getOrDefault("Sud", Collections.emptyList()));

        return "resa";
    }

    private Map<String, List<Double>> calculateAnnualResaAverage(
            List<SampleRecord> records, List<Integer> years) {

        // Raggruppa per anno e area
        Map<Integer, Map<String, List<SampleRecord>>> grouped = records.stream()
                .filter(r -> r.date() != null)
                .collect(Collectors.groupingBy(
                        r -> r.date().getYear(),
                        Collectors.groupingBy(r -> normArea(r.area()))
                ));

        Map<String, List<Double>> result = new HashMap<>();

        for (String area : List.of("Nord", "Centro", "Sud")) {
            List<Double> annualValues = new ArrayList<>(years.size());
            for (Integer year : years) {
                double yearResa = Optional.ofNullable(grouped.get(year))
                        .map(m -> m.get(area))
                        .map(list -> {
                            double totalYield = list.stream()
                                    .mapToDouble(r -> Optional.ofNullable(r.yieldT()).orElse(0.0))
                                    .sum();
                            double totalSurface = list.stream()
                                    .mapToDouble(r -> Optional.ofNullable(r.surfaceHa()).orElse(0.0))
                                    .sum();
                            return totalSurface > 0 ? totalYield / totalSurface : 0.0;
                        })
                        .orElse(0.0);
                annualValues.add(round2(yearResa));
            }
            result.put(area, annualValues);
        }

        return result;
    }

    private static String norm(String s) {
        if (s == null) return "";
        String noAccents = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase();
    }

    private static String normArea(String area) {
        if (area == null) return "Altro";
        String a = norm(area);
        if (a.contains("nord"))   return "Nord";
        if (a.contains("centro")) return "Centro";
        if (a.contains("sud"))    return "Sud";
        return "Altro";
    }

    private static Map<String, Double> sumBy(Map<String, List<SampleRecord>> byArea,
                                             Function<SampleRecord, Double> getter) {
        Map<String, Double> out = new HashMap<>();
        byArea.forEach((area, list) ->
                out.put(area, list.stream().mapToDouble(r -> {
                    Double val = getter.apply(r);
                    return val != null ? val : 0.0;
                }).sum())
        );
        return out;
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
                .collect(Collectors.toList());
    }

    public record ResaRow(String area, double yieldT, double surfaceHa, double resa) {}
}