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
 * Controller che espone il dashboard del KPI "Rischio Climatico (indice 0-1)".
 *
 * Responsabilità:
 * - Gestire la richiesta HTTP GET /rischioClimatico con filtri opzionali
 * - Delega a KpiService il calcolo del rischio climatico
 * - Scomporre il rischio totale in tre componenti: temperatura, acqua, gelate
 * - Aggregare dati per visualizzazione multi-granulare (giornaliera, annuale, per area)
 * - Popolare il Model Thymeleaf con attributi per la view "rischioClimatico"
 *
 * Pattern: Estende BaseKpiController per riutilizzare logica comune
 * - Implementa l'hook astratto populateKpiModel
 * - Eredita il template method processKpiRequest
 * - Eredita utility per filtri (filterByArea, extractYears)
 *
 * KPI principale: Rischio climatico aggregato
 * - Scala: indice [0..1]
 * - 0 = nessun rischio (condizioni meteorologiche ideali)
 * - 1 = rischio massimo (condizioni meteorologiche critiche/estreme)
 * - Interpretazione: probabilità e severità di danni dovuti a stress meteorologico
 */
@Controller
public class RischioClimaticoController extends BaseKpiController {

    // Pesature delle componenti di rischio (stimate)
    private static final double TEMP_WEIGHT  = 0.50;
    private static final double WATER_WEIGHT = 0.30;
    private static final double FROST_WEIGHT = 0.20;

