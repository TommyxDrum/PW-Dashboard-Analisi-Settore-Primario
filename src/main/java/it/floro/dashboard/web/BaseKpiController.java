package it.floro.dashboard.web;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.service.KpiFilters;
import it.floro.dashboard.service.KpiFilters.FilterParams;
import it.floro.dashboard.service.KpiService;
import it.floro.dashboard.service.SampleDataService;
import org.springframework.ui.Model;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller base astratto che centralizza la logica comune di tutti i KPI controller.
 * Elimina la duplicazione di codice fornendo metodi template comuni.
 */
public abstract class BaseKpiController {

    protected final SampleDataService sampleDataService;
    protected final KpiFilters kpiFilters;
    protected final KpiService kpiService;

    protected BaseKpiController(SampleDataService sampleDataService,
                                KpiFilters kpiFilters,
                                KpiService kpiService) {
        this.sampleDataService = sampleDataService;
        this.kpiFilters = kpiFilters;
        this.kpiService = kpiService;
    }

    /**
     * Template method: esegue il flusso comune di tutti i controller KPI.
     * Le sottoclassi devono implementare populateKpiModel per aggiungere i KPI specifici.
     */
    protected String processKpiRequest(
            String area,
            String crop,
            LocalDate startDate,
            LocalDate endDate,
            String periodo,
            Integer year,
            Integer month,
            Integer quarter,
            Model model,
            String viewName
    ) {
        // 1) Recupera tutti i dati
        List<SampleRecord> all = sampleDataService.getAll();

        // 2) Costruisci i filtri
        FilterParams params = kpiFilters.fromRequest(
                area, crop, startDate, endDate, periodo, year, month, quarter,
                sampleDataService.getMinDate(), sampleDataService.getMaxDate()
        );

        // 3) Applica i filtri
        List<SampleRecord> filtered = all.stream()
                .filter(kpiFilters.predicate(params))
                .collect(Collectors.toList());

        // 4) Popola il model con i KPI specifici (delegato alle sottoclassi)
        populateKpiModel(filtered, model);

        // 5) Aggiungi dati comuni a tutti i controller
        populateCommonAttributes(model, all, params);

        return viewName;
    }

    /**
     * Metodo astratto da implementare nelle sottoclassi per aggiungere KPI specifici.
     */
    protected abstract void populateKpiModel(List<SampleRecord> filtered, Model model);

    /**
     * Popola gli attributi comuni a tutti i controller KPI.
     */
    private void populateCommonAttributes(Model model, List<SampleRecord> all, FilterParams params) {
        model.addAttribute("areasList", kpiFilters.areasFrom(all));
        model.addAttribute("cropsList", kpiFilters.cropsFrom(all));
        model.addAttribute("selectedArea", params.area());
        model.addAttribute("selectedCrop", params.crop());
        model.addAttribute("startDate", params.start());
        model.addAttribute("endDate", params.end());
        model.addAttribute("from", params.start());
        model.addAttribute("to", params.end());
        model.addAttribute("area", params.area());
        model.addAttribute("crop", params.crop());
        model.addAttribute("periodo", params.periodo().name());
    }

    /**
     * Filtra records per area specifica (case e accent insensitive).
     */
    protected List<SampleRecord> filterByArea(List<SampleRecord> records, String area) {
        if (area == null || area.isBlank()) {
            return records;
        }
        String areaNorm = normalizeString(area);
        return records.stream()
                .filter(r -> r.area() != null && normalizeString(r.area()).equals(areaNorm))
                .collect(Collectors.toList());
    }

    /**
     * Genera lista di anni dalla data minima alla massima nei record.
     */
    protected List<Integer> extractYears(List<SampleRecord> records) {
        return records.stream()
                .map(SampleRecord::date)
                .filter(d -> d != null)
                .map(LocalDate::getYear)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Normalizza stringa per confronti case e accent insensitive.
     */
    private String normalizeString(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .trim();
    }
}