// src/main/java/it/floro/dashboard/web/ExportController.java
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
import java.time.format.DateTimeFormatter;
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
        sb.append("Data,Area,Campo,Coltura,Superficie (ha),Temp (°C),Umidità (%),Pioggia (mm),Indice Solare,Produzione (t),Acqua (m³),Costo (€),Prezzo (€/t)\n");

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (SampleRecord r : filtered) {
            sb.append(String.format(
                    "%s,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                    r.date() != null ? r.date().format(dateFormatter) : "",
                    safe(r.area()),
                    safe(r.field()),
                    safe(r.crop()),
                    r.surfaceHa(),
                    r.tempC(),
                    r.humidityPct(),
                    r.rainMm(),
                    r.solarIdx(),
                    r.yieldT(),
                    r.waterM3(),
                    r.costEur(),
                    r.priceEurT()
            ));
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        // 5) Nome file dinamico senza spazi
        String areaStr = (params.area() != null ? params.area() : "tutte_aree").replaceAll("\\s+", "_");
        String cropStr = (params.crop() != null ? params.crop() : "tutte_colture").replaceAll("\\s+", "_");
        String dateStr = params.start() != null ? params.start().format(DateTimeFormatter.ofPattern("yyyyMMdd")) : "data";

        String filename = String.format("export_%s_%s_%s.csv", areaStr, cropStr, dateStr);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .body(bytes);
    }

    private String safe(String s) {
        if (s == null || s.isEmpty()) return "";
        // Rimuove virgole e virgolette per evitare problemi nel CSV
        return s.replace(",", " ").replace("\"", "");
    }
}
