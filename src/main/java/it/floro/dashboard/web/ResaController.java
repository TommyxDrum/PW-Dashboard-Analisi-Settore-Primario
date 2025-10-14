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
                .filter(r -> !r.date().isBefore(finalFrom) && !r.date().isAfter(finalTo))
                .filter(r -> cropFilter == null || cropFilter.equalsIgnoreCase(r.crop()))
                .filter(r -> areaFilter == null || norm(r.area()).contains(norm(areaFilter)))
                .collect(Collectors.toList());

        List<String> areas = List.of("Nord", "Centro", "Sud");

        Map<String, List<SampleRecord>> byArea = filtered.stream()
                .collect(Collectors.groupingBy(r -> {
                    String a = norm(r.area());
                    if (a.contains("nord"))   return "Nord";
                    if (a.contains("centro")) return "Centro";
                    if (a.contains("sud"))    return "Sud";
                    return "Altro";
                }));

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

        List<String> cropsList = cropsFrom(cachedRecords);
        List<String> areasList = List.of("Nord", "Centro", "Sud");

        model.addAttribute("from", fromDate);
        model.addAttribute("to", toDate);
        model.addAttribute("crop", cropFilter);
        model.addAttribute("area", areaFilter);

        model.addAttribute("cropsList", cropsList);
        model.addAttribute("areasList", areasList);

        model.addAttribute("yieldNordT",   sumYield.getOrDefault("Nord", 0d));
        model.addAttribute("yieldCentroT", sumYield.getOrDefault("Centro", 0d));
        model.addAttribute("yieldSudT",    sumYield.getOrDefault("Sud", 0d));
        model.addAttribute("surfNordHa",   sumSurface.getOrDefault("Nord", 0d));
        model.addAttribute("surfCentroHa", sumSurface.getOrDefault("Centro", 0d));
        model.addAttribute("surfSudHa",    sumSurface.getOrDefault("Sud", 0d));

        model.addAttribute("resaRows", rows);
        model.addAttribute("totalYieldT", round2(totalYield));
        model.addAttribute("totalSurfaceHa", round2(totalSurface));
        model.addAttribute("totalResa", round2(totalResa));

        List<List<Object>> chartData = List.of(
                List.of("product", "Resa (t/ha)", "Produzione (t)", "Superficie (ha)"),
                List.of("Nord",
                        round2(resa.getOrDefault("Nord", 0d)),
                        round2(sumYield.getOrDefault("Nord", 0d)),
                        round2(sumSurface.getOrDefault("Nord", 0d))
                ),
                List.of("Centro",
                        round2(resa.getOrDefault("Centro", 0d)),
                        round2(sumYield.getOrDefault("Centro", 0d)),
                        round2(sumSurface.getOrDefault("Centro", 0d))
                ),
                List.of("Sud",
                        round2(resa.getOrDefault("Sud", 0d)),
                        round2(sumYield.getOrDefault("Sud", 0d)),
                        round2(sumSurface.getOrDefault("Sud", 0d))
                )
        );
        model.addAttribute("chartData", chartData);

        return "resa";
    }

    private static String norm(String s) {
        if (s == null) return "";
        String noAccents = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase();
    }

    private static Map<String, Double> sumBy(Map<String, List<SampleRecord>> byArea,
                                             Function<SampleRecord, Double> getter) {
        Map<String, Double> out = new HashMap<>();
        byArea.forEach((area, list) ->
                out.put(area, list.stream().mapToDouble(getter::apply).sum())
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
