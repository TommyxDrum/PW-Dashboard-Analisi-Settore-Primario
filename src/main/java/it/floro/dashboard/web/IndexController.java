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
 * 1. GRAFICO LINEARE: Resa annuale per area (Nord, Centro, Sud)
 *    - X: anni
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
     * GET /dashboard?area=Nord&periodo=anno&year=2024 → dashboard Nord 2024
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
     * SEZIONE 2: GRAFICO LINEARE - RESA ANNUALE PER AREA
     * - Serie temporale annuale di resa (t/ha) per area
     * - X: anni (estratti dal dataset filtrato)
     * - Y: resa media (t/ha)
     * - Serie: 3 linee (Nord, Centro, Sud)
     * - Utilizzo: analizzare trend pluriennale di resa per area
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

        // ===== SEZIONE 2: GRAFICO LINEARE - RESA ANNUALE PER AREA =====
        // Prepara dati per grafico lineare multi-serie (Chart.js Line Chart)

        // Calcola serie annuale di resa per ogni area
        Map<Integer, Double> resaNord = kpiService.serieResaAnnuale(nord);
        Map<Integer, Double> resaCentro = kpiService.serieResaAnnuale(centro);
        Map<Integer, Double> resaSud = kpiService.serieResaAnnuale(sud);

        // Estrai tutti gli anni presenti nel dataset filtrato
        // (utile se il filtro restringe a un sottoperiodo)
        List<Integer> years = extractYears(filtered);

        // Converti anni in stringhe per labels Chart.js (es. ["2020", "2021", "2022"])
        List<String> labels = years.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());

        // Allinea i valori di resa agli anni
        // Per ogni anno nella lista years, recupera il valore dalla mappa
        // Se l'anno non è presente nella mappa, usa 0.0 (nessun dato per quell'anno)
        // Questo assicura che le tre serie abbiano sempre la stessa lunghezza
        List<Double> yieldsNord = years.stream()
                .map(y -> resaNord.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
        List<Double> yieldsCentro = years.stream()
                .map(y -> resaCentro.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
        List<Double> yieldsSud = years.stream()
                .map(y -> resaSud.getOrDefault(y, 0.0))
                .collect(Collectors.toList());

        // Aggiungi al Model per rendering del grafico
        model.addAttribute("labels", labels);           // X-axis: anni
        model.addAttribute("yieldsNord", yieldsNord);   // Linea Nord
        model.addAttribute("yieldsCentro", yieldsCentro); // Linea Centro
        model.addAttribute("yieldsSud", yieldsSud);     // Linea Sud

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
}