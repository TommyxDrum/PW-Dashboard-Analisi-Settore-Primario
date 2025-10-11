package it.floro.dashboard.web;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.simulator.DataSimulator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
public class ExportController {
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export() {
        List<SampleRecord> data = new DataSimulator(42L, LocalDate.of(2024,1,1), 365, 8).generate();
        StringBuilder sb = new StringBuilder();
        sb.append("date,area,field,crop,yield_t,water_m3,cost_eur\n");
        for (SampleRecord r : data) {
            sb.append(r.date()).append(',')
                    .append(r.area()).append(',')
                    .append(r.field()).append(',')
                    .append(r.crop()).append(',')
                    .append(String.format("%.2f", r.yieldT())).append(',')
                    .append(String.format("%.2f", r.waterM3())).append(',')
                    .append(String.format("%.2f", r.costEur())).append('\n');
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(bytes);
    }
}