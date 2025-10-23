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
 * Controller che espone il dashboard del KPI "Margine unitario (€/t)".
 *
 * Responsabilità:
 * - Gestire la richiesta HTTP GET /margine con filtri opzionali
 * - Delega a KpiService il calcolo del margine unitario
 * - Aggregare dati per visualizzazione (serie temporali, per area, etc.)
 * - Analizzare redditività: correlazione tra prezzo, costo e margine
 * - Popolare il Model Thymeleaf con attributi per la view "margine"
 *
 * Pattern: Estende BaseKpiController per riutilizzare logica comune
 * - Implementa l'hook astratto populateKpiModel
 * - Eredita il template method processKpiRequest
 * - Eredita utility per filtri (filterByArea, extractYears)
 *
 * KPI principale: Margine unitario medio
 * - Formula: prezzo di vendita - costo di produzione (per tonnellata)
 * - Unità: euro per tonnellata (€/t)
 * - Interpretazione:
 *   • Positivo: profitto (ricavo supera il costo)
 *   • Negativo: perdita (costo supera il ricavo)
 *   • Zero: break-even (profitto nullo)
 *
 * KPI di redditività: il margine è il KPI più importante dal punto di vista economico
 * - Rispecchia la salute finanziaria dell'azienda agricola
 * - Dipende da due fattori controllabili: prezzo e costo
 * - Variazione di prezzo: mercato (non controllabile) + qualità (controllabile)
 * - Variazione di costo: efficienza produttiva (controllabile)
 *
 * Sottocomponenti visualizzati:
 * - Margine medio aggregato (€/t)
 * - Serie temporale giornaliera del margine
 * - Totali: prezzo medio, costo medio, margine medio
 * - Serie temporale annuale per area
 * - Disaggregazione per area: prezzo, costo, margine per Nord/Centro/Sud
 *
 * View renderizzata: "margine" (templates/margine.html)
 */
@Controller
public class MargineController extends BaseKpiController {

    // ========================================================================
    // COSTRUTTORE
    // ========================================================================

    /**
     * Costruttore con dependency injection.
     * Delegato a BaseKpiController via super().
     *
     * @param sampleDataService Service per accesso dati
     * @param kpiFilters Service per filtri e normalizzazione
     * @param kpiService Service per calcolo KPI
     */
    public MargineController(SampleDataService sampleDataService,
                             KpiFilters kpiFilters,
                             KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    // ========================================================================
    // ENDPOINT HTTP
    // ========================================================================

    /**
     * Endpoint GET /margine: visualizza il dashboard del margine unitario.
     *
     * Metodo HTTP: GET
     * Mapping: /margine
     * Risposta: HTML renderizzato (Thymeleaf template "margine.html")
     *
     * Parametri query (tutti opzionali):
     * - area: filtro area geografica (es. "Nord", "Centro", "Sud")
     * - crop: filtro coltura (es. "Grano duro", "Mais", "Olivo", "Vite")
     * - startDate: data inizio intervallo (formato ISO: YYYY-MM-DD)
     * - endDate: data fine intervallo (formato ISO: YYYY-MM-DD)
     * - periodo: periodo aggregazione ("giorno", "mese", "trimestre", "anno", "custom")
     * - year: anno per aggregazione annuale (es. 2024)
     * - month: mese per aggregazione mensile (es. 5)
     * - quarter: trimestre per aggregazione trimestrale (es. 2)
     *
     * Esempio URL:
     * GET /margine → dashboard con margini di tutto il dataset
     * GET /margine?area=Nord&crop=Grano%20duro&periodo=anno&year=2024 → margini Nord + Grano duro 2024
     * GET /margine?periodo=mese&year=2024&month=6 → margini giugno 2024
     *
     * Flusso:
     * 1. Delega a BaseKpiController.processKpiRequest()
     * 2. processKpiRequest esegue il template method:
     *    - Recupera dataset
     *    - Applica filtri
     *    - Chiama populateKpiModel() (implementato in questa classe)
     *    - Popola attributi comuni
     * 3. Ritorna il nome della view "margine"
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
     * @return Nome della view Thymeleaf ("margine")
     */
    @GetMapping("/margine")
    public String margineKpi(
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
                year, month, quarter, model, "margine");
    }

