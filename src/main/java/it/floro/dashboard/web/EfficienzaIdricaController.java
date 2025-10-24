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
 * Controller che espone il dashboard del KPI "Efficienza Idrica (kg/m³)".
 *
 * Responsabilità:
 * - Gestire la richiesta HTTP GET /efficienzaIdrica con filtri opzionali
 * - Delega a KpiService il calcolo dell'efficienza idrica
 * - Aggregare dati per visualizzazione multi-granulare (giornaliera, annuale, per area)
 * - Calcolare metriche correlate (produzione totale, consumo idrico totale)
 * - Determinare scale appropriate per grafici polari/radar
 * - Popolare il Model Thymeleaf con attributi per la view "efficienzaIdrica"
 *
 * Pattern: Estende BaseKpiController per riutilizzare logica comune
 * - Implementa l'hook astratto populateKpiModel
 * - Eredita il template method processKpiRequest
 * - Eredita utility per filtri (filterByArea, extractYears, normalizeString)
 *
 * KPI principale: Efficienza idrica aggregata
 * - Formula: (produzione in tonnellate × 1000) / consumo d'acqua in m³
 * - Unità: chilogrammi per metro cubo (kg/m³)
 * - Interpretazione: quanti kg di prodotto vengono generati per ogni m³ d'acqua utilizzata
 *
 * View renderizzata: "efficienzaIdrica" (templates/efficienzaIdrica.html)
 */
@Controller
public class EfficienzaIdricaController extends BaseKpiController {

