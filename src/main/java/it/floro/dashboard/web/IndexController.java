package it.floro.dashboard.web;

import it.floro.dashboard.domain.Kpi;
import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.service.KpiService;
import it.floro.dashboard.simulator.DataSimulator;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@Controller
public class IndexController {

    private List<SampleRecord> cached;
    private final KpiService kpiService;

    private static final LocalDate MAX_DATE = LocalDate.now();
    private static final LocalDate MIN_DATE = MAX_DATE.minusYears(10).withDayOfYear(1);

    public IndexController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    private void ensureData(long seed, LocalDate start, LocalDate end, int fields) {
        if (cached == null) {
            int days = (int) ChronoUnit.DAYS.between(start, end) + 1;
            cached = new DataSimulator(seed, start, days, fields).generate();
        }
    }

    @GetMapping("/")
    public String overview(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(name = "crop", required = false) String crop,
            @RequestParam(name = "area", required = false) String area,
            Model model) {

        ensureData(42L, MIN_DATE, MAX_DATE, 8);

        LocalDate fromDate = (from != null) ? from : MIN_DATE;
        LocalDate toDate   = (to   != null) ? to   : MAX_DATE;

        if (fromDate.isBefore(MIN_DATE)) fromDate = MIN_DATE;
        if (fromDate.isAfter(MAX_DATE))  fromDate = MAX_DATE;
        if (toDate.isAfter(MAX_DATE))    toDate   = MAX_DATE;
        if (toDate.isBefore(MIN_DATE))   toDate   = MIN_DATE;

        if (toDate.isBefore(fromDate)) {
            LocalDate tmp = fromDate; fromDate = toDate; toDate = tmp;
        }

        String cropNorm = (crop == null || crop.isBlank()) ? null : crop.trim();
        String areaNorm = (area == null || area.isBlank()) ? null : area.trim();

        final LocalDate fFrom = fromDate;
        final LocalDate fTo   = toDate;
        final String fCrop    = cropNorm;
        final String fArea    = areaNorm;

        List<SampleRecord> filtered = cached.stream()
                .filter(r -> !r.date().isBefore(fFrom) && !r.date().isAfter(fTo))
                .filter(r -> fCrop == null || fCrop.equalsIgnoreCase(r.crop()))
                .filter(r -> fArea == null || fArea.equalsIgnoreCase(r.area()))
                .toList();

        List<LocalDate> dateRange = new ArrayList<>();
        for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
            dateRange.add(d);
        }
        List<String> labels = dateRange.stream().map(LocalDate::toString).toList();

        Map<String, Map<LocalDate, Double>> sumByAreaDate = filtered.stream()
                .collect(Collectors.groupingBy(
                        SampleRecord::area,
                        Collectors.groupingBy(
                                SampleRecord::date,
                                Collectors.summingDouble(SampleRecord::yieldT)
                        )
                ));

        List<Double> yieldsNord   = seriesForArea("Nord",   dateRange, sumByAreaDate);
        List<Double> yieldsCentro = seriesForArea("Centro", dateRange, sumByAreaDate);
        List<Double> yieldsSud    = seriesForArea("Sud",    dateRange, sumByAreaDate);

        // KPI per record filtrati
        List<Kpi> kpis = filtered.stream().map(kpiService::compute).toList();

        double avgYieldHa = kpis.stream().mapToDouble(Kpi::yieldPerHa).filter(Double::isFinite).average().orElse(0.0);
        double avgEff     = kpis.stream().mapToDouble(Kpi::waterEfficiencyKgPerM3).filter(Double::isFinite).average().orElse(0.0);
        double avgCost    = kpis.stream().mapToDouble(Kpi::unitCostEurPerT).filter(Double::isFinite).average().orElse(0.0);
        double avgMargin  = kpis.stream().mapToDouble(Kpi::unitMarginEurPerT).filter(Double::isFinite).average().orElse(0.0);
        double avgRisk    = kpis.stream().mapToDouble(Kpi::climateRiskIdx).filter(Double::isFinite).average().orElse(0.0);

        // Efficienza media per coltura (solo per riepiloghi)
        Map<String, Double> byCropEff = filtered.stream().collect(
                Collectors.groupingBy(
                        SampleRecord::crop,
                        Collectors.averagingDouble(r ->
                                (r.waterM3() == 0) ? Double.NaN : (r.yieldT() * 1000.0) / r.waterM3())
                )
        );

        var effEntries = new ArrayList<>(byCropEff.entrySet());
        effEntries.removeIf(e -> e.getValue()==null || !Double.isFinite(e.getValue()) || e.getValue() == 0.0);
        effEntries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        if (!effEntries.isEmpty()) {
            model.addAttribute("bestEffCrop",  effEntries.get(0).getKey());
            model.addAttribute("bestEffValue", effEntries.get(0).getValue());
            model.addAttribute("allCropEfficiencies", effEntries.subList(0, Math.min(effEntries.size(), 3)));
        } else {
            model.addAttribute("bestEffCrop", "Nessun dato");
            model.addAttribute("bestEffValue", 0.0);
            model.addAttribute("allCropEfficiencies", Collections.emptyList());
        }

