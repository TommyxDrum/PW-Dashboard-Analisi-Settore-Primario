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
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Controller che espone il dashboard del KPI "Costo unitario (€/t)".
 *
 * Responsabilità:
 * - Gestire la richiesta HTTP GET /costi con filtri opzionali
 * - Delega a KpiService il calcolo del costo unitario
 * - Aggregare i dati per visualizzazione (serie temporali, per area, etc.)
 * - Scomporre il costo stimato in componenti (manodopera, materiali)
 * - Popolare il Model Thymeleaf con attributi per la view "costi"
 *
 * Pattern: Estende BaseKpiController per riutilizzare logica comune
 * - Implementa l'hook astratto populateKpiModel
 * - Eredita il template method processKpiRequest
 * - Eredita utility per filtri (filterByArea, extractYears, normalizeString)
 *
 * KPI principale: Costo unitario medio aggregato
 * - Formula: media( costo_totale / produzione )
 * - Unità: euro per tonnellata (€/t)
 * - Interpretazione: quanto costa produrre 1 tonnellata di merce
 *
 * Sottocomponenti visualizzati:
 * - Costo manodopera (60% del totale, stima)
 * - Costo materiali (40% del totale, stima)
 * - Serie temporale giornaliera
 * - Serie temporale annuale per area
 *
 * View renderizzata: "costi" (templates/costi.html)
 */
@Controller
public class CostiController extends BaseKpiController {

    // ========================================================================
    // COSTANTI DI CONFIGURAZIONE
    // ========================================================================

    /**
     * Rapporto stimato di manodopera sul costo totale.
     *
     * Assunzione: il 60% del costo unitario è dovuto a:
     * - Manodopera diretta (raccolta, potatura, manutenzione)
     * - Contributi sociali
     * - Supervisione e coordinamento
     *
     * Nota: È una stima semplificata. In realtà, il dataset SampleRecord
     * contiene scomposizioni più dettagliate (fixedCost, inputsCost, waterCost),
     * ma per la view utilizziamo questa approssimazione per chiarezza.
     */
    private static final double LABOR_RATIO = 0.60;

    /**
     * Rapporto stimato di materiali sul costo totale.
     *
     * Assunzione: il 40% del costo unitario è dovuto a:
     * - Sementi/propaguli
     * - Fertilizzanti
     * - Pesticidi/fungicidi
     * - Carburante per meccanizzazione
     * - Ammortamenti attrezzature
     *
     * Nota: LABOR_RATIO + MATERIALS_RATIO = 1.0
     */
    private static final double MATERIALS_RATIO = 0.40;

    // ========================================================================
    // COSTRUTTORE
    // ========================================================================

    /**
     * Costruttore con dependency injection.
     * Delegato a BaseKpiController via super().
     *
     * @param sampleDataService Service per accesso dati
     * @param kpiFilters Service per filtri e normalizzzazione
     * @param kpiService Service per calcolo KPI
     */
    public CostiController(SampleDataService sampleDataService,
                           KpiFilters kpiFilters,
                           KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    // ========================================================================
    // ENDPOINT HTTP
    // ========================================================================

    /**
     * Endpoint GET /costi: visualizza il dashboard del costo unitario.
     *
     * Metodo HTTP: GET
     * Mapping: /costi
     * Risposta: HTML renderizzato (Thymeleaf template "costi.html")
     *
     * Parametri query (tutti opzionali):
     * - area: filtro area geografica (es. "Nord", "Centro", "Sud")
     * - crop: filtro coltura (es. "Grano duro", "Mais")
     * - startDate: data inizio intervallo (formato ISO: YYYY-MM-DD)
     * - endDate: data fine intervallo (formato ISO: YYYY-MM-DD)
     * - periodo: periodo aggregazione ("giorno", "mese", "trimestre", "anno", "custom")
     * - year: anno per aggregazione annuale (es. 2024)
     * - month: mese per aggregazione mensile (es. 5)
     * - quarter: trimestre per aggregazione trimestrale (es. 2)
     *
     * Esempio URL:
     * GET /costi?area=Sud&crop=Vite&periodo=anno&year=2024
     *
     * Flusso:
     * 1. Delega a BaseKpiController.processKpiRequest()
     * 2. processKpiRequest esegue il template method:
     *    - Recupera dataset
     *    - Applica filtri
     *    - Chiama populateKpiModel() (implementato in questa classe)
     *    - Popola attributi comuni
     * 3. Ritorna il nome della view "costi" da renderizzare
     *
     * @param area Area geografica (opzionale)
     * @param crop Coltura (opzionale)
     * @param startDate Data inizio (opzionale, deserializzato da ISO.DATE)
     * @param endDate Data fine (opzionale, deserializzato da ISO.DATE)
     * @param periodo Periodo aggregazione (opzionale)
     * @param year Anno (opzionale)
     * @param month Mese (opzionale)
     * @param quarter Trimestre (opzionale)
     * @param model Spring Model per aggiungere attributi view
     * @return Nome della view Thymeleaf ("costi")
     */
    @GetMapping("/costi")
    public String costoUnitarioKpi(
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
                year, month, quarter, model, "costi");
    }

