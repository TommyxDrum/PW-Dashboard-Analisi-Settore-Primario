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
 * Controller che espone il dashboard del KPI "Costo unitario (€/t)".
 *
 * Responsabilità:
 * - Gestire la richiesta HTTP GET /costi con filtri opzionali
 * - Delega a KpiService il calcolo del costo unitario
 * - Aggregare i dati per visualizzazione (serie temporali, per area, etc.)
 * - Scomporre il costo stimato in componenti (manodopera, materiali)
 * - Popolare il Model Thymeleaf con attributi per la view "costi"
 *
 * Pattern: Estende BaseKpiController per riutilizzare logica comune
 * - Implementa l'hook astratto populateKpiModel
 * - Eredita il template method processKpiRequest
 * - Eredita utility per filtri (filterByArea, extractYears, normalizeString)
 *
 * KPI principale: Costo unitario medio aggregato
 * - Formula: media( costo_totale / produzione )
 * - Unità: euro per tonnellata (€/t)
 * - Interpretazione: quanto costa produrre 1 tonnellata di merce
 *
 * Sottocomponenti visualizzati:
 * - Costo manodopera (60% del totale, stima)
 * - Costo materiali (40% del totale, stima)
 * - Serie temporale giornaliera
 * - Serie temporale annuale per area
 *
 * View renderizzata: "costi" (templates/costi.html)
 */
@Controller
public class CostiController extends BaseKpiController {

    // ========================================================================
    // COSTANTI DI CONFIGURAZIONE
    // ========================================================================

    private static final double LABOR_RATIO = 0.60;
    private static final double MATERIALS_RATIO = 0.40;

    // ========================================================================
    // COSTRUTTORE
    // ========================================================================

    public CostiController(SampleDataService sampleDataService,
                           KpiFilters kpiFilters,
                           KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    // ========================================================================
    // ENDPOINT HTTP
    // ========================================================================

    @GetMapping("/costi")
    public String costoUnitarioKpi(
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
                year, month, quarter, model, "costi");
    }

