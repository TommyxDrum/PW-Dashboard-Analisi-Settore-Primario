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
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Controller principale della Dashboard (landing page).
 *
 * Responsabilità:
 * - Esporre il punto di ingresso principale dell'applicazione (/ e /dashboard)
 * - Calcolare i 5 KPI principali in forma aggregata
 * - Visualizzare una panoramica multi-KPI con grafici sintetici
 * - Supportare i medesimi filtri del dashboard (area, crop, periodo)
 * - Fornire view "index" (templates/index.html) con 5 grafici diversi
 *
 * Pattern: Estende BaseKpiController
 * - Implementa l'hook astratto populateKpiModel
 * - Eredita template method processKpiRequest
 * - Eredita utility per filtri (filterByArea, extractYears)
 *
 * KPI visualizzati (card sintetiche in alto):
 * 1. Resa Media: tonnellate per ettaro (t/ha)
 * 2. Efficienza Idrica: chilogrammi per metro cubo (kg/m³)
 * 3. Costo Unitario: euro per tonnellata (€/t)
 * 4. Margine Unitario: euro per tonnellata (€/t)
 * 5. Rischio Climatico: indice [0..1]
 *
 * Grafici visualizzati (sotto le card):
 * 1. GRAFICO LINEARE: Resa giornaliera per area nel mese selezionato (MODIFICATO DA ANNUALE A MENSILE)
 *    - X: giorni del mese (1-31)
 *    - Y: resa media (t/ha)
 *    - Serie: 3 linee (Nord, Centro, Sud)
 *
 * 2. GRAFICO POLARE: Efficienza idrica per area
 *    - Visualizza 3 raggi (Nord, Centro, Sud)
 *    - Utile per comparazione visiva aree
 *
 * 3. GRAFICO BARRE: Costo unitario per area
 *    - X: area (Nord, Centro, Sud)
 *    - Y: costo medio (€/t)
 *
 * 4. GRAFICO BARRE: Margine unitario per area
 *    - X: area (Nord, Centro, Sud)
 *    - Y: margine medio (€/t)
 *
 * 5. GAUGE: Rischio climatico per area
 *    - Tre gauge (speedometer) che mostrano rischio [0..1]
 *
 * View renderizzata: "index" (templates/index.html)
 * - Template Thymeleaf che integra i 5 grafici
 * - Utilizza Chart.js per rendering
 */
@Controller
public class IndexController extends BaseKpiController {

    // ========================================================================
    // COSTRUTTORE
    // ========================================================================

