package it.floro.dashboard.web;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.service.KpiFilters;
import it.floro.dashboard.service.KpiFilters.FilterParams;
import it.floro.dashboard.service.SampleDataService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Controller per l'esportazione dei dati filtrati in formato CSV.
 * Esporta solo i record corrispondenti ai filtri selezionati dal front-end.
 */
@Controller
public class ExportController {

    private final SampleDataService sampleDataService;
    private final KpiFilters kpiFilters;

    public ExportController(SampleDataService sampleDataService, KpiFilters kpiFilters) {
        this.sampleDataService = sampleDataService;
        this.kpiFilters = kpiFilters;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer quarter
    ) {
        // 1) Recupera i dati completi
        List<SampleRecord> all = sampleDataService.getAll();

        // 2) Costruisci i parametri filtro
        FilterParams params = kpiFilters.fromRequest(
                area, crop, startDate, endDate, periodo, year, month, quarter,
                sampleDataService.getMinDate(), sampleDataService.getMaxDate()
        );

        // 3) Applica il filtro ai record
        List<SampleRecord> filtered = all.stream()
                .filter(kpiFilters.predicate(params))
                .collect(Collectors.toList());

        // 4) Converte in CSV
        StringBuilder sb = new StringBuilder();
        sb.append("Data,Area,Coltura,Superficie (ha),Produzione (t),Costo (€),Prezzo (€/t),Efficienza Idrica (kg/m³),Rischio Climatico (0-1)\n");

        for (SampleRecord r : filtered) {
            sb.append(String.format("%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                    r.date(),
                    safe(r.area()),
                    safe(r.crop()),
                    r.surfaceHa(),
                    r.yieldT(),
                    r.costEur(),
                    r.priceEurT(),
                    (r.waterM3() > 0 ? (r.yieldT() * 1000 / r.waterM3()) : 0),
                    r.solarIdx() // opzionalmente puoi sostituire con un indice da KpiService
            ));
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        // 5) Nome file dinamico
        String filename = String.format("export_%s_%s.csv",
                (params.area() != null ? params.area() : "all"),
                (params.crop() != null ? params.crop() : "all")
        ).replaceAll("\\s+", "_");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .body(bytes);
    }

    private String safe(String s) {
        return s != null ? s.replace(",", " ") : "";
    }
}
