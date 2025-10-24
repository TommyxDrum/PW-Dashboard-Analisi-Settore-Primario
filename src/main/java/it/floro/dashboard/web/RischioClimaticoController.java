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
 * Controller che espone il dashboard del KPI "Rischio Climatico (indice 0-1)".
 *
 * Responsabilità:
 * - Gestire la richiesta HTTP GET /rischioClimatico con filtri opzionali
 * - Delega a KpiService il calcolo del rischio climatico
 * - Scomporre il rischio totale in tre componenti: temperatura, acqua, gelate
 * - Aggregare dati per visualizzazione multi-granulare (giornaliera, annuale, per area)
 * - Popolare il Model Thymeleaf con attributi per la view "rischioClimatico"
 *
 * Pattern: Estende BaseKpiController per riutilizzare logica comune
 * - Implementa l'hook astratto populateKpiModel
 * - Eredita il template method processKpiRequest
 * - Eredita utility per filtri (filterByArea, extractYears)
 *
 * KPI principale: Rischio climatico aggregato
 * - Scala: indice [0..1]
 * - 0 = nessun rischio (condizioni meteorologiche ideali)
 * - 1 = rischio massimo (condizioni meteorologiche critiche/estreme)
 * - Interpretazione: probabilità e severità di danni dovuti a stress meteorologico
 *
 * Significato agricolo:
 * - Il rischio climatico è il fattore più impredittibile e incontrollabile
 * - Gli agricoltori non possono controllare il meteo, ma possono pianificare mitigation
 * - Fondamentale per assicurazioni agricole, crediti agrari, pianificazione investimenti
 * - Eventi estremi (siccità, gelate tardive, alluvioni) possono annullare un intero raccolto
 *
 * Componenti del rischio (scomposizione stimata):
 * 1. RISCHIO TEMPERATURA (peso 50%)
 *    - Temperature estreme (< -5°C o > 40°C) danneggiano le colture
 *    - Stress termico riduce resa e qualità
 *    - Nord: rischio gelate (inverno troppo rigido)
 *    - Sud: rischio caldo (estate troppo torrida)
 *    - Fattori: anomalie termiche, ondate di caldo, gelate tardive/precoci
 *
 * 2. RISCHIO ACQUA/SICCITÀ (peso 30%)
 *    - Deficit idrico critico in agricoltura irrigua e pluviale
 *    - Stress idrico estivo (luglio-agosto) è il fattore limitante principale in Sud Italia
 *    - Precipitazioni insufficienti durante ciclo vegetativo
 *    - Fattori: assenza di pioggia, siccità prolungata, basse riserve idriche
 *
 * 3. RISCHIO GELATE (peso 20%)
 *    - Gelate tardive (aprile-maggio) uccidono boccioli e fiori
 *    - Critiche per colture precoci (frutta, vite in fioritura)
 *    - Gelate invernali possono danneggiare piante pluriennali (olivo, vite, frutteto)
 *    - Fattori: temperature sotto zero durante fasi critiche di sviluppo
 *
 * Note sulla scomposizione:
 * - La scomposizione attuale è STIMATA (i dati non hanno ancora la granularità)
 * - In futuro, integrare misure granulari per ogni componente da stazioni meteo
 * - Pesi (50%, 30%, 20%) sono approssimativi e basati su impatto agricolo medio
 * - Pesi andrebbero adattati per coltura (vite teme gelate, mais teme siccità)
 *
 * Differenze regionali previste:
 * - Nord: rischio temperatura maggiore (gelate) + rischio acqua moderato
 * - Centro: rischio equilibrato tra temperatura e acqua
 * - Sud: rischio acqua maggiore (siccità estiva) + rischio temperatura (caldo)
 *
 * Sottocomponenti visualizzati:
 * - Rischio medio aggregato (indice [0..1])
 * - Serie temporale giornaliera del rischio
 * - Scomposizione totale: rischio temp, rischio acqua, rischio gelate
 * - Serie temporale annuale per area
 * - Disaggregazione per area: indice totale e componenti per Nord/Centro/Sud
 *
 * View renderizzata: "rischioClimatico" (templates/rischioClimatico.html)
 * - Grafici: gauge (rischio totale), stacked bar (componenti), trend annuale
 */