    /**
     * Costruttore con dependency injection.
     * Delegato a BaseKpiController via super().
     *
     * @param sampleDataService Service per accesso dati
     * @param kpiFilters Service per filtri
     * @param kpiService Service per calcolo KPI
     */
    public IndexController(SampleDataService sampleDataService,
                           KpiFilters kpiFilters,
                           KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    // ========================================================================
    // ENDPOINT HTTP - DASHBOARD PRINCIPALE
    // ========================================================================

    /**
     * Endpoint GET / e /dashboard: visualizza la dashboard principale.
     *
     * Metodo HTTP: GET
     * Mapping: / (root) e /dashboard (alias)
     * Risposta: HTML renderizzato (Thymeleaf template "index.html")
     *
     * Parametri query (tutti opzionali, identici al dashboard KPI):
     * - area: area geografica (es. "Nord", "Centro", "Sud")
     * - crop: coltura (es. "Grano duro", "Mais")
     * - startDate: data inizio intervallo (ISO: YYYY-MM-DD)
     * - endDate: data fine intervallo (ISO: YYYY-MM-DD)
     * - periodo: periodo aggregazione ("giorno", "mese", "trimestre", "anno", "custom")
     * - year: anno (es. 2024)
     * - month: mese (es. 5)
     * - quarter: trimestre (es. 2)
     *
     * Esempio URL:
     * GET / → dashboard con tutti i dati
     * GET /dashboard?area=Nord&periodo=mese&year=2024&month=5 → dashboard Nord maggio 2024
     * GET /?crop=Vite → dashboard solo Vite, tutte le aree e periodi
     *
     * Flusso:
     * 1. Delega a BaseKpiController.processKpiRequest()
     * 2. processKpiRequest esegue il template method:
     *    - Recupera dataset
     *    - Applica filtri (area, crop, date)
     *    - Chiama populateKpiModel() (implementato in questa classe)
     *    - Popola attributi comuni
     * 3. Ritorna il nome della view "index" da renderizzare
     *
     * @param area Area geografica (opzionale)
     * @param crop Coltura (opzionale)
     * @param startDate Data inizio (opzionale)
     * @param endDate Data fine (opzionale)
     * @param periodo Periodo aggregazione (opzionale)
     * @param year Anno (opzionale)
     * @param month Mese (opzionale)
     * @param quarter Trimestre (opzionale)
     * @param model Spring Model per aggiungere attributi view
     * @return Nome della view Thymeleaf ("index")
     */
    @GetMapping({"/index"})
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

    // ========================================================================
    // HOOK ASTRATTO - IMPLEMENTAZIONE
    // ========================================================================

    /**
     * Implementazione dell'hook astratto di BaseKpiController.
     *
     * Responsabilità:
     * - Calcolare i 5 KPI principali aggregati
     * - Preparare dati per 5 grafici diversi
     * - Disaggregare per area geografica
     * - Popolare il Model con attributi per la view Thymeleaf
     *
     * Struttura della popolazione (6 sezioni):
     *
     * SEZIONE 1: KPI SINTETICI AGGREGATI
     * - Resa media, efficienza idrica, costo, margine, rischio climatico
     * - Visualizzati come card numeriche in alto
     *
     * SEZIONE 2: GRAFICO LINEARE - RESA GIORNALIERA PER AREA NEL MESE SELEZIONATO [MODIFICATO]
     * - Serie temporale giornaliera di resa (t/ha) per area nel mese specificato
     * - X: giorni del mese (1-31, solo giorni con dati)
     * - Y: resa media (t/ha)
     * - Serie: 3 linee (Nord, Centro, Sud)
     * - Utilizzo: analizzare trend giornaliero di resa per area nel mese selezionato
     * - Nota: Se non è specificato mese/anno, usa il mese corrente
     *
     * SEZIONE 3: GRAFICO POLARE - EFFICIENZA IDRICA PER AREA
     * - Visualizzazione radar/spider plot dell'efficienza
     * - 3 raggi: Nord, Centro, Sud
     * - Scala: dinamica basata su max(efficienze) × 1.2
     * - Utilizzo: comparazione visiva veloce dell'efficienza tra aree
     *
     * SEZIONE 4: GRAFICO BARRE - COSTO UNITARIO PER AREA
     * - Valori medi di costo (€/t) per area
     * - X: area (Nord, Centro, Sud)
     * - Y: costo medio (€/t)
     * - Utilizzo: identificare aree con costi più alti
     *
     * SEZIONE 5: GRAFICO BARRE - MARGINE UNITARIO PER AREA
     * - Valori medi di margine (€/t) per area
     * - X: area (Nord, Centro, Sud)
     * - Y: margine medio (€/t)
     * - Interpretazione: margine positivo = profitto, negativo = perdita
     * - Utilizzo: identificare aree più/meno redditizie
     *
     * SEZIONE 6: GAUGE - RISCHIO CLIMATICO PER AREA
     * - Tre speedometer/gauge che mostrano rischio [0..1] per area
     * - Nord, Centro, Sud
     * - Scala: [0..1] con colori (verde = basso, rosso = alto)
     * - Utilizzo: alert visivo su rischi meteorologici per area
     *
     * @param filtered Lista di record post-filtri (area, crop, date)
     * @param model Spring Model dove aggiungere attributi
     */
    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {

        // ===== SEZIONE 1: KPI SINTETICI AGGREGATI (Card numeriche in alto) =====
        // Calcola i 5 KPI principali su tutto il dataset filtrato

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

        // ===== PREPARAZIONE COMUNE: FILTRO PER AREA =====
        // Disaggrega il dataset filtrato per area per tutti i grafici

        List<SampleRecord> nord = filterByArea(filtered, "Nord");
        List<SampleRecord> centro = filterByArea(filtered, "Centro");
        List<SampleRecord> sud = filterByArea(filtered, "Sud");

        // ===== SEZIONE 2: GRAFICO LINEARE - RESA GIORNALIERA PER AREA NEL MESE SELEZIONATO [MODIFICATO] =====
        // Prepara dati per grafico lineare multi-serie (Chart.js Line Chart)
        // MODIFICATO: ora usa serie giornaliera filtrata per mese anziché serie annuale

        // Determina il mese/anno per il filtro
        // Se non specificati, usa il mese corrente
        LocalDate today = LocalDate.now();
        int tempYear = today.getYear();
        int tempMonth = today.getMonthValue();

        // Estrai i giorni del dataset filtrato
        List<LocalDate> daysInFiltered = filtered.stream()
                .map(SampleRecord::date)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // Se ci sono dati filtrati, usa il mese/anno del primo dato disponibile
        if (!daysInFiltered.isEmpty()) {
            LocalDate firstDate = daysInFiltered.get(0);
            tempYear = firstDate.getYear();
            tempMonth = firstDate.getMonthValue();
        }

        // Dichiara come final per uso in lambda expression
        final int selectedYear = tempYear;
        final int selectedMonth = tempMonth;

        // Crea il YearMonth per filtrare
        YearMonth selectedYearMonth = YearMonth.of(selectedYear, selectedMonth);

        // Calcola serie GIORNALIERA di resa per ogni area
        Map<LocalDate, Double> resaGiornNord = kpiService.serieResaGiornaliera(nord);
        Map<LocalDate, Double> resaGiornCentro = kpiService.serieResaGiornaliera(centro);
        Map<LocalDate, Double> resaGiornSud = kpiService.serieResaGiornaliera(sud);

        // Filtra solo il mese selezionato per ogni area
        Map<LocalDate, Double> resaMeseNord = filterByYearMonth(resaGiornNord, selectedYearMonth);
        Map<LocalDate, Double> resaMeseCentro = filterByYearMonth(resaGiornCentro, selectedYearMonth);
        Map<LocalDate, Double> resaMeseSud = filterByYearMonth(resaGiornSud, selectedYearMonth);

        // Estrai tutti i giorni presenti nei dati del mese (union di tutte e tre le aree)
        Set<Integer> giorniSet = new TreeSet<>();
        resaMeseNord.keySet().forEach(d -> giorniSet.add(d.getDayOfMonth()));
        resaMeseCentro.keySet().forEach(d -> giorniSet.add(d.getDayOfMonth()));
        resaMeseSud.keySet().forEach(d -> giorniSet.add(d.getDayOfMonth()));

        // Se il mese non ha dati, aggiungi almeno il primo giorno per evitare grafico vuoto
        if (giorniSet.isEmpty()) {
            giorniSet.add(1);
        }

        // Converti giorni in stringhe per labels Chart.js (es. ["1", "2", "3", ...])
        List<String> labels = giorniSet.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());

        // Allinea i valori di resa ai giorni del mese
        // Per ogni giorno nella lista giorniSet, recupera il valore dalla mappa
        // Se il giorno non è presente nella mappa, usa 0.0 (nessun dato per quel giorno)
        List<Double> yieldsNord = giorniSet.stream()
                .map(g -> {
                    LocalDate d = LocalDate.of(selectedYear, selectedMonth, g);
                    return resaMeseNord.getOrDefault(d, 0.0);
                })
                .collect(Collectors.toList());

        List<Double> yieldsCentro = giorniSet.stream()
                .map(g -> {
                    LocalDate d = LocalDate.of(selectedYear, selectedMonth, g);
                    return resaMeseCentro.getOrDefault(d, 0.0);
                })
                .collect(Collectors.toList());

        List<Double> yieldsSud = giorniSet.stream()
                .map(g -> {
                    LocalDate d = LocalDate.of(selectedYear, selectedMonth, g);
                    return resaMeseSud.getOrDefault(d, 0.0);
                })
                .collect(Collectors.toList());

        // Aggiungi al Model per rendering del grafico
        model.addAttribute("labels", labels);           // X-axis: giorni del mese
        model.addAttribute("yieldsNord", yieldsNord);   // Linea Nord
        model.addAttribute("yieldsCentro", yieldsCentro); // Linea Centro
        model.addAttribute("yieldsSud", yieldsSud);     // Linea Sud

        // Aggiungi areaLabels per il grafico Efficienza Idrica (legenda aree)
        model.addAttribute("areaLabels", Arrays.asList("Nord", "Centro", "Sud"));

        // Aggiungi info sul mese visualizzato per la UI
        model.addAttribute("selectedMonthYear", selectedYearMonth);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("selectedYear", selectedYear);

        // ===== SEZIONE 3: GRAFICO POLARE - EFFICIENZA IDRICA PER AREA =====
        // Prepara dati per grafico radar/polar (Chart.js Radar Chart)

        // Calcola efficienza media per area
        double effNord = kpiService.calcolaEfficienzaIdrica(nord);
        double effCentro = kpiService.calcolaEfficienzaIdrica(centro);
        double effSud = kpiService.calcolaEfficienzaIdrica(sud);

        // Aggiungi valori al Model
        model.addAttribute("effNordKgM3", effNord);
        model.addAttribute("effCentroKgM3", effCentro);
        model.addAttribute("effSudKgM3", effSud);

        // Calcola scala massima per il grafico polare
        // Utile per normalizzare i raggi del radar e renderlo comparabile
        // Formula: max(efficienze) × 1.2 con minimo 100
        double effMaxScale = Math.max(Math.max(effNord, effCentro), Math.max(effSud, 100.0)) * 1.2;
        model.addAttribute("effMaxScale", effMaxScale);

        // ===== SEZIONE 4: GRAFICO BARRE - COSTO UNITARIO PER AREA =====
        // Prepara dati per grafico a barre (Chart.js Bar Chart)

        // Calcola costo medio per area
        double costiNord = kpiService.calcolaCostoUnitario(nord);
        double costiCentro = kpiService.calcolaCostoUnitario(centro);
        double costiSud = kpiService.calcolaCostoUnitario(sud);

        // Aggiungi valori al Model
        model.addAttribute("costNord", costiNord);
        model.addAttribute("costCentro", costiCentro);
        model.addAttribute("costSud", costiSud);

        // ===== SEZIONE 5: GRAFICO BARRE - MARGINE UNITARIO PER AREA =====
        // Prepara dati per grafico a barre (Chart.js Bar Chart)

        // Calcola margine medio per area
        double margNord = kpiService.calcolaMargineUnitario(nord);
        double margCentro = kpiService.calcolaMargineUnitario(centro);
        double margSud = kpiService.calcolaMargineUnitario(sud);

        // Aggiungi valori al Model
        model.addAttribute("marginNord", margNord);
        model.addAttribute("marginCentro", margCentro);
        model.addAttribute("marginSud", margSud);

        // ===== SEZIONE 6: GAUGE - RISCHIO CLIMATICO PER AREA =====
        // Prepara dati per gauge/speedometer (Chart.js Gauge Chart)

        // Calcola rischio climatico medio per area
        // Scala: [0..1] dove 0 = nessun rischio, 1 = rischio massimo
        double riskNord = kpiService.calcolaRischioClimatico(nord);
        double riskCentro = kpiService.calcolaRischioClimatico(centro);
        double riskSud = kpiService.calcolaRischioClimatico(sud);

        // Aggiungi valori al Model per rendering gauge
        model.addAttribute("riskNord", riskNord);
        model.addAttribute("riskCentro", riskCentro);
        model.addAttribute("riskSud", riskSud);
    }

    // ========================================================================
    // METODI HELPER PRIVATI - NUOVI PER FILTRO MENSILE
    // ========================================================================

    /**
     * Filtra una mappa di serie temporale per includere solo un mese/anno specifico.
     *
     * @param timeSeries Mappa <LocalDate, Double> con serie temporale giornaliera
     * @param yearMonth YearMonth per filtrare (es. 2024-05)
     * @return Mappa filtrata con solo i giorni del mese specificato
     */
    private Map<LocalDate, Double> filterByYearMonth(Map<LocalDate, Double> timeSeries, YearMonth yearMonth) {
        return timeSeries.entrySet().stream()
                .filter(e -> YearMonth.from(e.getKey()).equals(yearMonth))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,  // merge function (non dovrebbe capitare)
                        TreeMap::new   // mantiene ordinamento per data
                ));
    }
}
