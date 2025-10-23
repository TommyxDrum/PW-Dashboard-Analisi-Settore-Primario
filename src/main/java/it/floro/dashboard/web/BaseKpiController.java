package it.floro.dashboard.web;

import it.floro.dashboard.domain.SampleRecord;
import it.floro.dashboard.service.KpiFilters;
import it.floro.dashboard.service.KpiFilters.FilterParams;
import it.floro.dashboard.service.KpiService;
import it.floro.dashboard.service.SampleDataService;
import org.springframework.ui.Model;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller astratto base che centralizza la logica comune di tutti i KPI controller.
 *
 * Pattern: Template Method
 * - Definisce il flusso di elaborazione standard per tutti i controller KPI
 * - Fornisce hook per personalizzazione (metodo astratto populateKpiModel)
 * - Elimina duplicazione di codice tra KpiResaController, KpiEfficienzaController, etc.
 *
 * Responsabilità:
 * - Gestire il flusso comune: recupero dati → filtri → aggregazione KPI
 * - Fornire utility per filtraggi (area, crop, normalizzazione stringhe)
 * - Popolare Model con attributi comuni (liste area, crop, date selezionate)
 * - Gestire conversione di parametri request → FilterParams
 *
 * Flusso tipico (processKpiRequest):
 * 1. Recupera dataset completo da SampleDataService
 * 2. Costruisce FilterParams da parametri request
 * 3. Applica filtri (area, crop, date) con predicato
 * 4. Delega a sottoclasse (populateKpiModel) per aggiungere KPI specifici
 * 5. Popola attributi comuni nel Model (dropdown lists, date, filtri)
 *
 * Sottoclassi concrete: KpiResaController, KpiEfficienzaController, KpiCostoController, etc.
 */
public abstract class BaseKpiController {

    // ========================================================================
    // DIPENDENZE INIETTATE
    // ========================================================================

    /**
     * Service che fornisce accesso ai dati campionati.
     * Utilizzato per recuperare il dataset completo e limiti temporali.
     */
    protected final SampleDataService sampleDataService;

    /**
     * Service che gestisce la costruzione di filtri e predicati.
     * Utilizzato per: parsing parametri, normalizzazione stringhe, generazione predicate.
     */
    protected final KpiFilters kpiFilters;

    /**
     * Service che calcola i KPI da dati filtrati.
     * Utilizzato per aggregazioni (medie, serie temporali, totali).
     */
    protected final KpiService kpiService;

    // ========================================================================
    // COSTRUTTORE
    // ========================================================================

    /**
     * Costruttore con dependency injection delle tre dipendenze principali.
     *
     * @param sampleDataService Service per accesso dati
     * @param kpiFilters Service per filtri e predicati
     * @param kpiService Service per calcolo KPI
     */
    protected BaseKpiController(SampleDataService sampleDataService,
                                KpiFilters kpiFilters,
                                KpiService kpiService) {
        this.sampleDataService = sampleDataService;
        this.kpiFilters = kpiFilters;
        this.kpiService = kpiService;
    }

    // ========================================================================
    // TEMPLATE METHOD - FLUSSO STANDARD
    // ========================================================================

