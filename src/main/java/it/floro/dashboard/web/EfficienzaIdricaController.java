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
 * Controller che espone il dashboard del KPI "Efficienza Idrica (kg/m³)".
 *
 * Responsabilità:
 * - Gestire la richiesta HTTP GET /efficienzaIdrica con filtri opzionali
 * - Delega a KpiService il calcolo dell'efficienza idrica
 * - Aggregare dati per visualizzazione multi-granulare (giornaliera, annuale, per area)
 * - Calcolare metriche correlate (produzione totale, consumo idrico totale)
 * - Determinare scale appropriate per grafici polari/radar
 * - Popolare il Model Thymeleaf con attributi per la view "efficienzaIdrica"
 *
 * Pattern: Estende BaseKpiController per riutilizzare logica comune
 * - Implementa l'hook astratto populateKpiModel
 * - Eredita il template method processKpiRequest
 * - Eredita utility per filtri (filterByArea, extractYears, normalizeString)
 *
 * KPI principale: Efficienza idrica aggregata
 * - Formula: (produzione in tonnellate × 1000) / consumo d'acqua in m³
 * - Unità: chilogrammi per metro cubo (kg/m³)
 * - Interpretazione: quanti kg di prodotto vengono generati per ogni m³ d'acqua utilizzata
 * - KPI di sostenibilità: valori più alti indicano minor consumo idrico relativo
 *
 * Contesto agricolo:
 * - L'efficienza idrica è critica in agricoltura, soprattutto in aree aride/mediterranee
 * - Nei modelli irrigui, l'acqua è spesso il fattore limitante
 * - L'efficienza può essere migliorata con tecniche (gocciolamento vs aspersione)
 * - Correlazione positiva con reddittività: risparmiare acqua = risparmiare costi
 *
 * Sottocomponenti visualizzati:
 * - Efficienza media aggregata (kg/m³)
 * - Serie temporale giornaliera dell'efficienza
 * - Totali globali: produzione (t), consumo (m³), efficienza aggregata
 * - Serie temporale annuale per area (per grafici radar/trend)
 * - Scala massima per normalizzazione grafici polari
 * - Disaggregazione per area: produzione, acqua, efficienza per Nord/Centro/Sud
 *
 * View renderizzata: "efficienzaIdrica" (templates/efficienzaIdrica.html)
 */
@Controller
public class EfficienzaIdricaController extends BaseKpiController {

    // ========================================================================
    // COSTRUTTORE
    // ========================================================================

    /**
     * Costruttore con dependency injection.
     * Delegato a BaseKpiController via super().
     *
     * @param sampleDataService Service per accesso dati campionati
     * @param kpiFilters Service per filtri e normalizzazione
     * @param kpiService Service per calcolo KPI (incluso efficienza idrica)
     */
    public EfficienzaIdricaController(SampleDataService sampleDataService,
                                      KpiFilters kpiFilters,
                                      KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    // ========================================================================
    // ENDPOINT HTTP
    // ========================================================================

    /**
     * Endpoint GET /efficienzaIdrica: visualizza il dashboard dell'efficienza idrica.
     *
     * Metodo HTTP: GET
     * Mapping: /efficienzaIdrica
     * Risposta: HTML renderizzato (Thymeleaf template "efficienzaIdrica.html")
     *
     * Parametri query (tutti opzionali):
     * - area: filtro area geografica (es. "Nord", "Centro", "Sud")
     *   Utilizzo: concentrarsi su una regione specifica per analizzare performance irrigua
     * - crop: filtro coltura (es. "Grano duro", "Mais", "Olivo", "Vite")
     *   Utilizzo: alcune colture sono più efficienti dell'acqua di altre
     * - startDate: data inizio intervallo (formato ISO: YYYY-MM-DD)
     * - endDate: data fine intervallo (formato ISO: YYYY-MM-DD)
     * - periodo: periodo aggregazione ("giorno", "mese", "trimestre", "anno", "custom")
     * - year: anno per aggregazione annuale (es. 2024)
     * - month: mese per aggregazione mensile (es. 5)
     * - quarter: trimestre per aggregazione trimestrale (es. 2)
     *
     * Esempio URL:
     * GET /efficienzaIdrica?area=Sud&periodo=anno&year=2024
     * → Mostra efficienza idrica media del Sud per l'anno 2024
     *
     * Flusso:
     * 1. Delega a BaseKpiController.processKpiRequest()
     * 2. processKpiRequest esegue il template method:
     *    - Recupera dataset
     *    - Applica filtri (area, crop, date)
     *    - Chiama populateKpiModel() (implementato in questa classe)
     *    - Popola attributi comuni (dropdown, filtri)
     * 3. Ritorna il nome della view "efficienzaIdrica"
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
     * @return Nome della view Thymeleaf ("efficienzaIdrica")
     */
    @GetMapping("/efficienzaIdrica")
    public String efficienzaIdricaKpi(
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
                year, month, quarter, model, "efficienzaIdrica");
    }

