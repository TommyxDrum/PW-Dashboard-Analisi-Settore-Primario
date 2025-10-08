package it.floro.dashboard.service;

import it.floro.dashboard.domain.Kpi;
import it.floro.dashboard.domain.SampleRecord;
import org.springframework.stereotype.Service;

@Service
public class KpiService {
    public Kpi compute(SampleRecord r) {
        double yieldPerHa = safeDiv(r.yieldT(), r.surfaceHa());
        double waterEff = safeDiv(r.yieldT()*1000.0, r.waterM3());
        double unitCost = safeDiv(r.costEur(), r.yieldT());
        double unitMargin = safeDiv(r.priceEurT()*r.yieldT() - r.costEur(), r.yieldT());
        // climate risk normalized approx
        double tempN = normalize(r.tempC(), -5, 35);
        double humN = normalize(r.humidityPct(), 20, 90);
        double rainN = normalize(r.rainMm(), 0, 30);
        double risk = 0.4*tempN + 0.3*humN + 0.3*rainN;
        return new Kpi(yieldPerHa, waterEff, unitCost, unitMargin, risk);
    }

    private double safeDiv(double a, double b) { return (b==0)? Double.NaN : a/b; }
    private double normalize(double x, double lo, double hi) {
        if (hi<=lo) return 0;
        double v = (x - lo)/(hi - lo);
        if (v<0) v=0; if (v>1) v=1;
        return v;
    }
}
