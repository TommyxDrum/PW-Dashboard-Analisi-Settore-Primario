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
 * Controller KPI "Margine unitario (€/t)" - ARRICCHITO.
 */
@Controller
public class MargineController extends BaseKpiController {

    public MargineController(SampleDataService sampleDataService,
                             KpiFilters kpiFilters,
                             KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    @GetMapping("/margine")
    public String margineKpi(
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
                year, month, quarter, model, "margine");
    }

    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {
        // KPI Base
        double margineUnitMedio = kpiService.calcolaMargineUnitario(filtered);
        Map<LocalDate, Double> margineUnitGiorno = kpiService.serieMargineUnitarioGiornaliera(filtered);

        model.addAttribute("margineUnitMedio", margineUnitMedio);
        model.addAttribute("margineUnitGiorno", margineUnitGiorno);

        // Totali (prezzo, costo, margine)
        double totalAvgPrice = kpiService.prezzoMedio(filtered);
        double totalAvgCost = kpiService.calcolaCostoUnitario(filtered);
        double totalAvgMargin = margineUnitMedio;

        model.addAttribute("totalAvgPrice", totalAvgPrice);
        model.addAttribute("totalAvgCost", totalAvgCost);
        model.addAttribute("totalAvgMargin", totalAvgMargin);

        // Serie annuali per area
        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        List<SampleRecord> nordAll = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll = filterByArea(all, "Sud");

        Map<Integer, Double> annualMarginNord = kpiService.serieMargineUnitarioAnnuale(nordAll);
        Map<Integer, Double> annualMarginCentro = kpiService.serieMargineUnitarioAnnuale(centroAll);
        Map<Integer, Double> annualMarginSud = kpiService.serieMargineUnitarioAnnuale(sudAll);

        model.addAttribute("years", years);
        model.addAttribute("annualMarginNord", toOrderedList(annualMarginNord, years));
        model.addAttribute("annualMarginCentro", toOrderedList(annualMarginCentro, years));
        model.addAttribute("annualMarginSud", toOrderedList(annualMarginSud, years));

        // Dati per area (periodo filtrato) - per bar chart e tabella
        List<SampleRecord> nordFiltered = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered = filterByArea(filtered, "Sud");

        double marginNord = kpiService.calcolaMargineUnitario(nordFiltered);
        double marginCentro = kpiService.calcolaMargineUnitario(centroFiltered);
        double marginSud = kpiService.calcolaMargineUnitario(sudFiltered);

        double costNord = kpiService.calcolaCostoUnitario(nordFiltered);
        double costCentro = kpiService.calcolaCostoUnitario(centroFiltered);
        double costSud = kpiService.calcolaCostoUnitario(sudFiltered);

        model.addAttribute("avgMarginNord", marginNord);
        model.addAttribute("avgMarginCentro", marginCentro);
        model.addAttribute("avgMarginSud", marginSud);
        model.addAttribute("avgCostNord", costNord);
        model.addAttribute("avgCostCentro", costCentro);
        model.addAttribute("avgCostSud", costSud);

        // Tabella dettagli
        double priceNord = kpiService.prezzoMedio(nordFiltered);
        double priceCentro = kpiService.prezzoMedio(centroFiltered);
        double priceSud = kpiService.prezzoMedio(sudFiltered);

        List<MargineRow> margineRows = Arrays.asList(
                new MargineRow("Nord", priceNord, costNord, marginNord),
                new MargineRow("Centro", priceCentro, costCentro, marginCentro),
                new MargineRow("Sud", priceSud, costSud, marginSud)
        );
        model.addAttribute("margineRows", margineRows);
    }

    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    public static class MargineRow {
        private final String area;
        private final double price;
        private final double cost;
        private final double margin;

        public MargineRow(String area, double price, double cost, double margin) {
            this.area = area;
            this.price = price;
            this.cost = cost;
            this.margin = margin;
        }

        public String getArea() { return area; }
        public double getPrice() { return price; }
        public double getCost() { return cost; }
        public double getMargin() { return margin; }
    }
}