    // ========================================================================
    // HOOK ASTRATTO - IMPLEMENTAZIONE
    // ========================================================================

    /**
     * Implementazione dell'hook astratto di BaseKpiController.
     *
     * Responsabilità:
     * - Calcolare il margine unitario in diverse forme (aggregato, per area, serie temporali)
     * - Correlate il margine con prezzo e costo
     * - Popolare il Model con attributi per la view Thymeleaf
     *
     * Struttura della popolazione (5 sezioni):
     *
     * SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato)
     * - margineUnitMedio: media dei margini unitari (€/t)
     * - margineUnitGiorno: serie temporale giornaliera (data → margine €/t)
     *
     * SEZIONE 2: TOTALI (Prezzo, costo, margine aggregati)
     * - prezzoMedio: prezzo medio di vendita (€/t)
     * - costoUnitario: costo medio di produzione (€/t)
     * - margineUnitario: differenza (prezzo - costo) (€/t)
     * - Utilizzati per visualizzare la "ricetta" che genera il margine
     *
     * SEZIONE 3: SERIE ANNUALI PER AREA
     * - Margine unitario annuale per Nord, Centro, Sud
     * - Utilizzato per grafici trend multi-anno
     *
     * SEZIONE 4: KPI PER AREA (Periodo filtrato)
     * - Margine unitario medio per area
     * - Costo unitario medio per area (componente negativa del margine)
     * - Utilizzati per bar chart comparazione aree
     *
     * SEZIONE 5: TABELLA DETTAGLI
     * - Righe MargineRow con prezzo, costo, margine per area
     * - Visualizzazione tabellare nel dashboard
     * - Consente di vedere come il margine è composto
     *
     * @param filtered Lista di record post-filtri (area, crop, date)
     * @param model Spring Model dove aggiungere attributi
     */
    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {

        // ===== SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato) =====

        // Margine unitario medio: media della differenza (prezzo - costo) per record
        double margineUnitMedio = kpiService.calcolaMargineUnitario(filtered);

        // Serie temporale giornaliera del margine: data → margine medio giornaliero
        Map<LocalDate, Double> margineUnitGiorno = kpiService.serieMargineUnitarioGiornaliera(filtered);

        model.addAttribute("margineUnitMedio", margineUnitMedio);
        model.addAttribute("margineUnitGiorno", margineUnitGiorno);

        // ===== SEZIONE 2: TOTALI (Prezzo, costo, margine aggregati) =====
        // Mostra i tre componenti che formano il margine

        // Prezzo medio di vendita (€/t): ciò che l'agricoltore riceve
        double totalAvgPrice = kpiService.prezzoMedio(filtered);

        // Costo unitario medio (€/t): ciò che l'agricoltore spende
        double totalAvgCost = kpiService.calcolaCostoUnitario(filtered);

        // Margine medio: differenza (ricavo - costo)
        // Rappresenta il profitto lordo per tonnellata di prodotto
        double totalAvgMargin = margineUnitMedio;

        model.addAttribute("totalAvgPrice", totalAvgPrice);
        model.addAttribute("totalAvgCost", totalAvgCost);
        model.addAttribute("totalAvgMargin", totalAvgMargin);

        // ===== SEZIONE 3: SERIE ANNUALI PER AREA =====
        // Recupera il dataset completo per aggregazioni multi-anno

        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        // Filtra dataset completo per ogni area
        List<SampleRecord> nordAll = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll = filterByArea(all, "Sud");

        // Calcola serie annuali di margine per ogni area
        Map<Integer, Double> annualMarginNord = kpiService.serieMargineUnitarioAnnuale(nordAll);
        Map<Integer, Double> annualMarginCentro = kpiService.serieMargineUnitarioAnnuale(centroAll);
        Map<Integer, Double> annualMarginSud = kpiService.serieMargineUnitarioAnnuale(sudAll);

        // Converte mappe in liste ordinate per facilità in Thymeleaf
        model.addAttribute("years", years);
        model.addAttribute("annualMarginNord", toOrderedList(annualMarginNord, years));
        model.addAttribute("annualMarginCentro", toOrderedList(annualMarginCentro, years));
        model.addAttribute("annualMarginSud", toOrderedList(annualMarginSud, years));

        // ===== SEZIONE 4: KPI PER AREA (Periodo filtrato) =====
        // Applica gli stessi filtri (area, crop, date) ma disaggregati per area geografica

        List<SampleRecord> nordFiltered = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered = filterByArea(filtered, "Sud");

        // Margine unitario medio per area (€/t)
        double marginNord = kpiService.calcolaMargineUnitario(nordFiltered);
        double marginCentro = kpiService.calcolaMargineUnitario(centroFiltered);
        double marginSud = kpiService.calcolaMargineUnitario(sudFiltered);

        // Costo unitario medio per area (€/t) - componente negativa del margine
        double costNord = kpiService.calcolaCostoUnitario(nordFiltered);
        double costCentro = kpiService.calcolaCostoUnitario(centroFiltered);
        double costSud = kpiService.calcolaCostoUnitario(sudFiltered);

        model.addAttribute("avgMarginNord", marginNord);
        model.addAttribute("avgMarginCentro", marginCentro);
        model.addAttribute("avgMarginSud", marginSud);
        model.addAttribute("avgCostNord", costNord);
        model.addAttribute("avgCostCentro", costCentro);
        model.addAttribute("avgCostSud", costSud);

        // ===== SEZIONE 5: TABELLA DETTAGLI =====
        // Aggrega prezzo, costo, margine per area in MargineRow

        // Prezzo medio per area (€/t)
        double priceNord = kpiService.prezzoMedio(nordFiltered);
        double priceCentro = kpiService.prezzoMedio(centroFiltered);
        double priceSud = kpiService.prezzoMedio(sudFiltered);

        // Crea righe di dati per la tabella
        List<MargineRow> margineRows = Arrays.asList(
                new MargineRow("Nord", priceNord, costNord, marginNord),
                new MargineRow("Centro", priceCentro, costCentro, marginCentro),
                new MargineRow("Sud", priceSud, costSud, marginSud)
        );
        model.addAttribute("margineRows", margineRows);
    }