    // ========================================================================
    // HOOK ASTRATTO - IMPLEMENTAZIONE
    // ========================================================================

    /**
     * Implementazione dell'hook astratto di BaseKpiController.
     *
     * Responsabilità:
     * - Calcolare l'efficienza idrica in diverse forme (aggregata, per area, serie temporali)
     * - Correlate metriche (produzione totale, consumo idrico totale)
     * - Determinare scale appropriate per visualizzazioni grafiche
     * - Popolare il Model con attributi per la view Thymeleaf
     *
     * Struttura della popolazione (6 sezioni):
     *
     * SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato)
     * - efficienzaMedia: media dell'efficienza idrica kg/m³
     * - efficienzaGiorno: serie temporale giornaliera (data → efficienza kg/m³)
     *
     * SEZIONE 2: TOTALI GLOBALI (Metriche correlate)
     * - sommaProduzione: totale in tonnellate
     * - sommaAcqua: totale consumo in m³
     * - totalEfficiency: efficienza aggregata = (totale t × 1000) / (totale m³)
     *
     * SEZIONE 3: SERIE ANNUALI PER AREA
     * - Efficienza idrica annuale per Nord, Centro, Sud
     * - Utilizzato per grafici radar/trend multi-anno
     * - Analizzare evoluzione temporale dell'efficienza per area
     *
     * SEZIONE 4: SCALA MASSIMA PER GRAFICI POLARI
     * - Calcola il massimo tra tutte le metriche di efficienza
     * - Usato per normalizzare scale in grafici radar (spider plots)
     * - Assicura che i grafici polari siano comparabili tra loro
     *
     * SEZIONE 5: KPI PER AREA (Periodo filtrato)
     * - Produzione totale per area (t)
     * - Consumo idrico per area (m³)
     * - Efficienza idrica per area (kg/m³)
     * - Utilizzato per scatter plot, bar chart, comparazioni geografiche
     *
     * SEZIONE 6: TABELLA DETTAGLI
     * - Righe EfficienzaRow con dati aggregati per area
     * - Visualizzazione tabellare nel dashboard
     *
     * @param filtered Lista di record post-filtri (area, crop, date)
     * @param model Spring Model dove aggiungere attributi
     */
    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {

        // ===== SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato) =====

        // Efficienza idrica media: media dei rapporti (prodotto/acqua) per record
        // Formula in KpiService: media( (yieldT * 1000) / waterM3 )
        double efficienzaMedia = kpiService.calcolaEfficienzaIdrica(filtered);

        // Serie temporale giornaliera: per ogni giorno, efficienza media di quel giorno
        // Utilizzata per visualizzare trend giornalieri
        Map<LocalDate, Double> efficienzaGiorno = kpiService.serieEfficienzaIdricaGiornaliera(filtered);

        model.addAttribute("efficienzaMedia", efficienzaMedia);
        model.addAttribute("efficienzaGiorno", efficienzaGiorno);

        // ===== SEZIONE 2: TOTALI GLOBALI (Metriche correlate) =====
        // Calcola efficienza come rapporto aggregato: produzione totale / acqua totale

        // Somma totale di produzione in tonnellate
        double totalYieldT = kpiService.sommaProduzione(filtered);

        // Somma totale di acqua in metri cubi
        double totalWaterM3 = kpiService.sommaAcqua(filtered);

        // Efficienza aggregata: (tonnellate × 1000 per convertire a kg) / m³
        // Questo è diverso da "media delle efficienze giornaliere"
        // È il rapporto aggregato: quanto prodotto per unità d'acqua totale
        double totalEfficiency = totalWaterM3 > 0 ? (totalYieldT * 1000) / totalWaterM3 : 0.0;

        model.addAttribute("totalYieldT", totalYieldT);
        model.addAttribute("totalWaterM3", totalWaterM3);
        model.addAttribute("totalEfficiency", totalEfficiency);

        // ===== SEZIONE 3: SERIE ANNUALI PER AREA =====
        // Recupera il dataset completo per aggregazioni multi-anno

        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        // Filtra dataset completo per ogni area (senza altre restrizioni)
        List<SampleRecord> nordAll = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll = filterByArea(all, "Sud");

        // Calcola serie annuali di efficienza idrica per ogni area
        Map<Integer, Double> annualEffNord = kpiService.serieEfficienzaIdricaAnnuale(nordAll);
        Map<Integer, Double> annualEffCentro = kpiService.serieEfficienzaIdricaAnnuale(centroAll);
        Map<Integer, Double> annualEffSud = kpiService.serieEfficienzaIdricaAnnuale(sudAll);

        // Converte mappe in liste ordinate per facilità in Thymeleaf
        model.addAttribute("years", years);
        model.addAttribute("annualEfficiencyNord", toOrderedList(annualEffNord, years));
        model.addAttribute("annualEfficiencyCentro", toOrderedList(annualEffCentro, years));
        model.addAttribute("annualEfficiencySud", toOrderedList(annualEffSud, years));

        // ===== SEZIONE 4: SCALA MASSIMA PER GRAFICI POLARI =====
        // Determina il valore massimo tra tutte le efficienze per normalizzare scale grafiche

        // Inizia con il massimo tra: efficienza media attuale e 100 (baseline minima)
        double effMax = Math.max(Math.max(efficienzaMedia, 100),
                // Prende il massimo tra i massimi annuali delle tre aree
                Collections.max(Arrays.asList(
                        // Massimo annuale Nord (o 0 se nessun dato)
                        annualEffNord.values().stream().max(Double::compare).orElse(0.0),
                        // Massimo annuale Centro
                        annualEffCentro.values().stream().max(Double::compare).orElse(0.0),
                        // Massimo annuale Sud
                        annualEffSud.values().stream().max(Double::compare).orElse(0.0)
                )));

        model.addAttribute("effMaxScale", effMax);

        // ===== SEZIONE 5: KPI PER AREA (Periodo filtrato) =====
        // Applica gli stessi filtri (area, crop, date) ma disaggregati per area geografica

        List<SampleRecord> nordFiltered = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered = filterByArea(filtered, "Sud");

        // Produzione totale per area (tonnellate)
        double yieldNordT = kpiService.sommaProduzione(nordFiltered);
        double yieldCentroT = kpiService.sommaProduzione(centroFiltered);
        double yieldSudT = kpiService.sommaProduzione(sudFiltered);

        // Consumo idrico totale per area (m³)
        double waterNordM3 = kpiService.sommaAcqua(nordFiltered);
        double waterCentroM3 = kpiService.sommaAcqua(centroFiltered);
        double waterSudM3 = kpiService.sommaAcqua(sudFiltered);

        // Efficienza idrica per area: (produzione t × 1000) / (consumo m³)
        // Interpretazione per area:
        // - Nord: quanti kg di prodotto al m³ d'acqua nel Nord
        // - Centro: quanti kg di prodotto al m³ d'acqua nel Centro
        // - Sud: quanti kg di prodotto al m³ d'acqua nel Sud
        // Valori più alti indicano aree più efficienti nell'uso dell'acqua
        double effNordKgM3 = waterNordM3 > 0 ? (yieldNordT * 1000) / waterNordM3 : 0.0;
        double effCentroKgM3 = waterCentroM3 > 0 ? (yieldCentroT * 1000) / waterCentroM3 : 0.0;
        double effSudKgM3 = waterSudM3 > 0 ? (yieldSudT * 1000) / waterSudM3 : 0.0;

        model.addAttribute("yieldNordT", yieldNordT);
        model.addAttribute("yieldCentroT", yieldCentroT);
        model.addAttribute("yieldSudT", yieldSudT);
        model.addAttribute("waterNordM3", waterNordM3);
        model.addAttribute("waterCentroM3", waterCentroM3);
        model.addAttribute("waterSudM3", waterSudM3);
        model.addAttribute("effNordKgM3", effNordKgM3);
        model.addAttribute("effCentroKgM3", effCentroKgM3);
        model.addAttribute("effSudKgM3", effSudKgM3);

        // ===== SEZIONE 6: TABELLA DETTAGLI =====
        // Aggrega i dati per area in una lista di EfficienzaRow per visualizzazione tabellare

        List<EfficienzaRow> efficienzaRows = Arrays.asList(
                new EfficienzaRow("Nord", yieldNordT, waterNordM3, effNordKgM3),
                new EfficienzaRow("Centro", yieldCentroT, waterCentroM3, effCentroKgM3),
                new EfficienzaRow("Sud", yieldSudT, waterSudM3, effSudKgM3)
        );
        model.addAttribute("efficienzaRows", efficienzaRows);
    }

