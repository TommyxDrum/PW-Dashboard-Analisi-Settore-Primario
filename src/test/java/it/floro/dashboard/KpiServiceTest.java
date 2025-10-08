package it.floro.dashboard;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.service.KpiService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;

public class KpiServiceTest {
    @Test
    void testKpiNotNaN() {
        var r = new SampleRecord(LocalDate.of(2024,1,1), "Nord", "A01", "Grano duro",
                2.0, 20, 50, 5, 0.5, 3.5, 150, 400, 280);
        var k = new KpiService().compute(r);
        assertFalse(Double.isNaN(k.yieldPerHa()));
        assertFalse(Double.isNaN(k.waterEfficiencyKgPerM3()));
    }
}