    // ========================================================================
    // METODI HELPER PRIVATI
    // ========================================================================

    /**
     * Converte una mappa (anno → valore) in una lista ordinata per anni.
     *
     * Utilizzo: trasformare il risultato di serieMargineUnitarioAnnuale()
     * in un formato facilmente iterabile in Thymeleaf.
     *
     * Algoritmo:
     * 1. Itera su ogni anno della lista "years" in ordine
     * 2. Per ogni anno, recupera il valore dalla mappa
     * 3. Se l'anno non è presente nella mappa, usa 0.0 come default
     * 4. Ritorna una lista ordinata per anno
     *
     * Esempio:
     * - map = {2020: 50, 2022: 80}
     * - years = [2020, 2021, 2022]
     * - output = [50, 0.0, 80]
     *
     * Vantaggio: il risultato ha sempre la stessa lunghezza di "years",
     * e il valore alla posizione i corrisponde a years.get(i).
     * Questo facilita l'allineamento con assi X in grafici Chart.js.
     *
     * @param map Mappa anno → valore (es. output di serieMargineUnitarioAnnuale)
     * @param years Lista di anni in ordine crescente
     * @return Lista di valori ordinati per anno (0.0 per anni mancanti)
     */
    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // INNER CLASS: MargineRow
    // ========================================================================

