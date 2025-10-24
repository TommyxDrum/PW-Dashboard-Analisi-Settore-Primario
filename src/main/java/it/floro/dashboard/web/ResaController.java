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
 * Controller che espone il dashboard del KPI "Resa per ettaro (t/ha)".
 *
 * Responsabilità:
 * - Gestire la richiesta HTTP GET /resa con filtri opzionali
 * - Delega a KpiService il calcolo della resa per ettaro
 * - Aggregare dati per visualizzazione multi-granulare (giornaliera, annuale, per area)
 * - Correlate metriche (produzione totale, superficie totale)
 * - Popolare il Model Thymeleaf con attributi per la view "resa"
 *
 * Pattern: Estende BaseKpiController per riutilizzare logica comune
 * - Implementa l'hook astratto populateKpiModel
 * - Eredita il template method processKpiRequest
 * - Eredita utility per filtri (filterByArea, extractYears)
 *
 * KPI principale: Resa media per ettaro
 * - Formula: produzione in tonnellate / superficie in ettari
 * - Unità: tonnellate per ettaro (t/ha)
 * - Interpretazione: quante tonnellate di prodotto vengono generate per ettaro coltivato
 * - KPI di produttività: valori più alti indicano maggiore rendimento della terra
 *
 * Significato agricolo:
 * - La resa è l'indicatore primario di successo produttivo
 * - Dipende da: coltura, clima, tecnica colturale, qualità del terreno, acqua, fertilizzanti
 * - Benchmarking: confrontare resa tra anni, aree, colture per identificare trends e anomalie
 * - Incrementare la resa è una priorità strategica (terra è risorsa scarsa e costosa)
 *
 * Sottocomponenti visualizzati:
 * - Resa media aggregata (t/ha)
 * - Serie temporale giornaliera della resa
 * - Totali: produzione totale (t), superficie totale (ha), resa aggregata
 * - Serie temporale annuale per area (per grafici trend multi-anno)
 * - Disaggregazione per area: produzione, superficie, resa per Nord/Centro/Sud
 *
 * View renderizzata: "resa" (templates/resa.html)
 * - Grafici: lineare (trend annuale), donut (distribuzione per area), tabella
 */
@Controller
public class ResaController extends BaseKpiController {

    // ========================================================================
    // COSTRUTTORE
    // ========================================================================

    /**
     * Costruttore con dependency injection.
     * Delegato a BaseKpiController via super().
     *
     * @param sampleDataService Service per accesso dati campionati
     * @param kpiFilters Service per filtri e normalizzazione
     * @param kpiService Service per calcolo KPI (incluso resa per ettaro)
     */
    public ResaController(SampleDataService sampleDataService,
                          KpiFilters kpiFilters,
                          KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    // ========================================================================
    // ENDPOINT HTTP
    // ========================================================================

    /**
     * Endpoint GET /resa: visualizza il dashboard della resa per ettaro.
     *
     * Metodo HTTP: GET
     * Mapping: /resa
     * Risposta: HTML renderizzato (Thymeleaf template "resa.html")
     *
     * Parametri query (tutti opzionali):
     * - area: filtro area geografica (es. "Nord", "Centro", "Sud")
     *   Utilizzo: analizzare disparità di resa tra aree
     * - crop: filtro coltura (es. "Grano duro", "Mais", "Olivo", "Vite")
     *   Utilizzo: rese diverse per colture diverse (mais ha resa più alta di olivo)
     * - startDate: data inizio intervallo (formato ISO: YYYY-MM-DD)
     * - endDate: data fine intervallo (formato ISO: YYYY-MM-DD)
     * - periodo: periodo aggregazione ("giorno", "mese", "trimestre", "anno", "custom")
     * - year: anno per aggregazione annuale (es. 2024)
     * - month: mese per aggregazione mensile (es. 5)
     * - quarter: trimestre per aggregazione trimestrale (es. 2)
     *
     * Esempio URL:
     * GET /resa → dashboard con tutte le rese
     * GET /resa?crop=Mais&periodo=anno&year=2024 → rese mais anno 2024
     * GET /resa?area=Sud&area=Centro → rese Sud+Centro (filtro multi-area)
     *
     * Flusso:
     * 1. Delega a BaseKpiController.processKpiRequest()
     * 2. processKpiRequest esegue il template method:
     *    - Recupera dataset
     *    - Applica filtri (area, crop, date)
     *    - Chiama populateKpiModel() (implementato in questa classe)
     *    - Popola attributi comuni
     * 3. Ritorna il nome della view "resa"
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
     * @return Nome della view Thymeleaf ("resa")
     */
    @GetMapping("/resa")
    public String resaKpi(
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
                year, month, quarter, model, "resa");
    }

