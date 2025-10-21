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
 * Controller KPI "Rischio Climatico (indice 0-1)" - ARRICCHITO.
 * NOTA: I dati attuali non hanno scomposizione rischio (temp/water/frost).
 * Useremo valori stimati basati sul rischio totale.
 */
@Controller
public class RischioClimaticoController extends BaseKpiController {

    // Stima distribuzione: 50% temp, 30% water, 20% frost
    private static final double TEMP_WEIGHT = 0.50;
    private static final double WATER_WEIGHT = 0.30;
    private static final double FROST_WEIGHT = 0.20;

    public RischioClimaticoController(SampleDataService sampleDataService,
                                      KpiFilters kpiFilters,
                                      KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    @GetMapping("/rischio-climatico")
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
        // KPI Base
        double rischioMedio = kpiService.calcolaRischioClimatico(filtered);
        Map<LocalDate, Double> rischioGiornaliero = kpiService.serieRischioClimaticoGiornaliera(filtered);

        model.addAttribute("rischioMedio", rischioMedio);
        model.addAttribute("rischioGiornaliero", rischioGiornaliero);

        // Totali con scomposizione stimata
        double totalAvgRiskIndex = rischioMedio;
        double totalAvgRiskTemp = rischioMedio * TEMP_WEIGHT;
        double totalAvgRiskWater = rischioMedio * WATER_WEIGHT;
        double totalAvgRiskFrost = rischioMedio * FROST_WEIGHT;

        model.addAttribute("totalAvgRiskIndex", totalAvgRiskIndex);
        model.addAttribute("totalAvgRiskTemp", totalAvgRiskTemp);
        model.addAttribute("totalAvgRiskWater", totalAvgRiskWater);

        // Serie annuali per area
        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        List<SampleRecord> nordAll = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll = filterByArea(all, "Sud");

        Map<Integer, Double> annualRiskNord = kpiService.serieRischioClimaticoAnnuale(nordAll);
        Map<Integer, Double> annualRiskCentro = kpiService.serieRischioClimaticoAnnuale(centroAll);
        Map<Integer, Double> annualRiskSud = kpiService.serieRischioClimaticoAnnuale(sudAll);

        model.addAttribute("years", years);
        model.addAttribute("annualRiskNord", toOrderedList(annualRiskNord, years));
        model.addAttribute("annualRiskCentro", toOrderedList(annualRiskCentro, years));
        model.addAttribute("annualRiskSud", toOrderedList(annualRiskSud, years));

        // Dati per area (periodo filtrato) - per radar chart e tabella
        List<SampleRecord> nordFiltered = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered = filterByArea(filtered, "Sud");

        double riskNord = kpiService.calcolaRischioClimatico(nordFiltered);
        double riskCentro = kpiService.calcolaRischioClimatico(centroFiltered);
        double riskSud = kpiService.calcolaRischioClimatico(sudFiltered);

        // Scomposizione stimata per area
        double riskTempNord = riskNord * TEMP_WEIGHT;
        double riskTempCentro = riskCentro * TEMP_WEIGHT;
        double riskTempSud = riskSud * TEMP_WEIGHT;

        double riskWaterNord = riskNord * WATER_WEIGHT;
        double riskWaterCentro = riskCentro * WATER_WEIGHT;
        double riskWaterSud = riskSud * WATER_WEIGHT;

        double riskFrostNord = riskNord * FROST_WEIGHT;
        double riskFrostCentro = riskCentro * FROST_WEIGHT;
        double riskFrostSud = riskSud * FROST_WEIGHT;

        model.addAttribute("riskTempNord", riskTempNord);
        model.addAttribute("riskTempCentro", riskTempCentro);
        model.addAttribute("riskTempSud", riskTempSud);
        model.addAttribute("riskWaterNord", riskWaterNord);
        model.addAttribute("riskWaterCentro", riskWaterCentro);
        model.addAttribute("riskWaterSud", riskWaterSud);
        model.addAttribute("riskFrostNord", riskFrostNord);
        model.addAttribute("riskFrostCentro", riskFrostCentro);
        model.addAttribute("riskFrostSud", riskFrostSud);

        // Tabella dettagli
        List<RischioRow> rischioRows = Arrays.asList(
                new RischioRow("Nord", riskNord, riskTempNord, riskWaterNord, riskFrostNord),
                new RischioRow("Centro", riskCentro, riskTempCentro, riskWaterCentro, riskFrostCentro),
                new RischioRow("Sud", riskSud, riskTempSud, riskWaterSud, riskFrostSud)
        );
        model.addAttribute("rischioRows", rischioRows);
    }

    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    public static class RischioRow {
        private final String area;
        private final double riskIndex;
        private final double riskTemp;
        private final double riskWater;
        private final double riskFrost;

        public RischioRow(String area, double riskIndex, double riskTemp, double riskWater, double riskFrost) {
            this.area = area;
            this.riskIndex = riskIndex;
            this.riskTemp = riskTemp;
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