    /**
     * Record di dati per una riga della tabella di margine per area.
     *
     * Responsabilità:
     * - Contenere i dati aggregati di margine per una singola area
     * - Fornire getter accessibili da Thymeleaf
     * - Facilitare rendering tabellare nel dashboard
     * - Mostrare come il margine è composto (prezzo - costo)
     *
     * Campi:
     * - area: area geografica ("Nord", "Centro", "Sud")
     * - price: prezzo di vendita medio (€/t)
     * - cost: costo di produzione medio (€/t)
     * - margin: margine calcolato = prezzo - costo (€/t)
     *
     * Interpretazione economica:
     * - Margine positivo: profitto lordo per tonnellata
     * - Margine negativo: perdita per tonnellata (situazione critica)
     * - Margine zero: break-even (no profitto, no perdita)
     *
     * Utilizzo in Thymeleaf:
     * ```html
     * <table>
     *   <tr>
     *     <th>Area</th>
     *     <th>Prezzo (€/t)</th>
     *     <th>Costo (€/t)</th>
     *     <th>Margine (€/t)</th>
     *     <th>% Margine</th>
     *   </tr>
     *   <tr th:each="row : ${margineRows}">
     *     <td th:text="${row.area}"></td>
     *     <td th:text="${#numbers.formatDecimal(row.price, 0, 2)}"></td>
     *     <td th:text="${#numbers.formatDecimal(row.cost, 0, 2)}"></td>
     *     <td th:text="${#numbers.formatDecimal(row.margin, 0, 2)}"></td>
     *     <td th:text="${#numbers.formatDecimal(row.margin / row.price * 100, 0, 1)} + '%'"></td>
     *   </tr>
     * </table>
     * ```
     *
     * Analisi dei dati:
     * - Confrontare margini tra aree rivela disparità di redditività
     * - Margini alti in un'area possono indicare: prezzi alti O costi bassi
     * - Margini bassi possono indicare: prezzi bassi (mercato) O costi alti (inefficienza)
     */
    public static class MargineRow {

        /**
         * Area geografica ("Nord", "Centro", "Sud").
         */
        private final String area;

        /**
         * Prezzo di vendita medio per l'area in euro per tonnellata.
         * Rappresenta il ricavo lordo (senza considerare costi).
         */
        private final double price;

        /**
         * Costo di produzione medio per l'area in euro per tonnellata.
         * Include: costi fissi, materiali, manodopera, acqua.
         */
        private final double cost;

        /**
         * Margine unitario calcolato per l'area in euro per tonnellata.
         * Formula: prezzo - costo
         *
         * Interpretazione:
         * - Positivo (es. 50 €/t): profitto lordo di 50€ per ogni tonnellata venduta
         * - Negativo (es. -20 €/t): perdita di 20€ per ogni tonnellata (situazione insostenibile)
         * - Zero: nessun profitto netto
         */
        private final double margin;

        /**
         * Costruttore per creare una riga di dati di margine.
         *
         * @param area Area geografica
         * @param price Prezzo medio (€/t)
         * @param cost Costo medio (€/t)
         * @param margin Margine calcolato (€/t)
         */
        public MargineRow(String area, double price, double cost, double margin) {
            this.area = area;
            this.price = price;
            this.cost = cost;
            this.margin = margin;
        }

        /**
         * Getter: area geografica.
         * Utilizzato in template Thymeleaf: ${row.area}
         */
        public String getArea() {
            return area;
        }

        /**
         * Getter: prezzo di vendita medio.
         * Utilizzato in template Thymeleaf: ${row.price}
         *
         * Nota: il prezzo è influenzato dal mercato (fattore esterno),
         * ma anche dalla qualità del prodotto (fattore interno).
         */
        public double getPrice() {
            return price;
        }

        /**
         * Getter: costo di produzione medio.
         * Utilizzato in template Thymeleaf: ${row.cost}
         *
         * Nota: il costo è principalmente controllabile dall'azienda
         * attraverso efficienza operativa (reduce waste, optimize inputs).
         */
        public double getCost() {
            return cost;
        }

        /**
         * Getter: margine unitario calcolato (profitto lordo per tonnellata).
         * Utilizzato in template Thymeleaf: ${row.margin}
         *
         * Formula: margin = price - cost
         *
         * Interpretazione:
         * - Valore alto (es. 80-150 €/t): buona redditività
         * - Valore moderato (es. 30-80 €/t): redditività accettabile
         * - Valore basso/negativo (es. < 0 €/t): critico, insostenibile
         *
         * Per migliorare il margine:
         * 1. Aumentare il prezzo: vendere a prezzo più alto (qualità, branding)
         * 2. Ridurre il costo: migliorare efficienza operativa
         * 3. Mix di entrambi: strategia ottimale a lungo termine
         */
        public double getMargin() {
            return margin;
        }
    }
}