    // ========================================================================
    // HOOK ASTRATTO - IMPLEMENTAZIONE
    // ========================================================================

    /**
     * Implementazione dell'hook astratto di BaseKpiController.
     *
     * Responsabilità:
     * - Calcolare la resa per ettaro in diverse forme (aggregata, per area, serie temporali)
     * - Correlate metriche (produzione totale, superficie totale)
     * - Popolare il Model con attributi per la view Thymeleaf
     *
     * Struttura della popolazione (5 sezioni):
     *
     * SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato)
     * - resaMedia: media della resa per ettaro (t/ha)
     * - resaGiornaliera: serie temporale giornaliera (data → resa media €/t)
     *
     * SEZIONE 2: TOTALI GLOBALI (Metriche correlate)
     * - sommaProduzione: totale in tonnellate
     * - sommaSuperficie: totale superficie in ettari
     * - totalResa: resa aggregata = (totale t) / (totale ha)
     *
     * SEZIONE 3: SERIE ANNUALI PER AREA
     * - Resa annuale per Nord, Centro, Sud
     * - Utilizzato per grafici lineari/trend multi-anno
     * - Analizzare evoluzione temporale della resa per area
     *
     * SEZIONE 4: KPI PER AREA (Periodo filtrato)
     * - Produzione totale per area (t)
     * - Superficie totale per area (ha)
     * - Resa per area calcolata = produzione / superficie
     * - Utilizzato per donut chart, bar chart, comparazioni geografiche
     *
     * SEZIONE 5: TABELLA DETTAGLI
     * - Righe ResaRow con dati aggregati per area
     * - Visualizzazione tabellare nel dashboard
     *
     * @param filtered Lista di record post-filtri (area, crop, date)
     * @param model Spring Model dove aggiungere attributi
     */
    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {

        // ===== SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato) =====

        // Resa media: media della resa per ettaro su tutti i record filtrati
        // Formula in KpiService: media( yieldT / surfaceHa )
        double resaMedia = kpiService.calcolaResaMedia(filtered);

        // Serie temporale giornaliera: per ogni giorno, resa media di quel giorno
        // Utilizzata per visualizzare trend giornalieri
        Map<LocalDate, Double> resaGiornaliera = kpiService.serieResaGiornaliera(filtered);

        model.addAttribute("resaMedia", resaMedia);
        model.addAttribute("resaGiornaliera", resaGiornaliera);

        // ===== SEZIONE 2: TOTALI GLOBALI (Metriche correlate) =====
        // Calcola resa come rapporto aggregato: produzione totale / superficie totale

        // Somma totale di produzione in tonnellate
        double totalYieldT = kpiService.sommaProduzione(filtered);

        // Somma totale di superficie in ettari
        double totalSurfaceHa = kpiService.sommaSuperficie(filtered);

        // Resa aggregata: tonnellate / ettari
        // Questo è diverso da "media delle rese giornaliere"
        // È il rapporto aggregato: quanto prodotto totale per unità di terra totale
        double totalResa = totalSurfaceHa > 0 ? totalYieldT / totalSurfaceHa : 0.0;

        model.addAttribute("totalYieldT", totalYieldT);
        model.addAttribute("totalSurfaceHa", totalSurfaceHa);
        model.addAttribute("totalResa", totalResa);

        // ===== SEZIONE 3: SERIE ANNUALI PER AREA =====
        // Recupera il dataset completo per aggregazioni multi-anno

        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        // Filtra dataset completo per ogni area (senza altre restrizioni)
        List<SampleRecord> nordAll = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll = filterByArea(all, "Sud");

        // Calcola serie annuali di resa per ogni area
        Map<Integer, Double> annualResaNord = kpiService.serieResaAnnuale(nordAll);
        Map<Integer, Double> annualResaCentro = kpiService.serieResaAnnuale(centroAll);
        Map<Integer, Double> annualResaSud = kpiService.serieResaAnnuale(sudAll);

        // Converte mappe in liste ordinate per facilità in Thymeleaf
        model.addAttribute("years", years);
        model.addAttribute("annualResaNord", toOrderedList(annualResaNord, years));
        model.addAttribute("annualResaCentro", toOrderedList(annualResaCentro, years));
        model.addAttribute("annualResaSud", toOrderedList(annualResaSud, years));

        // ===== SEZIONE 4: KPI PER AREA (Periodo filtrato) =====
        // Applica gli stessi filtri (area, crop, date) ma disaggregati per area geografica

        List<SampleRecord> nordFiltered = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered = filterByArea(filtered, "Sud");

        // Produzione totale per area (tonnellate)
        double yieldNordT = kpiService.sommaProduzione(nordFiltered);
        double yieldCentroT = kpiService.sommaProduzione(centroFiltered);
        double yieldSudT = kpiService.sommaProduzione(sudFiltered);

        // Superficie totale per area (ettari)
        double surfNordHa = kpiService.sommaSuperficie(nordFiltered);
        double surfCentroHa = kpiService.sommaSuperficie(centroFiltered);
        double surfSudHa = kpiService.sommaSuperficie(sudFiltered);

        model.addAttribute("yieldNordT", yieldNordT);
        model.addAttribute("yieldCentroT", yieldCentroT);
        model.addAttribute("yieldSudT", yieldSudT);
        model.addAttribute("surfNordHa", surfNordHa);
        model.addAttribute("surfCentroHa", surfCentroHa);
        model.addAttribute("surfSudHa", surfSudHa);

        // ===== SEZIONE 5: TABELLA DETTAGLI =====
        // Aggrega i dati per area in una lista di ResaRow per visualizzazione tabellare

        List<ResaRow> resaRows = Arrays.asList(
                new ResaRow("Nord", yieldNordT, surfNordHa, surfNordHa > 0 ? yieldNordT / surfNordHa : 0),
                new ResaRow("Centro", yieldCentroT, surfCentroHa, surfCentroHa > 0 ? yieldCentroT / surfCentroHa : 0),
                new ResaRow("Sud", yieldSudT, surfSudHa, surfSudHa > 0 ? yieldSudT / surfSudHa : 0)
        );
        model.addAttribute("resaRows", resaRows);
    }

