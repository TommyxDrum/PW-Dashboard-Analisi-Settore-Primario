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
import java.util.Locale;
import java.util.stream.Collectors;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Export CSV compatibile Excel ITA:
 * - Separatore di campo: ';'
 * - Decimali con virgola
 * - BOM UTF-8 per migliorare riconoscimento in Excel
 */
@Controller
public class ExportController {

    private static final char DELIMITER = ';';
    private static final String NEWLINE = "\n";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILENAME_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

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

        // ===== STEP 1: DATI =====
        List<SampleRecord> all = sampleDataService.getAll();

        // ===== STEP 2: FILTRI =====
        FilterParams params = kpiFilters.fromRequest(
                area, crop, startDate, endDate, periodo, year, month, quarter,
                sampleDataService.getMinDate(), sampleDataService.getMaxDate()
        );

        // ===== STEP 3: APPLY =====
        List<SampleRecord> filtered = all.stream()
                .filter(kpiFilters.predicate(params))
                .collect(Collectors.toList());

        // ===== STEP 4: CSV =====
        StringBuilder sb = new StringBuilder(128 + filtered.size() * 128);

        // Header con ';'
        sb.append(String.join(String.valueOf(DELIMITER),
                "Data",
                "Area",
                "Campo",
                "Coltura",
                "Superficie (ha)",
                "Temp (°C)",
                "Umidità (%)",
                "Pioggia (mm)",
                "Indice Solare",
                "Produzione (t)",
                "Acqua (m³)",
                "Costo (€)",
                "Prezzo (€/t)"
        )).append(NEWLINE);

        for (SampleRecord r : filtered) {
            sb.append(nullSafeDate(r.date()))
                    .append(DELIMITER).append(safe(r.area()))
                    .append(DELIMITER).append(safe(r.field()))
                    .append(DELIMITER).append(safe(r.crop()))
                    .append(DELIMITER).append(numIt(r.surfaceHa()))
                    .append(DELIMITER).append(numIt(r.tempC()))
                    .append(DELIMITER).append(numIt(r.humidityPct()))
                    .append(DELIMITER).append(numIt(r.rainMm()))
                    .append(DELIMITER).append(numIt(r.solarIdx()))
                    .append(DELIMITER).append(numIt(r.yieldT()))
                    .append(DELIMITER).append(numIt(r.waterM3()))
                    .append(DELIMITER).append(numIt(r.costEur()))
                    .append(DELIMITER).append(numIt(r.priceEurT()))
                    .append(NEWLINE);
        }

        // ===== STEP 5: NOME FILE =====
        String areaStr = (params.area() != null ? params.area() : "tutte_aree").replaceAll("\\s+", "_");
        String cropStr = (params.crop() != null ? params.crop() : "tutte_colture").replaceAll("\\s+", "_");
        String dateStr = params.start() != null ? params.start().format(FILENAME_DATE_FMT) : "data";
        String filename = String.format("export_%s_%s_%s.csv", areaStr, cropStr, dateStr);

        // ===== STEP 6: BYTES + BOM UTF-8 =====
        // Prepend BOM per Excel
        byte[] bom = new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF};
        byte[] csv = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[bom.length + csv.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(csv, 0, bytes, bom.length, csv.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .body(bytes);
    }

    // ===================== Helpers =====================

    private String nullSafeDate(LocalDate d) {
        return d != null ? d.format(DATE_FMT) : "";
    }

    /**
     * Sanitizzazione per campi testuali:
     * - Rimuove doppi apici
     * - Rimuove CR/LF (niente newline in un campo)
     * - CONSENTE le virgole (ora il delimitatore è ';')
     * - Mitiga CSV injection: se inizia con = + - @, prefissa apostrofo
     */
    private String safe(String s) {
        if (s == null || s.isEmpty()) return "";
        String cleaned = s.replace("\"", "").replace("\r", " ").replace("\n", " ");
        if (!cleaned.isEmpty()) {
            char c = cleaned.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@') {
                cleaned = "'" + cleaned;
            }
        }
        return cleaned;
    }

    /**
     * Formatta con 2 decimali, punto → virgola (locale ITA), senza grouping.
     */
    private String numIt(double v) {
        // Usa Locale.ROOT per avere il punto, poi sostituisci con virgola.
        return String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
    }
}