    public EfficienzaIdricaController(SampleDataService sampleDataService,
                                      KpiFilters kpiFilters,
                                      KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    @GetMapping("/efficienzaIdrica")
    public String efficienzaIdricaKpi(
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
                year, month, quarter, model, "efficienzaIdrica");
    }

    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {

        // ===== SEZIONE 1: KPI BASE =====
        double efficienzaMedia = kpiService.calcolaEfficienzaIdrica(filtered);
        Map<LocalDate, Double> efficienzaGiorno = kpiService.serieEfficienzaIdricaGiornaliera(filtered);

        model.addAttribute("efficienzaMedia", efficienzaMedia);
        model.addAttribute("efficienzaGiorno", efficienzaGiorno);

        // ===== SEZIONE 2: TOTALI GLOBALI =====
        double totalYieldT = kpiService.sommaProduzione(filtered);
        double totalWaterM3 = kpiService.sommaAcqua(filtered);
        double totalEfficiency = totalWaterM3 > 0 ? (totalYieldT * 1000) / totalWaterM3 : 0.0;

        model.addAttribute("totalYieldT", totalYieldT);
        model.addAttribute("totalWaterM3", totalWaterM3);
        model.addAttribute("totalEfficiency", totalEfficiency);

        // ===== SEZIONE 3: SERIE ANNUALI PER AREA =====
        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        List<SampleRecord> nordAll = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll = filterByArea(all, "Sud");

        Map<Integer, Double> annualEffNord = kpiService.serieEfficienzaIdricaAnnuale(nordAll);
        Map<Integer, Double> annualEffCentro = kpiService.serieEfficienzaIdricaAnnuale(centroAll);
        Map<Integer, Double> annualEffSud = kpiService.serieEfficienzaIdricaAnnuale(sudAll);

        model.addAttribute("years", years);
        model.addAttribute("annualEfficiencyNord", toOrderedList(annualEffNord, years));
        model.addAttribute("annualEfficiencyCentro", toOrderedList(annualEffCentro, years));
        model.addAttribute("annualEfficiencySud", toOrderedList(annualEffSud, years));

        // ===== SEZIONE 4: SCALA MASSIMA PER GRAFICI POLARI =====
        double effMax = Math.max(Math.max(efficienzaMedia, 100),
                Collections.max(Arrays.asList(
                        annualEffNord.values().stream().max(Double::compare).orElse(0.0),
                        annualEffCentro.values().stream().max(Double::compare).orElse(0.0),
                        annualEffSud.values().stream().max(Double::compare).orElse(0.0)
                )));
        model.addAttribute("effMaxScale", effMax);

        // ===== SEZIONE 5: KPI PER AREA =====
        List<SampleRecord> nordFiltered = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered = filterByArea(filtered, "Sud");

        double yieldNordT = kpiService.sommaProduzione(nordFiltered);
        double yieldCentroT = kpiService.sommaProduzione(centroFiltered);
        double yieldSudT = kpiService.sommaProduzione(sudFiltered);

        double waterNordM3 = kpiService.sommaAcqua(nordFiltered);
        double waterCentroM3 = kpiService.sommaAcqua(centroFiltered);
        double waterSudM3 = kpiService.sommaAcqua(sudFiltered);

        double effNordKgM3 = waterNordM3 > 0 ? (yieldNordT * 1000) / waterNordM3 : 0.0;
        double effCentroKgM3 = waterCentroM3 > 0 ? (yieldCentroT * 1000) / waterCentroM3 : 0.0;
        double effSudKgM3 = waterSudM3 > 0 ? (yieldSudT * 1000) / waterSudM3 : 0.0;

        model.addAttribute("yieldNordT", yieldNordT);
        model.addAttribute("yieldCentroT", yieldCentroT);
        model.addAttribute("yieldSudT", yieldSudT);
        model.addAttribute("waterNordM3", waterNordM3);
        model.addAttribute("waterCentroM3", waterCentroM3);
        model.addAttribute("waterSudM3", waterSudM3);
        model.addAttribute("effNordKgM3", effNordKgM3);
        model.addAttribute("effCentroKgM3", effCentroKgM3);
        model.addAttribute("effSudKgM3", effSudKgM3);

        List<EfficienzaRow> efficienzaRows = Arrays.asList(
                new EfficienzaRow("Nord", yieldNordT, waterNordM3, effNordKgM3),
                new EfficienzaRow("Centro", yieldCentroT, waterCentroM3, effCentroKgM3),
                new EfficienzaRow("Sud", yieldSudT, waterSudM3, effSudKgM3)
        );
        model.addAttribute("efficienzaRows", efficienzaRows);

        // ===== SERIE MENSILE PER AREA (Efficienza giornaliera) =====
        if (!filtered.isEmpty()) {
            Optional<LocalDate> maybeFirst = filtered.stream()
                    .map(SampleRecord::date)
                    .filter(Objects::nonNull)
                    .sorted()
                    .findFirst();
            if (maybeFirst.isPresent()) {
                LocalDate firstDate = maybeFirst.get();
                java.time.YearMonth selectedYearMonth = java.time.YearMonth.from(firstDate);

                Map<LocalDate, Double> giornNord = kpiService.serieEfficienzaIdricaGiornaliera(nordFiltered);
                Map<LocalDate, Double> giornCentro = kpiService.serieEfficienzaIdricaGiornaliera(centroFiltered);
                Map<LocalDate, Double> giornSud = kpiService.serieEfficienzaIdricaGiornaliera(sudFiltered);

                Map<LocalDate, Double> meseNord = filterByYearMonth(giornNord, selectedYearMonth);
                Map<LocalDate, Double> meseCentro = filterByYearMonth(giornCentro, selectedYearMonth);
                Map<LocalDate, Double> meseSud = filterByYearMonth(giornSud, selectedYearMonth);

                java.util.Set<Integer> daySet = new java.util.TreeSet<>();
                meseNord.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                meseCentro.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                meseSud.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                if (daySet.isEmpty()) daySet.add(1);

                List<String> dailyLabels = daySet.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.toList());

                java.util.List<Double> dailyEfficiencyNord = daySet.stream()
                        .map(g -> {
                            java.time.LocalDate d = java.time.LocalDate.of(
                                    selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseNord.getOrDefault(d, 0.0);
                        })
                        .collect(java.util.stream.Collectors.toList());
                java.util.List<Double> dailyEfficiencyCentro = daySet.stream()
                        .map(g -> {
                            java.time.LocalDate d = java.time.LocalDate.of(
                                    selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseCentro.getOrDefault(d, 0.0);
                        })
                        .collect(java.util.stream.Collectors.toList());
                java.util.List<Double> dailyEfficiencySud = daySet.stream()
                        .map(g -> {
                            java.time.LocalDate d = java.time.LocalDate.of(
                                    selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseSud.getOrDefault(d, 0.0);
                        })
                        .collect(java.util.stream.Collectors.toList());

                model.addAttribute("dailyLabels", dailyLabels);
                model.addAttribute("dailyEfficiencyNord", dailyEfficiencyNord);
                model.addAttribute("dailyEfficiencyCentro", dailyEfficiencyCentro);
                model.addAttribute("dailyEfficiencySud", dailyEfficiencySud);
            }
        } else {
            model.addAttribute("dailyLabels", java.util.Collections.emptyList());
            model.addAttribute("dailyEfficiencyNord", java.util.Collections.emptyList());
            model.addAttribute("dailyEfficiencyCentro", java.util.Collections.emptyList());
            model.addAttribute("dailyEfficiencySud", java.util.Collections.emptyList());
        }
    }

    // ====== HELPER ======

    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    private Map<LocalDate, Double> filterByYearMonth(Map<LocalDate, Double> timeSeries,
                                                     java.time.YearMonth yearMonth) {
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

    public static class EfficienzaRow {
        private final String area;
        private final double yieldT;
        private final double waterM3;
        private final double efficiencyKgM3;

        public EfficienzaRow(String area, double yieldT, double waterM3, double efficiencyKgM3) {
            this.area = area;
            this.yieldT = yieldT;
            this.waterM3 = waterM3;
            this.efficiencyKgM3 = efficiencyKgM3;
        }

        public String getArea() { return area; }
        public double getYieldT() { return yieldT; }
        public double getWaterM3() { return waterM3; }
        public double getEfficiencyKgM3() { return efficiencyKgM3; }
    }
}
