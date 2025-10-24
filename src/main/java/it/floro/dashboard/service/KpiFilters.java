package it.floro.dashboard.service;

import it.floro.dashboard.domain.SampleRecord;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Month;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Componente centralizzato per la gestione dei filtri sul dataset agricolo.
 *
 * Responsabilità:
 * - Parsing e normalizzazione dei parametri di filtro da request HTTP
 * - Coalescenza intelligente di date in base al periodo selezionato (giorno, mese, trimestre, anno)
 * - Normalizzazione case- e accent-insensitive di stringhe (area, coltura)
 * - Generazione di Predicate per filtrare liste di SampleRecord
 * - Estrazione di valori univoci (aree, colture disponibili)
 *
 * Nota: I metodi sono utilizzati direttamente dai controller insieme a SampleDataService e KpiService.
 */
@Component
public class KpiFilters {

    /**
     * Enum che rappresenta i periodi di aggregazione dati.
     * Supporta sia terminologia italiana che inglese.
     */
    public enum Periodo {
        GIORNO,     // Analisi giornaliera
        MESE,       // Analisi mensile
        TRIMESTRE,  // Analisi trimestrale
        ANNO,       // Analisi annuale
        CUSTOM;     // Intervallo custom definito esplicitamente

        /**
         * Parsing robusto del periodo da stringa con fallback a CUSTOM.
         * Accetta sia terminologia italiana che inglese.
         *
         * Esempi validi: "giorno", "day", "mese", "month", "trimestre", "quarter", "anno", "year"
         *
         * @param s Stringa del periodo (case-insensitive)
         * @return Enum Periodo corrispondente, o CUSTOM se non riconosciuto
         */
        public static Periodo ofNullable(String s) {
            if (s == null || s.isBlank()) return CUSTOM;
            return switch (s.trim().toLowerCase()) {
                case "giorno", "day", "daily" -> GIORNO;
                case "mese", "month", "monthly" -> MESE;
                case "trimestre", "quarter", "quarterly" -> TRIMESTRE;
                case "anno", "year", "yearly" -> ANNO;
                default -> CUSTOM;
            };
        }
    }

    /**
     * Record che racchiude i parametri di filtro normalizzati e validati.
     *
     * Campi:
     * - area: Area geografica normalizzata (null = senza filtro)
     * - crop: Tipo di coltura normalizzata (null = senza filtro)
     * - start/end: Intervallo date [start, end] inclusivo
     * - periodo: Tipo di periodo selezionato
     * - year/month/quarter: Valori di periodo (solo per debug/UI)
     */
    public record FilterParams(
            String area,
            String crop,
            LocalDate start,
            LocalDate end,
            Periodo periodo,
            Integer year,
            Integer month,
            Integer quarter
    ) {}