        // Scala robusta per efficienza
        List<Double> effSeries = filtered.stream()
                .map(r -> (r.waterM3() == 0) ? Double.NaN : (r.yieldT() * 1000.0) / r.waterM3())
                .filter(Double::isFinite)
                .sorted()
                .toList();

        double effMaxScale = 50.0;
        if (!effSeries.isEmpty()) {
            int idx = (int) Math.floor(0.95 * (effSeries.size() - 1));
            double p95 = effSeries.get(idx);
            effMaxScale = Math.max(10.0, Math.ceil((p95 * 1.10) / 5.0) * 5.0);
        }

        // Efficienza media (Kg/mÂ³) per area (per polar)
        Map<String, List<Double>> effListByArea = filtered.stream().collect(
                Collectors.groupingBy(
                        SampleRecord::area,
                        Collectors.mapping(r -> {
                            double v = (r.waterM3() == 0) ? Double.NaN : (r.yieldT() * 1000.0) / r.waterM3();
                            return v;
                        }, Collectors.toList())
                )
        );

        double effNordKgM3   = avgFinite(effListByArea.get("Nord"));
        double effCentroKgM3 = avgFinite(effListByArea.get("Centro"));
        double effSudKgM3    = avgFinite(effListByArea.get("Sud"));

        // Costi/Margini/Rischio per area (â‚¬/t e indice 0..1)
        Map<String, List<Kpi>> kpisByArea = filtered.stream().collect(
                Collectors.groupingBy(
                        SampleRecord::area,
                        Collectors.mapping(kpiService::compute, Collectors.toList())
                )
        );

        double costNord     = avgFiniteKpi(kpisByArea.get("Nord"),   Kpi::unitCostEurPerT);
        double costCentro   = avgFiniteKpi(kpisByArea.get("Centro"), Kpi::unitCostEurPerT);
        double costSud      = avgFiniteKpi(kpisByArea.get("Sud"),    Kpi::unitCostEurPerT);

        double marginNord   = avgFiniteKpi(kpisByArea.get("Nord"),   Kpi::unitMarginEurPerT);
        double marginCentro = avgFiniteKpi(kpisByArea.get("Centro"), Kpi::unitMarginEurPerT);
        double marginSud    = avgFiniteKpi(kpisByArea.get("Sud"),    Kpi::unitMarginEurPerT);

        // >>> NUOVO: rischio medio per area (0..1) <<<
        double riskNord     = avgFiniteKpi(kpisByArea.get("Nord"),   Kpi::climateRiskIdx);
        double riskCentro   = avgFiniteKpi(kpisByArea.get("Centro"), Kpi::climateRiskIdx);
        double riskSud      = avgFiniteKpi(kpisByArea.get("Sud"),    Kpi::climateRiskIdx);

        // ===== Model attributes =====
        model.addAttribute("labels", labels);
        model.addAttribute("yieldsNord",   yieldsNord);
        model.addAttribute("yieldsCentro", yieldsCentro);
        model.addAttribute("yieldsSud",    yieldsSud);

        model.addAttribute("avgYieldHa", avgYieldHa);
        model.addAttribute("avgEff", avgEff);
        model.addAttribute("avgCost", avgCost);
        model.addAttribute("avgMargin", avgMargin);
        model.addAttribute("avgRisk", avgRisk);
        model.addAttribute("effMaxScale", effMaxScale);

        model.addAttribute("effNordKgM3",   effNordKgM3);
        model.addAttribute("effCentroKgM3", effCentroKgM3);
        model.addAttribute("effSudKgM3",    effSudKgM3);

        // per ECharts
        model.addAttribute("costNord", costNord);
        model.addAttribute("costCentro", costCentro);
        model.addAttribute("costSud", costSud);
        model.addAttribute("marginNord", marginNord);
        model.addAttribute("marginCentro", marginCentro);
        model.addAttribute("marginSud", marginSud);

        // >>> NUOVI attributi per i 3 gauge rischio <<<
        model.addAttribute("riskNord", riskNord);
        model.addAttribute("riskCentro", riskCentro);
        model.addAttribute("riskSud", riskSud);

        model.addAttribute("minDate", MIN_DATE);
        model.addAttribute("maxDate", MAX_DATE);

        model.addAttribute("from", fromDate);
        model.addAttribute("to", toDate);
        model.addAttribute("crop", cropNorm);
        model.addAttribute("area", areaNorm);

        return "index";
    }

    private static double avgFinite(Collection<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        return values.stream()
                .filter(Objects::nonNull)
                .filter(Double::isFinite)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private static double avgFiniteKpi(Collection<Kpi> values, java.util.function.ToDoubleFunction<Kpi> f) {
        if (values == null || values.isEmpty()) return 0.0;
        return values.stream()
                .mapToDouble(f)
                .filter(Double::isFinite)
                .average()
                .orElse(0.0);
    }

    private List<Double> seriesForArea(String areaKey,
                                       List<LocalDate> dateRange,
                                       Map<String, Map<LocalDate, Double>> sumByAreaDate) {
        Map<LocalDate, Double> map = sumByAreaDate.getOrDefault(areaKey, Collections.emptyMap());
        List<Double> out = new ArrayList<>(dateRange.size());
        for (LocalDate d : dateRange) {
            out.add(map.getOrDefault(d, 0.0));
        }
        return out;
    }
}