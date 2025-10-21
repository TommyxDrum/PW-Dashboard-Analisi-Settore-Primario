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
 * Controller KPI "Costo unitario (€/t)" - ARRICCHITO.
 * NOTA: I template si aspettano anche scomposizione manodopera/materiali,
 * che non è presente nei dati attuali. Useremo valori stimati.
 */
@Controller
public class CostiController extends BaseKpiController {

    // Stima: 60% manodopera, 40% materiali del costo totale
    private static final double LABOR_RATIO = 0.60;
    private static final double MATERIALS_RATIO = 0.40;

    public CostiController(SampleDataService sampleDataService,
                           KpiFilters kpiFilters,
                           KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

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

    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {
        // KPI Base
        double costoUnitMedio = kpiService.calcolaCostoUnitario(filtered);
        Map<LocalDate, Double> costoUnitGiorno = kpiService.serieCostoUnitarioGiornaliera(filtered);

        model.addAttribute("costoUnitMedio", costoUnitMedio);
        model.addAttribute("costoUnitGiorno", costoUnitGiorno);

        // Totali con scomposizione stimata
        double totalAvgCost = costoUnitMedio;
        double totalAvgCostLabor = totalAvgCost * LABOR_RATIO;
        double totalAvgCostMaterials = totalAvgCost * MATERIALS_RATIO;

        model.addAttribute("totalAvgCost", totalAvgCost);
        model.addAttribute("totalAvgCostLabor", totalAvgCostLabor);
        model.addAttribute("totalAvgCostMaterials", totalAvgCostMaterials);

        // Serie annuali per area
        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        List<SampleRecord> nordAll = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll = filterByArea(all, "Sud");

        Map<Integer, Double> annualCostNord = kpiService.serieCostoUnitarioAnnuale(nordAll);
        Map<Integer, Double> annualCostCentro = kpiService.serieCostoUnitarioAnnuale(centroAll);
        Map<Integer, Double> annualCostSud = kpiService.serieCostoUnitarioAnnuale(sudAll);

        model.addAttribute("years", years);
        model.addAttribute("annualCostNord", toOrderedList(annualCostNord, years));
        model.addAttribute("annualCostCentro", toOrderedList(annualCostCentro, years));
        model.addAttribute("annualCostSud", toOrderedList(annualCostSud, years));

        // Dati per area (periodo filtrato) - per bar chart e tabella
        List<SampleRecord> nordFiltered = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered = filterByArea(filtered, "Sud");

        double costNord = kpiService.calcolaCostoUnitario(nordFiltered);
        double costCentro = kpiService.calcolaCostoUnitario(centroFiltered);
        double costSud = kpiService.calcolaCostoUnitario(sudFiltered);

        // Scomposizione stimata per area
        double avgCostLaborNord = costNord * LABOR_RATIO;
        double avgCostLaborCentro = costCentro * LABOR_RATIO;
        double avgCostLaborSud = costSud * LABOR_RATIO;

        double avgCostMaterialsNord = costNord * MATERIALS_RATIO;
        double avgCostMaterialsCentro = costCentro * MATERIALS_RATIO;
        double avgCostMaterialsSud = costSud * MATERIALS_RATIO;

        model.addAttribute("avgCostLaborNord", avgCostLaborNord);
        model.addAttribute("avgCostLaborCentro", avgCostLaborCentro);
        model.addAttribute("avgCostLaborSud", avgCostLaborSud);
        model.addAttribute("avgCostMaterialsNord", avgCostMaterialsNord);
        model.addAttribute("avgCostMaterialsCentro", avgCostMaterialsCentro);
        model.addAttribute("avgCostMaterialsSud", avgCostMaterialsSud);

        // Tabella dettagli
        List<CostiRow> costiRows = Arrays.asList(
                new CostiRow("Nord", costNord, avgCostLaborNord, avgCostMaterialsNord),
                new CostiRow("Centro", costCentro, avgCostLaborCentro, avgCostMaterialsCentro),
                new CostiRow("Sud", costSud, avgCostLaborSud, avgCostMaterialsSud)
        );
        model.addAttribute("costiRows", costiRows);
    }

    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

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