    // ========================================================================
    // HOOK ASTRATTO - IMPLEMENTAZIONE
    // ========================================================================

    /**
     * Implementazione dell'hook astratto di BaseKpiController.
     *
     * Responsabilità:
     * - Calcolare KPI specifici del costo unitario
     * - Aggregare dati per diverse granularità temporali e geografiche
     * - Popolare il Model con attributi per la view Thymeleaf
     *
     * Struttura della popopolazione (7 sezioni):
     *
     * SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato)
     * - costoUnitMedio: media dei costi per tonnellata
     * - costoUnitGiorno: serie temporale giornaliera
     *
     * SEZIONE 2: SCOMPOSIZIONE TOTALE (Stima)
     * - Costo totale medio
     * - Costo manodopera (60% stima)
     * - Costo materiali (40% stima)
     *
     * SEZIONE 3: SERIE ANNUALI PER AREA
     * - Costo unitario annuale per Nord, Centro, Sud
     * - Utilizzato per grafici trend multi-anno
     *
     * SEZIONE 4: KPI PER AREA (Periodo filtrato)
     * - Costo unitario medio per ogni area
     * - Scomposizione manodopera/materiali per area
     * - Utilizzato per bar chart confronto aree
     *
     * SEZIONE 5: TABELLA DETTAGLI
     * - Righe CostiRow con dati aggregati per area
     * - Visualizzazione tabellare nel dashboard
     *
     * @param filtered Lista di record post-filtri (area, crop, date)
     * @param model Spring Model dove aggiungere attributi
     */
    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {

        // ===== SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato) =====

        // Costo unitario medio aggregato su tutti i record filtrati
        double costoUnitMedio = kpiService.calcolaCostoUnitario(filtered);

        // Serie temporale giornaliera: data → costo unitario medio giornaliero
        Map<LocalDate, Double> costoUnitGiorno = kpiService.serieCostoUnitarioGiornaliera(filtered);

        model.addAttribute("costoUnitMedio", costoUnitMedio);
        model.addAttribute("costoUnitGiorno", costoUnitGiorno);

        // ===== SEZIONE 2: SCOMPOSIZIONE TOTALE (Stima) =====
        // Applica ratios di scomposizione (60% manodopera, 40% materiali)

        double totalAvgCost = costoUnitMedio;
        double totalAvgCostLabor = totalAvgCost * LABOR_RATIO;
        double totalAvgCostMaterials = totalAvgCost * MATERIALS_RATIO;

        model.addAttribute("totalAvgCost", totalAvgCost);
        model.addAttribute("totalAvgCostLabor", totalAvgCostLabor);
        model.addAttribute("totalAvgCostMaterials", totalAvgCostMaterials);

        // ===== SEZIONE 3: SERIE ANNUALI PER AREA =====
        // Recupera il dataset completo per aggregazioni multi-anno

        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        // Filtra dataset completo per ogni area (senza altre restrizioni)
        // In questo modo calcoliamo il costo per ogni anno per ogni area
        List<SampleRecord> nordAll = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll = filterByArea(all, "Sud");

        // Calcola serie annuali di costo per ogni area
        Map<Integer, Double> annualCostNord = kpiService.serieCostoUnitarioAnnuale(nordAll);
        Map<Integer, Double> annualCostCentro = kpiService.serieCostoUnitarioAnnuale(centroAll);
        Map<Integer, Double> annualCostSud = kpiService.serieCostoUnitarioAnnuale(sudAll);

        // Converte le mappe in liste ordinate per facilità di utilizzo in Thymeleaf
        model.addAttribute("years", years);
        model.addAttribute("annualCostNord", toOrderedList(annualCostNord, years));
        model.addAttribute("annualCostCentro", toOrderedList(annualCostCentro, years));
        model.addAttribute("annualCostSud", toOrderedList(annualCostSud, years));

        // ===== SEZIONE 4: KPI PER AREA (Periodo filtrato) =====
        // Applica gli stessi filtri (area, crop, date) ma disaggregati per area geografica

        List<SampleRecord> nordFiltered = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered = filterByArea(filtered, "Sud");

        // Costo unitario medio per area (sul periodo filtrato)
        double costNord = kpiService.calcolaCostoUnitario(nordFiltered);
        double costCentro = kpiService.calcolaCostoUnitario(centroFiltered);
        double costSud = kpiService.calcolaCostoUnitario(sudFiltered);

        // Scomposizione stimata (60% manodopera, 40% materiali) per area
        double avgCostLaborNord = costNord * LABOR_RATIO;
        double avgCostLaborCentro = costCentro * LABOR_RATIO;
        double avgCostLaborSud = costSud * LABOR_RATIO;

        double avgCostMaterialsNord = costNord * MATERIALS_RATIO;
        double avgCostMaterialsCentro = costCentro * MATERIALS_RATIO;
        double avgCostMaterialsSud = costSud * MATERIALS_RATIO;

        model.addAttribute("avgCostLaborNord", avgCostLaborNord);
        model.addAttribute("avgCostLaborCentro", avgCostLaborCentro);
        model.addAttribute("avgCostLaborSud", avgCostLaborSud);
        model.addAttribute("avgCostMaterialsNord", avgCostMaterialsNord);
        model.addAttribute("avgCostMaterialsCentro", avgCostMaterialsCentro);
        model.addAttribute("avgCostMaterialsSud", avgCostMaterialsSud);

        // ===== SEZIONE 5: TABELLA DETTAGLI =====
        // Aggrega i dati per area in una lista di CostiRow per visualizzazione tabellare

        List<CostiRow> costiRows = Arrays.asList(
                new CostiRow("Nord", costNord, avgCostLaborNord, avgCostMaterialsNord),
                new CostiRow("Centro", costCentro, avgCostLaborCentro, avgCostMaterialsCentro),
                new CostiRow("Sud", costSud, avgCostLaborSud, avgCostMaterialsSud)
        );
        model.addAttribute("costiRows", costiRows);
    }