    // ========================================================================
    // METODI HELPER PRIVATI
    // ========================================================================

    /**
     * Converte una mappa (anno → valore) in una lista ordinata per anni.
     *
     * Utilizzo: trasformare il risultato di serieEfficienzaIdricaAnnuale()
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
     * @param map Mappa anno → valore (es. output di serieEfficienzaIdricaAnnuale)
     * @param years Lista di anni in ordine crescente
     * @return Lista di valori ordinati per anno (0.0 per anni mancanti)
     */
    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // INNER CLASS: EfficienzaRow
    // ========================================================================

    /**
     * Record di dati per una riga della tabella di efficienza idrica per area.
     *
     * Responsabilità:
     * - Contenere i dati aggregati di efficienza idrica per una singola area
     * - Fornire getter accessibili da Thymeleaf
     * - Facilitare rendering tabellare nel dashboard
     *
     * Campi:
     * - area: area geografica ("Nord", "Centro", "Sud")
     * - yieldT: produzione totale in tonnellate
     * - waterM3: consumo idrico totale in m³
     * - efficiencyKgM3: efficienza idrica calcolata (kg/m³)
     *
     * Interpretazione:
     * - Area Nord: produce X tonnellate usando Y m³ d'acqua
     *   → Efficienza: Z kg per ogni m³ d'acqua
     * - Confronto tra aree rivela disparità nella gestione irrigua
     * - Valori più alti indicano gestione più sostenibile
     */
    public static class EfficienzaRow {

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
         * Consumo idrico totale della area in metri cubi.
         * Somma di tutta l'acqua irrigata nel periodo filtrato.
         */
        private final double waterM3;

