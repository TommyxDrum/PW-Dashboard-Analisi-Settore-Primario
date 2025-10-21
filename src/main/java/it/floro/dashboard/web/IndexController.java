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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Controller principale della Dashboard.
 * Mostra un riepilogo sintetico dei KPI principali in base ai filtri selezionati.
 */
@Controller
public class IndexController extends BaseKpiController {

    public IndexController(SampleDataService sampleDataService,
                           KpiFilters kpiFilters,
                           KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(
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
                year, month, quarter, model, "index");
    }

    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {
        // Calcola KPI sintetici per le card in alto
        double resaMedia = kpiService.calcolaResaMedia(filtered);
        double efficienzaIdrica = kpiService.calcolaEfficienzaIdrica(filtered);
        double costoUnitario = kpiService.calcolaCostoUnitario(filtered);
        double margineUnitario = kpiService.calcolaMargineUnitario(filtered);
        double rischioClimatico = kpiService.calcolaRischioClimatico(filtered);

        model.addAttribute("avgYieldHa", resaMedia);
        model.addAttribute("avgEff", efficienzaIdrica);
        model.addAttribute("avgCost", costoUnitario);
        model.addAttribute("avgMargin", margineUnitario);
        model.addAttribute("avgRisk", rischioClimatico);

        // === DATI PER I GRAFICI ===

        // Filtra per area
        List<SampleRecord> nord = filterByArea(filtered, "Nord");
        List<SampleRecord> centro = filterByArea(filtered, "Centro");
        List<SampleRecord> sud = filterByArea(filtered, "Sud");

        // 1. GRAFICO RESA: Serie temporali annuali per area
        Map<Integer, Double> resaNord = kpiService.serieResaAnnuale(nord);
        Map<Integer, Double> resaCentro = kpiService.serieResaAnnuale(centro);
        Map<Integer, Double> resaSud = kpiService.serieResaAnnuale(sud);

        // Estrai tutti gli anni presenti
        List<Integer> years = extractYears(filtered);
        List<String> labels = years.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());

        // Allinea i valori agli anni (0.0 se mancante)
        List<Double> yieldsNord = years.stream()
                .map(y -> resaNord.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
        List<Double> yieldsCentro = years.stream()
                .map(y -> resaCentro.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
        List<Double> yieldsSud = years.stream()
                .map(y -> resaSud.getOrDefault(y, 0.0))
                .collect(Collectors.toList());

        model.addAttribute("labels", labels);
        model.addAttribute("yieldsNord", yieldsNord);
        model.addAttribute("yieldsCentro", yieldsCentro);
        model.addAttribute("yieldsSud", yieldsSud);

        // 2. GRAFICO EFFICIENZA IDRICA: Valori medi per area (grafico polare)
        double effNord = kpiService.calcolaEfficienzaIdrica(nord);
        double effCentro = kpiService.calcolaEfficienzaIdrica(centro);
        double effSud = kpiService.calcolaEfficienzaIdrica(sud);

        model.addAttribute("effNordKgM3", effNord);
        model.addAttribute("effCentroKgM3", effCentro);
        model.addAttribute("effSudKgM3", effSud);
        model.addAttribute("effMaxScale", Math.max(Math.max(effNord, effCentro), Math.max(effSud, 100.0)) * 1.2);

        // 3. GRAFICO COSTI: Valori medi per area (grafico barre)
        double costiNord = kpiService.calcolaCostoUnitario(nord);
        double costiCentro = kpiService.calcolaCostoUnitario(centro);
        double costiSud = kpiService.calcolaCostoUnitario(sud);

        model.addAttribute("costNord", costiNord);
        model.addAttribute("costCentro", costiCentro);
        model.addAttribute("costSud", costiSud);

        // 4. GRAFICO MARGINI: Valori medi per area (grafico barre)
        double margNord = kpiService.calcolaMargineUnitario(nord);
        double margCentro = kpiService.calcolaMargineUnitario(centro);
        double margSud = kpiService.calcolaMargineUnitario(sud);

        model.addAttribute("marginNord", margNord);
        model.addAttribute("marginCentro", margCentro);
        model.addAttribute("marginSud", margSud);

        // 5. GAUGE RISCHIO CLIMATICO: Valori medi per area
        double riskNord = kpiService.calcolaRischioClimatico(nord);
        double riskCentro = kpiService.calcolaRischioClimatico(centro);
        double riskSud = kpiService.calcolaRischioClimatico(sud);

        model.addAttribute("riskNord", riskNord);
        model.addAttribute("riskCentro", riskCentro);
        model.addAttribute("riskSud", riskSud);
    }
}