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
 * Controller KPI "Resa per ettaro (t/ha)" - ARRICCHITO.
 * Fornisce tutti i dati necessari per resa.html.
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
        // KPI Base
        double resaMedia = kpiService.calcolaResaMedia(filtered);
        Map<LocalDate, Double> resaGiornaliera = kpiService.serieResaGiornaliera(filtered);

        model.addAttribute("resaMedia", resaMedia);
        model.addAttribute("resaGiornaliera", resaGiornaliera);

        // Totali globali
        double totalYieldT = kpiService.sommaProduzione(filtered);
        double totalSurfaceHa = kpiService.sommaSuperficie(filtered);
        double totalResa = totalSurfaceHa > 0 ? totalYieldT / totalSurfaceHa : 0.0;

        model.addAttribute("totalYieldT", totalYieldT);
        model.addAttribute("totalSurfaceHa", totalSurfaceHa);
        model.addAttribute("totalResa", totalResa);

        // Serie annuali per area (per il grafico a linee)
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

        // Dati per area (periodo filtrato) - per il donut chart
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

        // Tabella dettagli per area
        List<ResaRow> resaRows = Arrays.asList(
                new ResaRow("Nord", yieldNordT, surfNordHa, surfNordHa > 0 ? yieldNordT / surfNordHa : 0),
                new ResaRow("Centro", yieldCentroT, surfCentroHa, surfCentroHa > 0 ? yieldCentroT / surfCentroHa : 0),
                new ResaRow("Sud", yieldSudT, surfSudHa, surfSudHa > 0 ? yieldSudT / surfSudHa : 0)
        );
        model.addAttribute("resaRows", resaRows);
    }

    /**
     * Converte una Map<Integer, Double> in List<Double> ordinata per years.
     */
    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    /**
     * DTO per la tabella dettagli resa
     */
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