    /**
     * Costruisce un FilterParams consolidato da parametri raw di richiesta HTTP.
     *
     * Algoritmo:
     * 1. Normalizza il periodo da stringa
     * 2. Se mancano start/end, le calcola in base a periodo (anno, mese, trimestre, giorno) e parametri annessi
     * 3. Fallback a minDate/maxDate/today se ancora mancanti
     * 4. Ordina start/end se invertite
     * 5. Normalizza area e crop (trim + blank to null)
     *
     * Esempi:
     * - periodo="mese", year=2024, month=5 → start=2024-05-01, end=2024-05-31
     * - periodo="trimestre", year=2024, quarter=2 → start=2024-04-01, end=2024-06-30
     * - periodo="anno", year=2024 → start=2024-01-01, end=2024-12-31
     * - periodo="giorno", startDate=2024-05-15 → start=2024-05-15, end=2024-05-15
     * - periodo="custom", startDate=2024-01-01, endDate=2024-06-30 → usa le date fornite
     *
     * @param rawArea Area non normalizzata
     * @param rawCrop Coltura non normalizzata
     * @param startDate Data inizio (può essere null)
     * @param endDate Data fine (può essere null)
     * @param rawPeriodo Periodo come stringa (es. "mese", "month")
     * @param year Anno per il calcolo del periodo
     * @param month Mese per il calcolo del periodo
     * @param quarter Trimestre per il calcolo del periodo
     * @param minDate Data minima nel dataset (fallback)
     * @param maxDate Data massima nel dataset (fallback)
     * @return FilterParams con campi normalizzati e date calcolate
     */
    public FilterParams fromRequest(
            String rawArea,
            String rawCrop,
            LocalDate startDate,
            LocalDate endDate,
            String rawPeriodo,
            Integer year,
            Integer month,
            Integer quarter,
            LocalDate minDate,
            LocalDate maxDate
    ) {
        // Normalizza il periodo da stringa
        Periodo periodo = Periodo.ofNullable(rawPeriodo);

        LocalDate start = startDate;
        LocalDate end = endDate;

        // Se mancano start/end, le calcola in base al periodo
        if (start == null || end == null) {
            switch (periodo) {
                // ===== PERIODO ANNUALE =====
                case ANNO -> {
                    int y = coalesce(year, (maxDate != null ? maxDate.getYear() : LocalDate.now().getYear()));
                    start = LocalDate.of(y, 1, 1);
                    end = LocalDate.of(y, 12, 31);
                }

                // ===== PERIODO MENSILE =====
                case MESE -> {
                    int y = coalesce(year, (maxDate != null ? maxDate.getYear() : LocalDate.now().getYear()));
                    int m = coalesce(month, (maxDate != null ? maxDate.getMonthValue() : LocalDate.now().getMonthValue()));
                    Month mm = Month.of(bound(m, 1, 12));
                    start = LocalDate.of(y, mm, 1);
                    end = start.withDayOfMonth(start.lengthOfMonth());  // Ultimo giorno del mese
                }

                // ===== PERIODO TRIMESTRALE =====
                case TRIMESTRE -> {
                    int y = coalesce(year, (maxDate != null ? maxDate.getYear() : LocalDate.now().getYear()));
                    int q = bound(coalesce(quarter, quarterOfDate(coalesce(endDate, coalesce(maxDate, LocalDate.now())))), 1, 4);

                    // Mappa trimestre → primo mese
                    Month first = switch (q) {
                        case 1 -> Month.JANUARY;    // Q1: gen-feb-mar
                        case 2 -> Month.APRIL;      // Q2: apr-mag-giu
                        case 3 -> Month.JULY;       // Q3: lug-ago-set
                        case 4 -> Month.OCTOBER;    // Q4: ott-nov-dic
                        default -> Month.JANUARY;
                    };

                    start = LocalDate.of(y, first, 1);
                    end = start.plusMonths(2).withDayOfMonth(start.plusMonths(2).lengthOfMonth());  // Ultimo giorno del trimestre
                }

                // ===== PERIODO GIORNALIERO =====
                case GIORNO -> {
                    LocalDate d = coalesce(coalesce(startDate, endDate), coalesce(maxDate, LocalDate.now()));
                    start = d;
                    end = d;
                }

                // ===== PERIODO CUSTOM =====
                case CUSTOM -> {
                    // Se CUSTOM e mancano le date, fallback a range storico (1 anno)
                    start = coalesce(startDate, coalesce(minDate, LocalDate.now().minusYears(1).withDayOfYear(1)));
                    end = coalesce(endDate, coalesce(maxDate, LocalDate.now()));
                }
            }
        }

        // Ordina start/end se accidentalmente invertite
        if (start != null && end != null && start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        // Normalizza area e crop (trim + blank to null)
        String area = blankToNull(trimOrNull(rawArea));
        String crop = blankToNull(trimOrNull(rawCrop));

        return new FilterParams(area, crop, start, end, periodo, year, month, quarter);
    }

    /**
     * Genera un Predicate per filtrare SampleRecord in base ai parametri consolidati.
     *
     * Il predicato applica i seguenti filtri:
     * 1. Range temporale: record.date() deve essere in [start, end] (inclusivo)
     * 2. Area: se specificata, match case- e accent-insensitive (normalizza ambo i lati)
     * 3. Coltura: se specificata, match case- e accent-insensitive (normalizza ambo i lati)
     *
     * Nota: SampleRecord è un record → usare accessor methods come r.date(), r.area(), r.crop()
     */
    public Predicate<SampleRecord> predicate(FilterParams p) {
        // Normalizza i valori di filtro una sola volta (al di fuori del predicate)
        final String areaNorm = normalizeNullable(p.area());
        final String cropNorm = normalizeNullable(p.crop());
        final LocalDate from = (p.start() != null) ? p.start() : LocalDate.MIN;
        final LocalDate to   = (p.end()   != null) ? p.end()   : LocalDate.MAX;

        return r -> {
            // Null guard
            if (r == null) return false;

            // ===== FILTRO 1: DATE [start, end] =====
            LocalDate d = r.date();
            if (d == null || d.isBefore(from) || d.isAfter(to)) return false;

            // ===== FILTRO 2: AREA (case- e accent-insensitive) =====
            if (areaNorm != null) {
                String rArea = normalizeNullable(r.area());
                if (!areaNorm.equals(rArea)) return false;
            }

            // ===== FILTRO 3: COLTURA (case- e accent-insensitive) =====
            if (cropNorm != null) {
                String rCrop = normalizeNullable(r.crop());
                if (!cropNorm.equals(rCrop)) return false;
            }

            return true;
        };
    }

    /**
     * Estrae la lista di aree geografiche uniche presenti nel dataset.
     *
     * Proprietà:
     * - Ordinate alfabeticamente (case- e accent-insensitive)
     * - Deduplicate
     * - Null values esclusi
     *
     * Utilizzato per popolare dropdown/select in UI.
     *
     * @param all Lista completa di SampleRecord
     * @return Lista di aree uniche ordinate alfabeticamente
     */
    public List<String> areasFrom(List<SampleRecord> all) {
        return all.stream()
                .map(SampleRecord::area)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(alphaInsensitive())
                .collect(Collectors.toList());
    }

    /**
     * Estrae la lista di colture uniche presenti nel dataset.
     *
     * Proprietà:
     * - Ordinate alfabeticamente (case- e accent-insensitive)
     * - Deduplicate
     * - Null values esclusi
     *
     * Utilizzato per popolare dropdown/select in UI.
     *
     * @param all Lista completa di SampleRecord
     * @return Lista di colture uniche ordinate alfabeticamente
     */
    public List<String> cropsFrom(List<SampleRecord> all) {
        return all.stream()
                .map(SampleRecord::crop)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(alphaInsensitive())
                .collect(Collectors.toList());
    }

    // ========= METODI UTILITY PRIVATI =========

    /**
     * Coalescenza null-safe: ritorna v se non null, altrimenti fallback.
     *
     * @param v Valore primario
     * @param fallback Valore di fallback
     * @return v se non null, altrimenti fallback
     */
    private static <T> T coalesce(T v, T fallback) {
        return (v != null) ? v : fallback;
    }

    /**
     * Vincola un valore intero a un range [min, max].
     *
     * @param v Valore
     * @param min Limite inferiore
     * @param max Limite superiore
     * @return v se in [min, max], min se < min, max se > max
     */
    private static int bound(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Trim null-safe: ritorna s.trim() se non null, altrimenti null.
     *
     * @param s Stringa
     * @return s.trim() o null
     */
    private static String trimOrNull(String s) {
        return s == null ? null : s.trim();
    }

    /**
     * Converte stringhe blank (null o isBlank()) a null.
     *
     * @param s Stringa
     * @return null se s è null o blank, altrimenti s
     */
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * Normalizza una stringa per confronti case- e accent-insensitive.
     *
     * Trasformazioni applicate:
     * 1. Rimozione diacritici (accenti, tilde, etc.)
     * 2. Conversione a lowercase
     * 3. Rimozione caratteri speciali (solo a-z0-9 + spazi)
     * 4. Normalizzazione whitespace (spazi multipli → singolo spazio)
     * 5. Trim
     *
     * Esempi:
     * - "NORD" → "nord"
     * - "Mele Renette" → "mele renette"
     * - "Città" → "citta"
     * - "Agro-Pontino" → "agro pontino"
     *
     * @param s Stringa da normalizzare
     * @return Stringa normalizzata, o null se s è null
     */
    private static String normalizeNullable(String s) {
        if (s == null) return null;

        // Step 1: Decomposizione Unicode (NFD) + rimozione diacritici
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")   // Rimuove marche diacritiche
                .toLowerCase();

        // Step 2: Normalizzazione caratteri speciali e whitespace
        // Mantiene solo a-z, 0-9 e spazi; sostituisce altri con spazi
        n = n.replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")      // Spazi multipli → singolo
                .trim();

        return n;
    }

    /**
     * Comparator per ordinamento alfabetico case- e accent-insensitive.
     *
     * @return Comparator<String> che normalizza e confronta
     */
    private static Comparator<String> alphaInsensitive() {
        return Comparator.comparing(s -> normalizeNullable(Objects.toString(s, "")));
    }

    /**
     * Calcola il trimestre (1-4) di una data.
     *
     * Mapping:
     * - Gen-Mar (mesi 1-3) → Q1
     * - Apr-Giu (mesi 4-6) → Q2
     * - Lug-Set (mesi 7-9) → Q3
     * - Ott-Dic (mesi 10-12) → Q4
     *
     * @param d Data
     * @return Trimestre (1-4)
     */
    private static int quarterOfDate(LocalDate d) {
        int m = d.getMonthValue();
        if (m <= 3) return 1;
        if (m <= 6) return 2;
        if (m <= 9) return 3;
        return 4;
    }
}