@Controller
public class RischioClimaticoController extends BaseKpiController {

    // ========================================================================
    // COSTANTI DI CONFIGURAZIONE - PESI SCOMPOSIZIONE
    // ========================================================================

    /**
     * Peso della componente temperatura nel rischio climatico totale.
     *
     * Assunzione: il 50% del rischio climatico è dovuto a:
     * - Temperature estreme (troppo freddo o troppo caldo)
     * - Anomalie termiche durante fasi critiche di sviluppo
     * - Gelate tardive/precoci
     * - Ondate di caldo
     *
     * Nota: questo peso è una stima semplificata. In realtà:
     * - Colture freddo-tolleranti (grano): gelate invernali sono il rischio principale
     * - Colture caldo-tolleranti (mais): caldo estivo è il rischio principale
     * - Implementare pesi per-coltura per maggiore precisione
     */
    private static final double TEMP_WEIGHT = 0.50;

    /**
     * Peso della componente acqua/siccità nel rischio climatico totale.
     *
     * Assunzione: il 30% del rischio climatico è dovuto a:
     * - Deficit idrico (assenza di pioggia prolungata)
     * - Siccità estiva critica (luglio-agosto nel Mediterraneo)
     * - Basse riserve idriche in serbatoi/falde
     * - Stress idrico che riduce resa anche se non causa perdita totale
     *
     * Nota: il Sud Italia soffre principalmente di stress idrico estivo
     * - Implementare pesi adattivi per area (Sud: 40%, Nord: 20%)
     */
    private static final double WATER_WEIGHT = 0.30;

    /**
     * Peso della componente gelate nel rischio climatico totale.
     *
     * Assunzione: il 20% del rischio climatico è dovuto a:
     * - Gelate invernali (-5°C o sotto per giorni)
     * - Gelate tardive in primavera (aprile-maggio)
     * - Gelate precoci in autunno (settembre-ottobre)
     * - Danno critico solo se durante fasi vulnerabili di sviluppo
     *
     * Nota: Nord Italia ha rischio gelate più alto (~70% del rischio climatico)
     * - Implementare pesi adattivi per area (Nord: 35%, Sud: 10%)
     */
    private static final double FROST_WEIGHT = 0.20;

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
    public RischioClimaticoController(SampleDataService sampleDataService,
                                      KpiFilters kpiFilters,
                                      KpiService kpiService) {
        super(sampleDataService, kpiFilters, kpiService);
    }

    // ========================================================================
    // ENDPOINT HTTP
    // ========================================================================