    // ========================================================================
    // METODI HELPER PRIVATI
    // ========================================================================

    /**
     * Converte una mappa (anno → valore) in una lista ordinata per anni.
     *
     * Utilizzo: trasformare il risultato di serieResaAnnuale()
     * in un formato facilmente iterabile in Thymeleaf.
     *
     * Algoritmo:
     * 1. Itera su ogni anno della lista "years" in ordine ascendente
     * 2. Per ogni anno, recupera il valore dalla mappa
     * 3. Se l'anno non è presente nella mappa, usa 0.0 come default
     * 4. Ritorna una lista ordinata per anno
     *
     * Esempio:
     * - map = {2020: 4.5, 2022: 5.2}
     * - years = [2020, 2021, 2022]
     * - output = [4.5, 0.0, 5.2]
     *
     * Vantaggio: il risultato ha sempre la stessa lunghezza di "years",
     * e il valore alla posizione i corrisponde a years.get(i).
     * Questo facilita l'allineamento con assi X in grafici Chart.js.
     *
     * @param map Mappa anno → valore (es. output di serieResaAnnuale)
     * @param years Lista di anni in ordine crescente
     * @return Lista di valori ordinati per anno (0.0 per anni mancanti)
     */
    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // INNER CLASS: ResaRow
    // ========================================================================

