package it.floro.dashboard.web;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.service.KpiFilters;
import it.floro.dashboard.service.KpiService;
import it.floro.dashboard.service.SampleDataService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Controller che espone il dashboard del KPI "Resa per ettaro (t/ha)".
 *
 * Responsabilità:
 * - Gestire la richiesta HTTP GET /resa con filtri opzionali
 * - Delega a KpiService il calcolo della resa per ettaro
 * - Aggregare dati per visualizzazione multi-granulare (giornaliera, annuale, per area)
 * - Correlate metriche (produzione totale, superficie totale)
 * - Popolare il Model Thymeleaf con attributi per la view "resa"
 *
 * Pattern: Estende BaseKpiController per riutilizzare logica comune
 * - Implementa l'hook astratto populateKpiModel
 * - Eredita il template method processKpiRequest
 * - Eredita utility per filtri (filterByArea, extractYears)
 *
 * KPI principale: Resa media per ettaro
 * - Formula: produzione in tonnellate / superficie in ettari
 * - Unità: tonnellate per ettaro (t/ha)
 * - Interpretazione: quante tonnellate di prodotto vengono generate per ettaro coltivato
 *
 * View renderizzata: "resa" (templates/resa.html)
 */
@Controller
public class ResaController extends BaseKpiController {

    public ResaController(SampleDataService sampleDataService,
                          KpiFilters kpiFilters,
                          KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    @GetMapping("/resa")
    public String resaKpi(
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer quarter,
            Model model
    ) {
        return processKpiRequest(area, crop, startDate, endDate, periodo,
                year, month, quarter, model, "resa");
    }

    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {

        // ===== SEZIONE 1: KPI BASE =====
        double resaMedia = kpiService.calcolaResaMedia(filtered);
        Map<LocalDate, Double> resaGiornaliera = kpiService.serieResaGiornaliera(filtered);

        model.addAttribute("resaMedia", resaMedia);
        model.addAttribute("resaGiornaliera", resaGiornaliera);

        // ===== SEZIONE 2: TOTALI GLOBALI =====
        double totalYieldT = kpiService.sommaProduzione(filtered);
        double totalSurfaceHa = kpiService.sommaSuperficie(filtered);
        double totalResa = totalSurfaceHa > 0 ? totalYieldT / totalSurfaceHa : 0.0;

        model.addAttribute("totalYieldT", totalYieldT);
        model.addAttribute("totalSurfaceHa", totalSurfaceHa);
        model.addAttribute("totalResa", totalResa);

        // ===== SEZIONE 3: SERIE ANNUALI PER AREA =====
        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        List<SampleRecord> nordAll = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll = filterByArea(all, "Sud");

        Map<Integer, Double> annualResaNord = kpiService.serieResaAnnuale(nordAll);
        Map<Integer, Double> annualResaCentro = kpiService.serieResaAnnuale(centroAll);
        Map<Integer, Double> annualResaSud = kpiService.serieResaAnnuale(sudAll);

        model.addAttribute("years", years);
        model.addAttribute("annualResaNord", toOrderedList(annualResaNord, years));
        model.addAttribute("annualResaCentro", toOrderedList(annualResaCentro, years));
        model.addAttribute("annualResaSud", toOrderedList(annualResaSud, years));

        // ===== SEZIONE 4: KPI PER AREA (Periodo filtrato) =====
        List<SampleRecord> nordFiltered = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered = filterByArea(filtered, "Sud");

        double yieldNordT = kpiService.sommaProduzione(nordFiltered);
        double yieldCentroT = kpiService.sommaProduzione(centroFiltered);
        double yieldSudT = kpiService.sommaProduzione(sudFiltered);

        double surfNordHa = kpiService.sommaSuperficie(nordFiltered);
        double surfCentroHa = kpiService.sommaSuperficie(centroFiltered);
        double surfSudHa = kpiService.sommaSuperficie(sudFiltered);

        model.addAttribute("yieldNordT", yieldNordT);
        model.addAttribute("yieldCentroT", yieldCentroT);
        model.addAttribute("yieldSudT", yieldSudT);
        model.addAttribute("surfNordHa", surfNordHa);
        model.addAttribute("surfCentroHa", surfCentroHa);
        model.addAttribute("surfSudHa", surfSudHa);

        // ===== SEZIONE 5: TABELLA DETTAGLI =====
        List<ResaRow> resaRows = Arrays.asList(
                new ResaRow("Nord", yieldNordT, surfNordHa, surfNordHa > 0 ? yieldNordT / surfNordHa : 0),
                new ResaRow("Centro", yieldCentroT, surfCentroHa, surfCentroHa > 0 ? yieldCentroT / surfCentroHa : 0),
                new ResaRow("Sud", yieldSudT, surfSudHa, surfSudHa > 0 ? yieldSudT / surfSudHa : 0)
        );
        model.addAttribute("resaRows", resaRows);

        // ===== SERIE MENSILE PER AREA =====
        if (!filtered.isEmpty()) {
            Optional<LocalDate> maybeFirst = filtered.stream()
                    .map(SampleRecord::date)
                    .filter(Objects::nonNull)
                    .sorted()
                    .findFirst();
            if (maybeFirst.isPresent()) {
                LocalDate firstDate = maybeFirst.get();
                java.time.YearMonth selectedYearMonth = java.time.YearMonth.from(firstDate);

                Map<LocalDate, Double> giornNord = kpiService.serieResaGiornaliera(nordFiltered);
                Map<LocalDate, Double> giornCentro = kpiService.serieResaGiornaliera(centroFiltered);
                Map<LocalDate, Double> giornSud = kpiService.serieResaGiornaliera(sudFiltered);

                Map<LocalDate, Double> meseNord = filterByYearMonth(giornNord, selectedYearMonth);
                Map<LocalDate, Double> meseCentro = filterByYearMonth(giornCentro, selectedYearMonth);
                Map<LocalDate, Double> meseSud = filterByYearMonth(giornSud, selectedYearMonth);

                java.util.Set<Integer> daySet = new java.util.TreeSet<>();
                meseNord.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                meseCentro.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                meseSud.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                if (daySet.isEmpty()) {
                    daySet.add(1);
                }

                List<String> dailyLabels = daySet.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.toList());

                java.util.List<Double> dailyResaNord = daySet.stream()
                        .map(g -> {
                            java.time.LocalDate d = java.time.LocalDate.of(selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseNord.getOrDefault(d, 0.0);
                        })
                        .collect(java.util.stream.Collectors.toList());
                java.util.List<Double> dailyResaCentro = daySet.stream()
                        .map(g -> {
                            java.time.LocalDate d = java.time.LocalDate.of(selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseCentro.getOrDefault(d, 0.0);
                        })
                        .collect(java.util.stream.Collectors.toList());
                java.util.List<Double> dailyResaSud = daySet.stream()
                        .map(g -> {
                            java.time.LocalDate d = java.time.LocalDate.of(selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseSud.getOrDefault(d, 0.0);
                        })
                        .collect(java.util.stream.Collectors.toList());

                model.addAttribute("dailyLabels", dailyLabels);
                model.addAttribute("dailyResaNord", dailyResaNord);
                model.addAttribute("dailyResaCentro", dailyResaCentro);
                model.addAttribute("dailyResaSud", dailyResaSud);
            }
        } else {
            model.addAttribute("dailyLabels", java.util.Collections.emptyList());
            model.addAttribute("dailyResaNord", java.util.Collections.emptyList());
            model.addAttribute("dailyResaCentro", java.util.Collections.emptyList());
            model.addAttribute("dailyResaSud", java.util.Collections.emptyList());
        }
    }

    // ====== METODI HELPER ======

    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    private Map<LocalDate, Double> filterByYearMonth(Map<LocalDate, Double> timeSeries, java.time.YearMonth yearMonth) {
        return timeSeries.entrySet().stream()
                .filter(e -> java.time.YearMonth.from(e.getKey()).equals(yearMonth))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        TreeMap::new
                ));
    }

    // ====== INNER CLASS ======

    public static class ResaRow {
        private final String area;
        private final double yieldT;
        private final double surfaceHa;
        private final double resa;

        public ResaRow(String area, double yieldT, double surfaceHa, double resa) {
            this.area = area;
            this.yieldT = yieldT;
            this.surfaceHa = surfaceHa;
            this.resa = resa;
        }

        public String getArea() { return area; }
        public double getYieldT() { return yieldT; }
        public double getSurfaceHa() { return surfaceHa; }
        public double getResa() { return resa; }
    }
}