    /**
     * Endpoint GET /rischioClimatico: visualizza il dashboard del rischio climatico.
     *
     * Metodo HTTP: GET
     * Mapping: /rischioClimatico
     * Risposta: HTML renderizzato (Thymeleaf template "rischioClimatico.html")
     *
     * Parametri query (tutti opzionali):
     * - area: filtro area geografica (es. "Nord", "Centro", "Sud")
     *   Utilizzo: analizzare rischi specifici per area (Nord teme gelate, Sud teme siccità)
     * - crop: filtro coltura (es. "Grano duro", "Mais")
     *   Utilizzo: futuro - pesi del rischio varieranno per coltura
     * - startDate: data inizio intervallo (formato ISO: YYYY-MM-DD)
     * - endDate: data fine intervallo (formato ISO: YYYY-MM-DD)
     * - periodo: periodo aggregazione ("giorno", "mese", "trimestre", "anno", "custom")
     * - year: anno per aggregazione annuale (es. 2024)
     * - month: mese per aggregazione mensile (es. 5)
     * - quarter: trimestre per aggregazione trimestrale (es. 2)
     *
     * Esempio URL:
     * GET /rischioClimatico → dashboard con rischi globali
     * GET /rischioClimatico?area=Nord&periodo=anno&year=2024 → rischi Nord anno 2024
     * GET /rischioClimatico?periodo=mese&year=2024&month=7 → rischi luglio 2024
     *
     * Flusso:
     * 1. Delega a BaseKpiController.processKpiRequest()
     * 2. processKpiRequest esegue il template method:
     *    - Recupera dataset
     *    - Applica filtri
     *    - Chiama populateKpiModel() (implementato in questa classe)
     *    - Popola attributi comuni
     * 3. Ritorna il nome della view "rischioClimatico"
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
     * @return Nome della view Thymeleaf ("rischioClimatico")
     */
    @GetMapping("/rischioClimatico")
    public String rischioClimaticoKpi(
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
                year, month, quarter, model, "rischioClimatico");
    }

    // ========================================================================
    // HOOK ASTRATTO - IMPLEMENTAZIONE
    // ========================================================================

    /**
     * Implementazione dell'hook astratto di BaseKpiController.
     *
     * Responsabilità:
     * - Calcolare il rischio climatico in diverse forme (aggregato, per area, serie temporali)
     * - Scomporre il rischio totale nelle tre componenti (temperatura, acqua, gelate)
     * - Popolare il Model con attributi per la view Thymeleaf
     *
     * Struttura della popolazione (5 sezioni):
     *
     * SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato)
     * - rischioMedio: media dell'indice di rischio climatico [0..1]
     * - rischioGiornaliero: serie temporale giornaliera (data → rischio medio giornaliero)
     *
     * SEZIONE 2: SCOMPOSIZIONE TOTALE (Stima componenti)
     * - riskIndex: rischio totale aggregato
     * - riskTemp: componente temperatura (50% stima)
     * - riskWater: componente acqua/siccità (30% stima)
     * - riskFrost: componente gelate (20% stima, non attualmente visualizzato)
     *
     * SEZIONE 3: SERIE ANNUALI PER AREA
     * - Rischio climatico annuale per Nord, Centro, Sud
     * - Utilizzato per grafici trend multi-anno
     * - Analizzare evoluzione temporale del rischio per area
     *
     * SEZIONE 4: KPI PER AREA (Periodo filtrato)
     * - Rischio totale per area (Nord, Centro, Sud)
     * - Scomposizione per area: temperatura, acqua, gelate
     * - Utilizzato per gauge, stacked bar chart, comparazioni geografiche
     *
     * SEZIONE 5: TABELLA DETTAGLI
     * - Righe RischioRow con dati aggregati per area
     * - Visualizzazione tabellare nel dashboard
     *
     * @param filtered Lista di record post-filtri (area, crop, date)
     * @param model Spring Model dove aggiungere attributi
     */
    @Override
    protected void populateKpiModel(List<SampleRecord> filtered, Model model) {

        // ===== SEZIONE 1: KPI BASE (Aggregato sul periodo filtrato) =====

        // Rischio climatico medio: media dell'indice di rischio [0..1]
        double rischioMedio = kpiService.calcolaRischioClimatico(filtered);

        // Serie temporale giornaliera: per ogni giorno, rischio medio di quel giorno
        // Utilizzata per visualizzare trend giornalieri e identificare giorni critici
        Map<LocalDate, Double> rischioGiornaliero = kpiService.serieRischioClimaticoGiornaliera(filtered);

        model.addAttribute("rischioMedio", rischioMedio);
        model.addAttribute("rischioGiornaliero", rischioGiornaliero);

        // ===== SEZIONE 2: SCOMPOSIZIONE TOTALE (Stima componenti) =====
        // Scompone il rischio totale nelle tre componenti usando i pesi

        // Rischio totale aggregato [0..1]
        double totalAvgRiskIndex = rischioMedio;

        // Componente temperatura: 50% del rischio totale
        // Rappresenta il rischio dovuto a temperature estreme
        double totalAvgRiskTemp = rischioMedio * TEMP_WEIGHT;

        // Componente acqua/siccità: 30% del rischio totale
        // Rappresenta il rischio dovuto a deficit idrico
        double totalAvgRiskWater = rischioMedio * WATER_WEIGHT;

        // Componente gelate: 20% del rischio totale
        // Rappresenta il rischio dovuto a gelate critiche
        double totalAvgRiskFrost = rischioMedio * FROST_WEIGHT;

        model.addAttribute("totalAvgRiskIndex", totalAvgRiskIndex);
        model.addAttribute("totalAvgRiskTemp", totalAvgRiskTemp);
        model.addAttribute("totalAvgRiskWater", totalAvgRiskWater);

        // ===== SEZIONE 3: SERIE ANNUALI PER AREA =====
        // Recupera il dataset completo per aggregazioni multi-anno

        List<SampleRecord> all = sampleDataService.getAll();
        List<Integer> years = extractYears(all);

        // Filtra dataset completo per ogni area (senza altre restrizioni)
        List<SampleRecord> nordAll = filterByArea(all, "Nord");
        List<SampleRecord> centroAll = filterByArea(all, "Centro");
        List<SampleRecord> sudAll = filterByArea(all, "Sud");

        // Calcola serie annuali di rischio per ogni area
        Map<Integer, Double> annualRiskNord = kpiService.serieRischioClimaticoAnnuale(nordAll);
        Map<Integer, Double> annualRiskCentro = kpiService.serieRischioClimaticoAnnuale(centroAll);
        Map<Integer, Double> annualRiskSud = kpiService.serieRischioClimaticoAnnuale(sudAll);

        // Converte mappe in liste ordinate per facilità in Thymeleaf
        model.addAttribute("years", years);
        model.addAttribute("annualRiskNord", toOrderedList(annualRiskNord, years));
        model.addAttribute("annualRiskCentro", toOrderedList(annualRiskCentro, years));
        model.addAttribute("annualRiskSud", toOrderedList(annualRiskSud, years));

        // ===== SEZIONE 4: KPI PER AREA (Periodo filtrato) =====
        // Applica gli stessi filtri (area, crop, date) ma disaggregati per area geografica

        List<SampleRecord> nordFiltered = filterByArea(filtered, "Nord");
        List<SampleRecord> centroFiltered = filterByArea(filtered, "Centro");
        List<SampleRecord> sudFiltered = filterByArea(filtered, "Sud");

        // Rischio climatico medio per area [0..1]
        double riskNord = kpiService.calcolaRischioClimatico(nordFiltered);
        double riskCentro = kpiService.calcolaRischioClimatico(centroFiltered);
        double riskSud = kpiService.calcolaRischioClimatico(sudFiltered);

        // Scomposizione stimata per area: componente temperatura
        double riskTempNord = riskNord * TEMP_WEIGHT;
        double riskTempCentro = riskCentro * TEMP_WEIGHT;
        double riskTempSud = riskSud * TEMP_WEIGHT;

        // Scomposizione stimata per area: componente acqua/siccità
        double riskWaterNord = riskNord * WATER_WEIGHT;
        double riskWaterCentro = riskCentro * WATER_WEIGHT;
        double riskWaterSud = riskSud * WATER_WEIGHT;

        // Scomposizione stimata per area: componente gelate
        double riskFrostNord = riskNord * FROST_WEIGHT;
        double riskFrostCentro = riskCentro * FROST_WEIGHT;
        double riskFrostSud = riskSud * FROST_WEIGHT;

        model.addAttribute("riskTempNord", riskTempNord);
        model.addAttribute("riskTempCentro", riskTempCentro);
        model.addAttribute("riskTempSud", riskTempSud);
        model.addAttribute("riskWaterNord", riskWaterNord);
        model.addAttribute("riskWaterCentro", riskWaterCentro);
        model.addAttribute("riskWaterSud", riskWaterSud);
        model.addAttribute("riskFrostNord", riskFrostNord);
        model.addAttribute("riskFrostCentro", riskFrostCentro);
        model.addAttribute("riskFrostSud", riskFrostSud);

        // ===== SEZIONE 5: TABELLA DETTAGLI =====
        // Aggrega i dati per area in una lista di RischioRow per visualizzazione tabellare

        List<RischioRow> rischioRows = Arrays.asList(
                new RischioRow("Nord", riskNord, riskTempNord, riskWaterNord, riskFrostNord),
                new RischioRow("Centro", riskCentro, riskTempCentro, riskWaterCentro, riskFrostCentro),
                new RischioRow("Sud", riskSud, riskTempSud, riskWaterSud, riskFrostSud)
        );
        model.addAttribute("rischioRows", rischioRows);
    }

    // ========================================================================
    // METODI HELPER PRIVATI
    // ========================================================================

    /**
     * Converte una mappa (anno → valore) in una lista ordinata per anni.
     *
     * Utilizzo: trasformare il risultato di serieRischioClimaticoAnnuale()
     * in un formato facilmente iterabile in Thymeleaf.
     *
     * Algoritmo:
     * 1. Itera su ogni anno della lista "years" in ordine
     * 2. Per ogni anno, recupera il valore dalla mappa
     * 3. Se l'anno non è presente nella mappa, usa 0.0 come default
     * 4. Ritorna una lista ordinata per anno
     *
     * Esempio:
     * - map = {2020: 0.45, 2022: 0.68}
     * - years = [2020, 2021, 2022]
     * - output = [0.45, 0.0, 0.68]
     *
     * Vantaggio: il risultato ha sempre la stessa lunghezza di "years",
     * e il valore alla posizione i corrisponde a years.get(i).
     * Questo facilita l'allineamento con assi X in grafici Chart.js.
     *
     * @param map Mappa anno → valore (es. output di serieRischioClimaticoAnnuale)
     * @param years Lista di anni in ordine crescente
     * @return Lista di valori ordinati per anno (0.0 per anni mancanti)
     */
    private List<Double> toOrderedList(Map<Integer, Double> map, List<Integer> years) {
        return years.stream()
                .map(y -> map.getOrDefault(y, 0.0))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // INNER CLASS: RischioRow
    // ========================================================================

    /**
     * Record di dati per una riga della tabella di rischio climatico per area.
     *
     * Responsabilità:
     * - Contenere i dati aggregati di rischio climatico per una singola area
     * - Fornire getter accessibili da Thymeleaf
     * - Facilitare rendering tabellare nel dashboard
     * - Mostrare come il rischio totale è composto dalle tre componenti
     *
     * Campi:
     * - area: area geografica ("Nord", "Centro", "Sud")
     * - riskIndex: indice di rischio climatico totale [0..1]
     * - riskTemp: componente temperatura (peso 50%) [0..0.5]
     * - riskWater: componente acqua/siccità (peso 30%) [0..0.3]
     * - riskFrost: componente gelate (peso 20%) [0..0.2]
     *
     * Interpretazione dei dati:
     * - Nord: rischio temperatura alto (gelate invernali) + rischio acqua moderato
     * - Centro: rischio equilibrato tra tutte le componenti
     * - Sud: rischio acqua alto (siccità estiva) + rischio temperatura moderato
     *
     * Profili di rischio regionali previsti:
     * - Nord: riskIndex ~0.35, riskTemp ~0.20 (gelate), riskWater ~0.10, riskFrost ~0.05
     * - Centro: riskIndex ~0.45, riskTemp ~0.20, riskWater ~0.20, riskFrost ~0.05
     * - Sud: riskIndex ~0.50, riskTemp ~0.18 (caldo), riskWater ~0.25 (siccità), riskFrost ~0.07
     *
     * Utilizzo pratico:
     * - Assicurazioni: basare premi su riskIndex per area
     * - Pianificazione: selezionare strategie mitigazione basate su componente dominante
     * - Crediti agrari: valutare sostenibilità investimenti in base a rischio
     * - Ricerca: identificare aree di ricerca prioritaria per clima-resilienza
     */
    public static class RischioRow {

        /**
         * Area geografica ("Nord", "Centro", "Sud").
         */
        private final String area;

        /**
         * Indice di rischio climatico totale per l'area.
         * Scala: [0..1] dove 0 = nessun rischio, 1 = rischio massimo.
         *
         * Interpretazione:
         * - [0.0 - 0.2]: Rischio basso (condizioni meteo favorevoli)
         * - [0.2 - 0.4]: Rischio moderato (possibili anomalie meteorologiche)
         * - [0.4 - 0.6]: Rischio medio-alto (frequenti stress meteorologici)
         * - [0.6 - 0.8]: Rischio alto (molto probabili perdite di resa)
         * - [0.8 - 1.0]: Rischio critico (fallimento raccolto possibile)
         */
        private final double riskIndex;

        /**
         * Componente temperatura del rischio climatico.
         * Scala: [0..0.5] (50% del rischio totale).
         *
         * Rappresenta il rischio dovuto a:
         * - Ondate di caldo (temperature > 35°C in fasi critiche)
         * - Gelate tardive (temperature < 0°C in aprile-maggio)
         * - Gelate invernali (temperature < -10°C)
         * - Anomalie termiche (sbalzi improvvisi)
         *
         * Interpretazione per area:
         * - Nord: riskTemp alto (~0.20) → gelate invernali frequenti
         * - Sud: riskTemp moderato (~0.18) → caldo estivo, meno gelate
         *
         * Mitigation:
         * - Selezionare varietà freddo-tolleranti (Nord)
         * - Selezionare varietà caldo-tolleranti (Sud)
         * - Pianificare irrigazione per raffreddamento (Sud estate)
         */
        private final double riskTemp;

        /**
         * Componente acqua/siccità del rischio climatico.
         * Scala: [0..0.3] (30% del rischio totale).
         *
         * Rappresenta il rischio dovuto a:
         * - Siccità (assenza di pioggia > 30 giorni)
         * - Deficit idrico in estate (luglio-agosto)
         * - Basse riserve negli acquiferi e serbatoi
         * - Stress idrico che riduce resa anche senza morte della pianta
         *
         * Interpretazione per area:
         * - Nord: riskWater moderato (~0.10) → piogge più regolari
         * - Sud: riskWater alto (~0.25) → siccità estiva critica
         *
         * Mitigation:
         * - Implementare irrigazione effi ciente (gocciolamento)
         * - Costruire serbatoi per raccolta acqua invernale
         * - Scegliere colture resistenti alla siccità (olivo > mais)
         * - Utilizzo di ammendanti organici per trattenere umidità
         */
        private final double riskWater;

        /**
         * Componente gelate del rischio climatico.
         * Scala: [0..0.2] (20% del rischio totale).
         *
         * Rappresenta il rischio dovuto a:
         * - Gelate invernali critiche per piante pluriennali (olivo, vite)
         * - Gelate tardive in primavera che uccidono fiori/boccioli
         * - Gelate precoci in autunno che fermano ciclo vegetativo
         * - Durata e intensità delle gelate (gelate brevi meno critiche)
         *
         * Interpretazione per area:
         * - Nord: riskFrost più alto (~0.07) → gelate invernali frequenti
         * - Sud: riskFrost basso (~0.05) → gelate rare
         *
         * Mitigation:
         * - Proteggere colture sensibili (protezione antivento, drenaggio aria fredda)
         * - Selezionare siti con minore rischio gelate (esposizione, altimetria)
         * - Assicurare drenaggio notturno aria fredda (piante in avallamento)
         * - Irrig azione notturna durante gelate tardive (rilascia calore)
         */
        private final double riskFrost;

        /**
         * Costruttore per creare una riga di dati di rischio climatico.
         *
         * @param area Area geografica
         * @param riskIndex Indice di rischio totale [0..1]
         * @param riskTemp Componente temperatura [0..0.5]
         * @param riskWater Componente acqua [0..0.3]
         * @param riskFrost Componente gelate [0..0.2]
         */
        public RischioRow(String area, double riskIndex, double riskTemp, double riskWater, double riskFrost) {
            this.area = area;
            this.riskIndex = riskIndex;
            this.riskTemp = riskTemp;
            this.riskWater = riskWater;
            this.riskFrost = riskFrost;
        }

        /**
         * Getter: area geografica.
         * Utilizzato in template Thymeleaf: ${row.area}
         */
        public String getArea() {
            return area;
        }

        /**
         * Getter: indice di rischio climatico totale.
         * Utilizzato in template Thymeleaf: ${row.riskIndex}
         *
         * Interpretazione:
         * - Valore basso (< 0.3): condizioni meteo favorevoli
         * - Valore moderato (0.3-0.5): occorre monitoraggio
         * - Valore alto (> 0.5): situazione critica, mitigation urgente
         */
        public double getRiskIndex() {
            return riskIndex;
        }

        /**
         * Getter: componente temperatura del rischio.
         * Utilizzato in template Thymeleaf: ${row.riskTemp}
         */
        public double getRiskTemp() {
            return riskTemp;
        }

        /**
         * Getter: componente acqua/siccità del rischio.
         * Utilizzato in template Thymeleaf: ${row.riskWater}
         *
         * Nota: questa è spesso la componente dominante nel Sud Italia
         * durante i mesi estivi (luglio-settembre).
         */
        public double getRiskWater() {
            return riskWater;
        }

        /**
         * Getter: componente gelate del rischio.
         * Utilizzato in template Thymeleaf: ${row.riskFrost}
         */
        public double getRiskFrost() {
            return riskFrost;
        }
    }
}