        /**
         * Efficienza idrica calcolata in chilogrammi per metro cubo.
         * Formula: (yieldT × 1000) / waterM3
         *
         * Interpretazione:
         * - Quanto più alto il valore, migliore l'efficienza
         * - Valori tipici: [2, 10] kg/m³ a seconda della coltura e gestione
         * - Benchmark: Sud Italia tende ad avere valori più alti (clima secco-tollerante)
         */
        private final double efficiencyKgM3;

        /**
         * Costruttore per creare una riga di dati di efficienza idrica.
         *
         * @param area Area geografica
         * @param yieldT Produzione totale in tonnellate
         * @param waterM3 Consumo acqua totale in m³
         * @param efficiencyKgM3 Efficienza idrica (kg/m³)
         */
        public EfficienzaRow(String area, double yieldT, double waterM3, double efficiencyKgM3) {
            this.area = area;
            this.yieldT = yieldT;
            this.waterM3 = waterM3;
            this.efficiencyKgM3 = efficiencyKgM3;
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
         * Getter: consumo idrico totale in m³.
         * Utilizzato in template Thymeleaf: ${row.waterM3}
         */
        public double getWaterM3() {
            return waterM3;
        }

        /**
         * Getter: efficienza idrica in kg/m³.
         * Utilizzato in template Thymeleaf: ${row.efficiencyKgM3}
         *
         * Interpretazione:
         * - Valore alto (es. 6-8 kg/m³): efficienza buona
         * - Valore basso (es. 2-3 kg/m³): inefficienza, possibili sprechi idrici
         */
        public double getEfficiencyKgM3() {
            return efficiencyKgM3;
        }
    }
}