    // ========================================================================
    // METODI HELPER PRIVATI
    // ========================================================================

    /**
     * Converte una mappa (anno → valore) in una lista ordinata per anni.
     *
     * Utilizzo: trasformare il risultato di serieCostoUnitarioAnnuale()
     * in un formato facilmente iterabile in Thymeleaf.
     *
     * Algoritmo:
     * 1. Itera su ogni anno della lista "years" in ordine
     * 2. Per ogni anno, recupera il valore dalla mappa
     * 3. Se l'anno non è presente nella mappa, usa 0.0 come default
     * 4. Ritorna una lista ordinata per anno
     *
     * Esempio:
     * - map = {2020: 250, 2022: 270}
     * - years = [2020, 2021, 2022]
     * - output = [250, 0.0, 270]
     *
     * Vantaggio: il risultato ha sempre la stessa lunghezza di "years",
     * e il valore alla posizione i corrisponde a years.get(i).
     * Questo facilita l'allineamento con assi X in grafici Chart.js.
     *
     * @param map Mappa anno → valore (es. output di serieCostoUnitarioAnnuale)
     * @param years Lista di anni in ordine crescente
     * @return Lista di valori ordinati per anno (0.0 per anni mancanti)
     */
    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // INNER CLASS: CostiRow
    // ========================================================================

    /**
     * Record di dati per una riga della tabella di costi per area.
     *
     * Responsabilità:
     * - Contenere i dati aggregati di costo per una singola area
     * - Fornire getter accessibili da Thymeleaf
     * - Facilitare rendering tabellare nel dashboard
     *
     * Utilizzo in Thymeleaf:
     * ```html
     * <table>
     *   <tr>
     *     <th>Area</th>
     *     <th>Costo Totale (€/t)</th>
     *     <th>Manodopera (€/t)</th>
     *     <th>Materiali (€/t)</th>
     *   </tr>
     *   <tr th:each="row : ${costiRows}">
     *     <td th:text="${row.area}"></td>
     *     <td th:text="${#numbers.formatDecimal(row.totalCost, 0, 2)}"></td>
     *     <td th:text="${#numbers.formatDecimal(row.laborCost, 0, 2)}"></td>
     *     <td th:text="${#numbers.formatDecimal(row.materialsCost, 0, 2)}"></td>
     *   </tr>
     * </table>
     * ```
     */
    public static class CostiRow {

        /**
         * Area geografica ("Nord", "Centro", "Sud").
         */
        private final String area;

        /**
         * Costo unitario totale medio per l'area in euro per tonnellata.
         */
        private final double totalCost;

        /**
         * Costo manodopera stimato (60% del totale) in euro per tonnellata.
         */
        private final double laborCost;

        /**
         * Costo materiali stimato (40% del totale) in euro per tonnellata.
         */
        private final double materialsCost;

        /**
         * Costruttore per creare una riga di dati di costo.
         *
         * @param area Area geografica
         * @param totalCost Costo totale medio (€/t)
         * @param laborCost Costo manodopera (€/t)
         * @param materialsCost Costo materiali (€/t)
         */
        public CostiRow(String area, double totalCost, double laborCost, double materialsCost) {
            this.area = area;
            this.totalCost = totalCost;
            this.laborCost = laborCost;
            this.materialsCost = materialsCost;
        }

        /**
         * Getter: area geografica.
         */
        public String getArea() {
            return area;
        }

        /**
         * Getter: costo totale unitario.
         * Utilizzato in template Thymeleaf: ${row.totalCost}
         */
        public double getTotalCost() {
            return totalCost;
        }

        /**
         * Getter: costo manodopera.
         * Utilizzato in template Thymeleaf: ${row.laborCost}
         */
        public double getLaborCost() {
            return laborCost;
        }

        /**
         * Getter: costo materiali.
         * Utilizzato in template Thymeleaf: ${row.materialsCost}
         */
        public double getMaterialsCost() {
            return materialsCost;
        }
    }
}