    public RischioClimaticoController(SampleDataService sampleDataService,
                                      KpiFilters kpiFilters,
                                      KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    @GetMapping("/rischioClimatico")
    public String rischioClimaticoKpi(
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
                year, month, quarter, model, "rischioClimatico");
    }

    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {

        // ===== SEZIONE 1: KPI BASE =====
        double rischioMedio = kpiService.calcolaRischioClimatico(filtered);
        Map<LocalDate, Double> rischioGiornaliero = kpiService.serieRischioClimaticoGiornaliera(filtered);
        model.addAttribute("rischioMedio", rischioMedio);
        model.addAttribute("rischioGiornaliero", rischioGiornaliero);

        // ===== SEZIONE 2: SCOMPOSIZIONE TOTALE =====
        double totalAvgRiskIndex = rischioMedio;
        double totalAvgRiskTemp  = rischioMedio * TEMP_WEIGHT;
        double totalAvgRiskWater = rischioMedio * WATER_WEIGHT;
        double totalAvgRiskFrost = rischioMedio * FROST_WEIGHT; // non visualizzato esplicitamente

        model.addAttribute("totalAvgRiskIndex", totalAvgRiskIndex);
        model.addAttribute("totalAvgRiskTemp", totalAvgRiskTemp);
        model.addAttribute("totalAvgRiskWater", totalAvgRiskWater);

        // ===== SEZIONE 3: SERIE ANNUALI PER AREA =====
        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        List<SampleRecord> nordAll   = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll    = filterByArea(all, "Sud");

        Map<Integer, Double> annualRiskNord   = kpiService.serieRischioClimaticoAnnuale(nordAll);
        Map<Integer, Double> annualRiskCentro = kpiService.serieRischioClimaticoAnnuale(centroAll);
        Map<Integer, Double> annualRiskSud    = kpiService.serieRischioClimaticoAnnuale(sudAll);

        model.addAttribute("years", years);
        model.addAttribute("annualRiskNord", toOrderedList(annualRiskNord, years));
        model.addAttribute("annualRiskCentro", toOrderedList(annualRiskCentro, years));
        model.addAttribute("annualRiskSud", toOrderedList(annualRiskSud, years));

        // ===== SEZIONE 4: KPI PER AREA =====
        List<SampleRecord> nordFiltered   = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered    = filterByArea(filtered, "Sud");

        double riskNord   = kpiService.calcolaRischioClimatico(nordFiltered);
        double riskCentro = kpiService.calcolaRischioClimatico(centroFiltered);
        double riskSud    = kpiService.calcolaRischioClimatico(sudFiltered);

        double riskTempNord   = riskNord   * TEMP_WEIGHT;
        double riskTempCentro = riskCentro * TEMP_WEIGHT;
        double riskTempSud    = riskSud    * TEMP_WEIGHT;

        double riskWaterNord   = riskNord   * WATER_WEIGHT;
        double riskWaterCentro = riskCentro * WATER_WEIGHT;
        double riskWaterSud    = riskSud    * WATER_WEIGHT;

        double riskFrostNord   = riskNord   * FROST_WEIGHT;
        double riskFrostCentro = riskCentro * FROST_WEIGHT;
        double riskFrostSud    = riskSud    * FROST_WEIGHT;

        model.addAttribute("riskTempNord", riskTempNord);
        model.addAttribute("riskTempCentro", riskTempCentro);
        model.addAttribute("riskTempSud", riskTempSud);
        model.addAttribute("riskWaterNord", riskWaterNord);
        model.addAttribute("riskWaterCentro", riskWaterCentro);
        model.addAttribute("riskWaterSud", riskWaterSud);
        model.addAttribute("riskFrostNord", riskFrostNord);
        model.addAttribute("riskFrostCentro", riskFrostCentro);
        model.addAttribute("riskFrostSud", riskFrostSud);

        // ===== SEZIONE 5: TABELLA DETTAGLI =====
        List<RischioRow> rischioRows = Arrays.asList(
                new RischioRow("Nord", riskNord, riskTempNord, riskWaterNord, riskFrostNord),
                new RischioRow("Centro", riskCentro, riskTempCentro, riskWaterCentro, riskFrostCentro),
                new RischioRow("Sud", riskSud, riskTempSud, riskWaterSud, riskFrostSud)
        );
        model.addAttribute("rischioRows", rischioRows);

        // ===== SERIE MENSILE PER AREA =====
        if (!filtered.isEmpty()) {
            Optional<LocalDate> maybeFirstDate = filtered.stream()
                    .map(SampleRecord::date)
                    .filter(Objects::nonNull)
                    .sorted()
                    .findFirst();
            if (maybeFirstDate.isPresent()) {
                LocalDate firstDate = maybeFirstDate.get();
                java.time.YearMonth selectedYearMonth = java.time.YearMonth.from(firstDate);

                Map<LocalDate, Double> giornNord   = kpiService.serieRischioClimaticoGiornaliera(nordFiltered);
                Map<LocalDate, Double> giornCentro = kpiService.serieRischioClimaticoGiornaliera(centroFiltered);
                Map<LocalDate, Double> giornSud    = kpiService.serieRischioClimaticoGiornaliera(sudFiltered);

                Map<LocalDate, Double> meseNord   = filterByYearMonth(giornNord, selectedYearMonth);
                Map<LocalDate, Double> meseCentro = filterByYearMonth(giornCentro, selectedYearMonth);
                Map<LocalDate, Double> meseSud    = filterByYearMonth(giornSud, selectedYearMonth);

                java.util.Set<Integer> daySet = new java.util.TreeSet<>();
                meseNord.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                meseCentro.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                meseSud.keySet().forEach(d -> daySet.add(d.getDayOfMonth()));
                if (daySet.isEmpty()) daySet.add(1);

                List<String> dailyLabels = daySet.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.toList());

                java.util.List<Double> dailyRiskNord = daySet.stream()
                        .map(g -> {
                            java.time.LocalDate d = java.time.LocalDate.of(
                                    selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseNord.getOrDefault(d, 0.0);
                        })
                        .collect(java.util.stream.Collectors.toList());
                java.util.List<Double> dailyRiskCentro = daySet.stream()
                        .map(g -> {
                            java.time.LocalDate d = java.time.LocalDate.of(
                                    selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseCentro.getOrDefault(d, 0.0);
                        })
                        .collect(java.util.stream.Collectors.toList());
                java.util.List<Double> dailyRiskSud = daySet.stream()
                        .map(g -> {
                            java.time.LocalDate d = java.time.LocalDate.of(
                                    selectedYearMonth.getYear(), selectedYearMonth.getMonthValue(), g);
                            return meseSud.getOrDefault(d, 0.0);
                        })
                        .collect(java.util.stream.Collectors.toList());

                model.addAttribute("dailyLabels", dailyLabels);
                model.addAttribute("dailyRiskNord", dailyRiskNord);
                model.addAttribute("dailyRiskCentro", dailyRiskCentro);
                model.addAttribute("dailyRiskSud", dailyRiskSud);
            }
        } else {
            model.addAttribute("dailyLabels", java.util.Collections.emptyList());
            model.addAttribute("dailyRiskNord", java.util.Collections.emptyList());
            model.addAttribute("dailyRiskCentro", java.util.Collections.emptyList());
            model.addAttribute("dailyRiskSud", java.util.Collections.emptyList());
        }
    }

    // ===== HELPER METHODS =====

    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    /**
     * Filtra una serie temporale giornaliera per mantenere solo le date appartenenti
     * a un determinato YearMonth. La serie risultante è ordinata per data.
     *
     * @param timeSeries mappa <LocalDate, Double> con la serie giornaliera completa
     * @param yearMonth  il mese e l'anno da filtrare
     * @return mappa ordinata con le sole date appartenenti al mese indicato
     */
    private Map<LocalDate, Double> filterByYearMonth(Map<LocalDate, Double> timeSeries,
                                                     java.time.YearMonth yearMonth) {
        return timeSeries.entrySet().stream()
                .filter(e -> java.time.YearMonth.from(e.getKey()).equals(yearMonth))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        java.util.TreeMap::new
                ));
    }

    // ===== INNER CLASS =====
    public static class RischioRow {
        private final String area;
        private final double riskIndex;
        private final double riskTemp;
        private final double riskWater;
        private final double riskFrost;

        public RischioRow(String area, double riskIndex, double riskTemp, double riskWater, double riskFrost) {
            this.area      = area;
            this.riskIndex = riskIndex;
            this.riskTemp  = riskTemp;
            this.riskWater = riskWater;
            this.riskFrost = riskFrost;
        }

        public String getArea() { return area; }
        public double getRiskIndex() { return riskIndex; }
        public double getRiskTemp() { return riskTemp; }
        public double getRiskWater() { return riskWater; }
        public double getRiskFrost() { return riskFrost; }
    }
}