    /**
     * Record di dati per una riga della tabella di resa per area.
     *
     * Responsabilità:
     * - Contenere i dati aggregati di resa per una singola area
     * - Fornire getter accessibili da Thymeleaf
     * - Facilitare rendering tabellare nel dashboard
     *
     * Campi:
     * - area: area geografica ("Nord", "Centro", "Sud")
     * - yieldT: produzione totale in tonnellate
     * - surfaceHa: superficie totale in ettari
     * - resa: resa calcolata = yieldT / surfaceHa (t/ha)
     *
     * Interpretazione dei dati:
     * - Resa Nord vs Sud può rivelare differenze climati che, tecniche, qualità terreno
     * - Nord più freddo → potrebbe avere resa più bassa per colture mediterranee
     * - Sud più caldo → potrebbe avere resa più alta per olivo/vite ma più bassa per grano freddo-tollerante
     * - Benchmark storico: confrontare resa anno-su-anno per identificare miglioramenti/peggioramenti
     *
     * KPI di performance:
     * - Resa è il primo indicatore di successo produttivo
     * - Incremento resa = maggior reddito dalla stessa terra
     * - Benchmarking industriale: identificare target e best practices
     */
    public static class ResaRow {

        /**
         * Area geografica ("Nord", "Centro", "Sud").
         */
        private final String area;

        /**
         * Produzione totale della area in tonnellate.
         * Somma di tutta la produzione nel periodo filtrato.
         */
        private final double yieldT;

        /**
         * Superficie totale della area in ettari.
         * Somma di tutta la terra coltivata nel periodo filtrato.
         */
        private final double surfaceHa;

        /**
         * Resa calcolata per l'area in tonnellate per ettaro.
         * Formula: yieldT / surfaceHa
         *
         * Interpretazione:
         * - Quanto più alto il valore, migliore la produttività della terra
         * - Valori tipici: [2, 9] t/ha a seconda della coltura
         *   • Grano duro: 3-5 t/ha
         *   • Mais: 6-8 t/ha
         *   • Olivo: 1-3 t/ha (ciclo lungo, meno intensivo)
         *   • Vite: 8-12 t/ha (alta intensività)
         * - Benchmark: confrontare con medie regionali/nazionali
         *
         * Fattori che influenzano la resa:
         * - Coltura (genetica, potenziale intrinseco)
         * - Clima (temperature, precipitazioni, stagione)
         * - Terreno (fertilità, pH, struttura)
         * - Tecnica colturale (irrigazione, fertilizzazione, diserbo, trattamenti)
         * - Gestione fitosanitaria (prevenzione malattie, parassiti)
         *
         * Strategie di incremento resa:
         * 1. Selezionare varietà ad alta resa
         * 2. Ottimizzare irrigazione (disponibilità acqua appropriata)
         * 3. Nutrizione bilanciata (fertilizzazione razionale)
         * 4. Controllo fitosanitario (prevenire perdite da malattie/parassiti)
         * 5. Densità di semina/impianto appropriata
         */
        private final double resa;

        /**
         * Costruttore per creare una riga di dati di resa.
         *
         * @param area Area geografica
         * @param yieldT Produzione totale in tonnellate
         * @param surfaceHa Superficie totale in ettari
         * @param resa Resa calcolata (t/ha)
         */
        public ResaRow(String area, double yieldT, double surfaceHa, double resa) {
            this.area = area;
            this.yieldT = yieldT;
            this.surfaceHa = surfaceHa;
            this.resa = resa;
        }

        /**
         * Getter: area geografica.
         * Utilizzato in template Thymeleaf: ${row.area}
         */
        public String getArea() {
            return area;
        }

        /**
         * Getter: produzione totale in tonnellate.
         * Utilizzato in template Thymeleaf: ${row.yieldT}
         */
        public double getYieldT() {
            return yieldT;
        }

        /**
         * Getter: superficie totale in ettari.
         * Utilizzato in template Thymeleaf: ${row.surfaceHa}
         */
        public double getSurfaceHa() {
            return surfaceHa;
        }

        /**
         * Getter: resa per ettaro in tonnellate per ettaro.
         * Utilizzato in template Thymeleaf: ${row.resa}
         *
         * Interpretazione benchmark:
         * - > 7 t/ha: resa eccellente (sopra media)
         * - 5-7 t/ha: resa buona (nella media)
         * - 3-5 t/ha: resa moderata (sotto media, investigare cause)
         * - < 3 t/ha: resa bassa (critica, revisione urgente)
         */
        public double getResa() {
            return resa;
        }
    }
}