    /**
     * Template method: esegue il flusso standard di elaborazione per tutti i controller KPI.
     *
     * Pattern: Template Method
     * - Definisce la sequenza di step invarianti
     * - Delega ai ganci (hook) populateKpiModel per step varianti (specifici per KPI)
     *
     * Flusso (5 step):
     *
     * 1. RECUPERO DATI COMPLETI
     *    - Legge l'intero dataset da SampleDataService (inizializzazione lazy)
     *    - Questo include 10 anni di storico simulato
     *
     * 2. PARSING PARAMETRI REQUEST
     *    - Converte parametri raw HTTP in FilterParams strutturato
     *    - Applica coalescenza su date mancanti
     *    - Normalizza periodo (giorno/mese/trimestre/anno/custom)
     *
     * 3. APPLICAZIONE FILTRI
     *    - Genera predicato da FilterParams (area, crop, date)
     *    - Applica predicato al dataset completo con stream().filter()
     *    - Restituisce subset di record filtrato
     *
     * 4. CALCOLO KPI SPECIFICI (Hook)
     *    - Delegato a populateKpiModel (implementato dalle sottoclassi)
     *    - Ogni sottoclasse calcola e aggiunge i suoi KPI specifici al Model
     *    - Esempi:
     *      - KpiResaController → aggiunge resaMedia, serieResa, resaPerArea
     *      - KpiEfficienzaController → aggiunge efficienzaMedia, serieEfficiency, etc.
     *
     * 5. POPOLO ATTRIBUTI COMUNI
     *    - Aggiunge liste dropdown (aree, colture)
     *    - Aggiunge filtri applicati (area, crop, date, periodo)
     *    - Aggiunge informazioni per UI (date minima/massima)
     *
     * @param area Area geografica (nullable, caso insensitive)
     * @param crop Coltura (nullable, caso insensitive)
     * @param startDate Data inizio (nullable, dipenderà dal periodo)
     * @param endDate Data fine (nullable, dipenderà dal periodo)
     * @param periodo Periodo aggregazione: "giorno", "mese", "trimestre", "anno", "custom"
     * @param year Anno per aggregazione annuale (es. periodo="anno")
     * @param month Mese per aggregazione mensile (es. periodo="mese")
     * @param quarter Trimestre per aggregazione trimestrale (es. periodo="trimestre")
     * @param model Spring Model dove aggiungere attributi per la view
     * @param viewName Nome della view Thymeleaf da renderizzare (es. "kpi/resa", "kpi/efficienza")
     * @return Nome della view da renderizzare
     */
    protected String processKpiRequest(
            String area,
            String crop,
            LocalDate startDate,
            LocalDate endDate,
            String periodo,
            Integer year,
            Integer month,
            Integer quarter,
            Model model,
            String viewName
    ) {
        // ===== STEP 1: RECUPERO DATASET COMPLETO =====
        List<SampleRecord> all = sampleDataService.getAll();

        // ===== STEP 2: PARSING PARAMETRI E COSTRUZIONE FILTRI =====
        FilterParams params = kpiFilters.fromRequest(
                area, crop, startDate, endDate, periodo, year, month, quarter,
                sampleDataService.getMinDate(), sampleDataService.getMaxDate()
        );

        // ===== STEP 3: APPLICAZIONE FILTRI AL DATASET =====
        // Genera predicato da FilterParams e lo applica al dataset
        // Risultato: subset di record che rispetta area, crop, date filters
        List<SampleRecord> filtered = all.stream()
                .filter(kpiFilters.predicate(params))
                .collect(Collectors.toList());

        // ===== STEP 4: DELEGAZIONE A SOTTOCLASSE (HOOK) =====
        // Ogni sottoclasse implementa populateKpiModel con i suoi KPI specifici
        // Accede a kpiService per calcolare metriche
        populateKpiModel(filtered, model);

        // ===== STEP 5: POPOLO ATTRIBUTI COMUNI =====
        // Aggiunge dati comuni a tutte le view KPI (dropdown, filtri, date)
        populateCommonAttributes(model, all, params);

        return viewName;
    }

    // ========================================================================
    // HOOK ASTRATTO - DA IMPLEMENTARE IN SOTTOCLASSI
    // ========================================================================

    /**
     * Hook astratto: metodo template che le sottoclassi devono implementare.
     *
     * Responsabilità (delegata alle sottoclassi):
     * - Calcolare i KPI specifici per quel controller
     * - Aggiungere attributi specifici al Model per la view
     *
     * Esempi di implementazione:
     *
     * KpiResaController:
     * ```
     * @Override
     * protected void populateKpiModel(List<SampleRecord> filtered, Model model) {
     *     double resaMedia = kpiService.calcolaResaMedia(filtered);
     *     Map<LocalDate, Double> serieGiornaliera = kpiService.serieResaGiornaliera(filtered);
     *     model.addAttribute("resaMedia", resaMedia);
     *     model.addAttribute("serieResa", serieGiornaliera);
     * }
     * ```
     *
     * KpiEfficienzaController:
     * ```
     * @Override
     * protected void populateKpiModel(List<SampleRecord> filtered, Model model) {
     *     double efficienzaMedia = kpiService.calcolaEfficienzaIdrica(filtered);
     *     Map<Integer, Double> serieAnnuale = kpiService.serieEfficienzaIdricaAnnuale(filtered);
     *     model.addAttribute("efficienzaMedia", efficienzaMedia);
     *     model.addAttribute("serieEfficiency", serieAnnuale);
     * }
     * ```
     *
     * @param filtered Lista di record post-filtri (area, crop, date)
     * @param model Spring Model per aggiungere attributi view-specific
     */
    protected abstract void populateKpiModel(List<SampleRecord> filtered, Model model);

    // ========================================================================
    // METODI HELPER COMUNI
    // ========================================================================