    // ========================================================================
    // HOOK ASTRATTO - IMPLEMENTAZIONE
    // ========================================================================

    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {

        // ===== SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato) =====
        double costoUnitMedio = kpiService.calcolaCostoUnitario(filtered);
        Map<LocalDate, Double> costoUnitGiorno = kpiService.serieCostoUnitarioGiornaliera(filtered);

        model.addAttribute("costoUnitMedio", costoUnitMedio);
        model.addAttribute("costoUnitGiorno", costoUnitGiorno);

        // ===== SEZIONE 2: SCOMPOSIZIONE TOTALE (Stima) =====
        double totalAvgCost = costoUnitMedio;
        double totalAvgCostLabor = totalAvgCost * LABOR_RATIO;
        double totalAvgCostMaterials = totalAvgCost * MATERIALS_RATIO;

        model.addAttribute("totalAvgCost", totalAvgCost);
        model.addAttribute("totalAvgCostLabor", totalAvgCostLabor);
        model.addAttribute("totalAvgCostMaterials", totalAvgCostMaterials);

        // ===== SEZIONE 3: SERIE ANNUALI PER AREA =====
        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        List<SampleRecord> nordAll   = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll    = filterByArea(all, "Sud");

        Map<Integer, Double> annualCostNord   = kpiService.serieCostoUnitarioAnnuale(nordAll);
        Map<Integer, Double> annualCostCentro = kpiService.serieCostoUnitarioAnnuale(centroAll);
        Map<Integer, Double> annualCostSud    = kpiService.serieCostoUnitarioAnnuale(sudAll);

        model.addAttribute("years", years);
        model.addAttribute("annualCostNord", toOrderedList(annualCostNord, years));
        model.addAttribute("annualCostCentro", toOrderedList(annualCostCentro, years));
        model.addAttribute("annualCostSud", toOrderedList(annualCostSud, years));

        // ===== SEZIONE 4: KPI PER AREA (Periodo filtrato) =====
        List<SampleRecord> nordFiltered   = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered    = filterByArea(filtered, "Sud");

        double costNord   = kpiService.calcolaCostoUnitario(nordFiltered);
        double costCentro = kpiService.calcolaCostoUnitario(centroFiltered);
        double costSud    = kpiService.calcolaCostoUnitario(sudFiltered);

        double avgCostLaborNord   = costNord   * LABOR_RATIO;
        double avgCostLaborCentro = costCentro * LABOR_RATIO;
        double avgCostLaborSud    = costSud    * LABOR_RATIO;

        double avgCostMaterialsNord   = costNord   * MATERIALS_RATIO;
        double avgCostMaterialsCentro = costCentro * MATERIALS_RATIO;
        double avgCostMaterialsSud    = costSud    * MATERIALS_RATIO;

        model.addAttribute("avgCostLaborNord", avgCostLaborNord);
        model.addAttribute("avgCostLaborCentro", avgCostLaborCentro);
        model.addAttribute("avgCostLaborSud", avgCostLaborSud);
        model.addAttribute("avgCostMaterialsNord", avgCostMaterialsNord);
        model.addAttribute("avgCostMaterialsCentro", avgCostMaterialsCentro);
        model.addAttribute("avgCostMaterialsSud", avgCostMaterialsSud);

        // ===== SEZIONE 5: TABELLA DETTAGLI =====
        List<CostiRow> costiRows = Arrays.asList(
                new CostiRow("Nord", costNord, avgCostLaborNord, avgCostMaterialsNord),
                new CostiRow("Centro", costCentro, avgCostLaborCentro, avgCostMaterialsCentro),
                new CostiRow("Sud", costSud, avgCostLaborSud, avgCostMaterialsSud)
        );
        model.addAttribute("costiRows", costiRows);

        // ===== SERIE MENSILE PER AREA (Costo giornaliero) =====
        if (!filtered.isEmpty()) {
            Optional<LocalDate> maybeFirst = filtered.stream()
                    .map(SampleRecord::date)
                    .filter(Objects::nonNull)
                    .sorted()
                    .findFirst();
            if (maybeFirst.isPresent()) {
                LocalDate firstDate = maybeFirst.get();
                java.time.YearMonth selectedYearMonth = java.time.YearMonth.from(firstDate);

                Map<LocalDate, Double> giornNord   = kpiService.serieCostoUnitarioGiornaliera(nordFiltered);
                Map<LocalDate, Double> giornCentro = kpiService.serieCostoUnitarioGiornaliera(centroFiltered);
                Map<LocalDate, Double> giornSud    = kpiService.serieCostoUnitarioGiornaliera(sudFiltered);

                Map<LocalDate, Double> meseNord   = filterByYearMonth(giornNord, selectedYearMonth);
                Map<LocalDate, Double> meseCentro = filterByYearMonth(giornCentro, selectedYearMonth);
                Map<LocalDate, Double> meseSud    = filterByYearMonth(giornSud, selectedYearMonth);

                Set<Integer> daySet = new TreeSet<>();
                meseNord.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                meseCentro.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                meseSud.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                if (daySet.isEmpty()) daySet.add(1);

                List<String> dailyLabels = daySet.stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());

                List<Double> dailyCostNord = daySet.stream()
                        .map(g -> {
                            LocalDate d = LocalDate.of(
                                    selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseNord.getOrDefault(d, 0.0);
                        })
                        .collect(Collectors.toList());
                List<Double> dailyCostCentro = daySet.stream()
                        .map(g -> {
                            LocalDate d = LocalDate.of(
                                    selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseCentro.getOrDefault(d, 0.0);
                        })
                        .collect(Collectors.toList());
                List<Double> dailyCostSud = daySet.stream()
                        .map(g -> {
                            LocalDate d = LocalDate.of(
                                    selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseSud.getOrDefault(d, 0.0);
                        })
                        .collect(Collectors.toList());

                model.addAttribute("dailyLabels", dailyLabels);
                model.addAttribute("dailyCostNord", dailyCostNord);
                model.addAttribute("dailyCostCentro", dailyCostCentro);
                model.addAttribute("dailyCostSud", dailyCostSud);
            }
        } else {
            model.addAttribute("dailyLabels", Collections.emptyList());
            model.addAttribute("dailyCostNord", Collections.emptyList());
            model.addAttribute("dailyCostCentro", Collections.emptyList());
            model.addAttribute("dailyCostSud", Collections.emptyList());
        }
    }

    // ========================================================================
    // METODI HELPER PRIVATI
    // ========================================================================

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

    // ========================================================================
    // INNER CLASS: CostiRow
    // ========================================================================

    public static class CostiRow {
        private final String area;
        private final double totalCost;
        private final double laborCost;
        private final double materialsCost;

        public CostiRow(String area, double totalCost, double laborCost, double materialsCost) {
            this.area = area;
            this.totalCost = totalCost;
            this.laborCost = laborCost;
            this.materialsCost = materialsCost;
        }

        public String getArea() { return area; }
        public double getTotalCost() { return totalCost; }
        public double getLaborCost() { return laborCost; }
        public double getMaterialsCost() { return materialsCost; }
    }
}