    /**
     * Popola il Model con attributi comuni a tutti i controller KPI.
     *
     * Attributi aggiunti:
     * - areasList: lista di aree disponibili nel dataset (per dropdown)
     * - cropsList: lista di colture disponibili nel dataset (per dropdown)
     * - selectedArea: area attualmente filtrata (nullable)
     * - selectedCrop: coltura attualmente filtrata (nullable)
     * - startDate, endDate: intervallo date filtrato
     * - from, to: alias per startDate, endDate
     * - area, crop: alias per selectedArea, selectedCrop
     * - periodo: periodo aggregazione selezionato (GIORNO/MESE/TRIMESTRE/ANNO/CUSTOM)
     *
     * Utilizzo in Thymeleaf:
     * ```html
     * <select name="area">
     *     <option value="">-- Tutte --</option>
     *     <option th:each="a : ${areasList}" th:value="${a}">[[${a}]]</option>
     * </select>
     *
     * <p>Periodo: [[${periodo}]]</p>
     * <p>Da [[${from}]] a [[${to}]]</p>
     * ```
     *
     * @param model Spring Model per aggiungere attributi
     * @param all Dataset completo (usato per estrarre liste disponibili)
     * @param params FilterParams applicato (contiene valori correnti)
     */
    private void populateCommonAttributes(Model model, List<SampleRecord> all, FilterParams params) {
        // Liste dropdown per filtri
        model.addAttribute("areasList", kpiFilters.areasFrom(all));
        model.addAttribute("cropsList", kpiFilters.cropsFrom(all));

        // Valori correnti dei filtri
        model.addAttribute("selectedArea", params.area());
        model.addAttribute("selectedCrop", params.crop());

        // Range date
        model.addAttribute("startDate", params.start());
        model.addAttribute("endDate", params.end());

        // Alias (per comodità in template)
        model.addAttribute("from", params.start());
        model.addAttribute("to", params.end());
        model.addAttribute("area", params.area());
        model.addAttribute("crop", params.crop());

        // Periodo aggregazione
        model.addAttribute("periodo", params.periodo().name());
    }

    /**
     * Filtra una lista di record per area geografica specifica.
     *
     * Caratteristiche:
     * - Case-insensitive: "NORD", "nord", "Nord" sono equivalenti
     * - Accent-insensitive: "Sudà", "Suda" sono equivalenti
     * - Null-safe: se area è null/blank, ritorna l'intera lista
     *
     * Algoritmo:
     * 1. Se area è null o blank, ritorna tutti i record
     * 2. Normalizza la stringa area (lowercase, rimuove accenti)
     * 3. Filtra record dove r.area() (normalizzato) uguaglia area
     *
     * @param records Lista di record da filtrare
     * @param area Area da filtrare (es. "Nord", "CENTRO", "sud")
     * @return Lista filtrata per area (o originale se area è null)
     */
    protected List<SampleRecord> filterByArea(List<SampleRecord> records, String area) {
        if (area == null || area.isBlank()) {
            return records;
        }
        String areaNorm = normalizeString(area);
        return records.stream()
                .filter(r -> r.area() != null && normalizeString(r.area()).equals(areaNorm))
                .collect(Collectors.toList());
    }

    /**
     * Estrae una lista di anni unici dal dataset.
     *
     * Utilizzo: popolare dropdown di selezione anno per filtri temporali.
     *
     * Procedura:
     * 1. Estrae data di ogni record
     * 2. Filtra null
     * 3. Estrae l'anno dalla data (LocalDate.getYear())
     * 4. Deduplica con distinct()
     * 5. Ordina in ascendente
     *
     * Esempio:
     * - Input: [2020-05-15, 2021-03-20, 2020-07-10, 2022-01-01, 2021-12-25]
     * - Output: [2020, 2021, 2022]
     *
     * @param records Lista di record da estrarre anni
     * @return Lista di anni unici ordinata in ascendente
     */
    protected List<Integer> extractYears(List<SampleRecord> records) {
        return records.stream()
                .map(SampleRecord::date)
                .filter(d -> d != null)
                .map(LocalDate::getYear)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Normalizza una stringa per confronti case- e accent-insensitive.
     *
     * Trasformazioni applicate:
     * 1. Decomposizione Unicode (NFD): scompone caratteri accentati
     *    Es. "à" → "a" + accent mark
     * 2. Rimozione diacritici: rimuove i segni diacritici
     *    Es. "Città" → "citta"
     * 3. Lowercase: converte a minuscole
     *    Es. "NORD" → "nord"
     * 4. Trim: rimuove spazi iniziali/finali
     *    Es. " Nord " → "nord"
     *
     * Esempi:
     * - "NORD" → "nord"
     * - "Città" → "citta"
     * - " CeNTRO " → "centro"
     * - "SUD" → "sud"
     *
     * Nota: null input ritorna stringa vuota ("") per evitare NullPointerException
     *
     * Regex pattern \\p{M}+ rappresenta le marche diacritiche Unicode
     *
     * @param s Stringa da normalizzare
     * @return Stringa normalizzata (lowercase, senza accenti, trimmed)
     */
    private String normalizeString(String s) {
        if (s == null) return "";

        // Step 1: Decomposizione Unicode (NFD)
        // "à" viene scomposto in "a" + combining diaeresis mark
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);

        // Step 2: Rimozione diacritici
        // \\p{M}+ rappresenta tutte le marche diacritiche combinate
        normalized = normalized.replaceAll("\\p{M}+", "");

        // Step 3 & 4: Lowercase e trim
        return normalized.toLowerCase().trim();
    }
}