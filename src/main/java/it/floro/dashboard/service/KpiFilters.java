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
 * Service concreto che centralizza parsing filtri, normalizzazione stringhe e predicate.
 * Usato direttamente dai controller insieme a SampleDataService e KpiService.
 */
@Component
public class KpiFilters {

    public enum Periodo {
        GIORNO, MESE, TRIMESTRE, ANNO, CUSTOM;

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

    /** Parametri filtro consolidati. */
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
     * Costruisce FilterParams applicando coalescenza e normalizzazione del periodo.
     * Se start/end non sono forniti, li ricava da (periodo, year, month, quarter) o da min/maxDate.
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
        Periodo periodo = Periodo.ofNullable(rawPeriodo);

        LocalDate start = startDate;
        LocalDate end = endDate;

        if (start == null || end == null) {
            switch (periodo) {
                case ANNO -> {
                    int y = coalesce(year, (maxDate != null ? maxDate.getYear() : LocalDate.now().getYear()));
                    start = LocalDate.of(y, 1, 1);
                    end = LocalDate.of(y, 12, 31);
                }
                case MESE -> {
                    int y = coalesce(year, (maxDate != null ? maxDate.getYear() : LocalDate.now().getYear()));
                    int m = coalesce(month, (maxDate != null ? maxDate.getMonthValue() : LocalDate.now().getMonthValue()));
                    Month mm = Month.of(bound(m, 1, 12));
                    start = LocalDate.of(y, mm, 1);
                    end = start.withDayOfMonth(start.lengthOfMonth());
                }
                case TRIMESTRE -> {
                    int y = coalesce(year, (maxDate != null ? maxDate.getYear() : LocalDate.now().getYear()));
                    int q = bound(coalesce(quarter, quarterOfDate(coalesce(endDate, coalesce(maxDate, LocalDate.now())))), 1, 4);
                    Month first = switch (q) {
                        case 1 -> Month.JANUARY;
                        case 2 -> Month.APRIL;
                        case 3 -> Month.JULY;
                        case 4 -> Month.OCTOBER;
                        default -> Month.JANUARY;
                    };
                    start = LocalDate.of(y, first, 1);
                    end = start.plusMonths(2).withDayOfMonth(start.plusMonths(2).lengthOfMonth());
                }
                case GIORNO -> {
                    LocalDate d = coalesce(coalesce(startDate, endDate), coalesce(maxDate, LocalDate.now()));
                    start = d;
                    end = d;
                }
                case CUSTOM -> {
                    // Se CUSTOM e mancano le date, fallback a min/max
                    start = coalesce(startDate, coalesce(minDate, LocalDate.now().minusYears(1).withDayOfYear(1)));
                    end = coalesce(endDate, coalesce(maxDate, LocalDate.now()));
                }
            }
        }

        // Ordina start/end se invertite
        if (start != null && end != null && start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        String area = blankToNull(trimOrNull(rawArea));
        String crop = blankToNull(trimOrNull(rawCrop));
        return new FilterParams(area, crop, start, end, periodo, year, month, quarter);
    }

    /**
     * Predicate che applica area/crop (case- e accent-insensitive) e range date [start,end].
     * NOTA: SampleRecord è un record → usare r.date(), r.area(), r.crop().
     */
    public Predicate<SampleRecord> predicate(FilterParams p) {
        final String areaNorm = normalizeNullable(p.area());
        final String cropNorm = normalizeNullable(p.crop());
        final LocalDate from = (p.start() != null) ? p.start() : LocalDate.MIN;
        final LocalDate to   = (p.end()   != null) ? p.end()   : LocalDate.MAX;

        return r -> {
            if (r == null) return false;

            // Date
            LocalDate d = r.date();
            if (d == null || d.isBefore(from) || d.isAfter(to)) return false;

            // Area
            if (areaNorm != null) {
                String rArea = normalizeNullable(r.area());
                if (!areaNorm.equals(rArea)) return false;
            }

            // Crop
            if (cropNorm != null) {
                String rCrop = normalizeNullable(r.crop());
                if (!cropNorm.equals(rCrop)) return false;
            }

            return true;
        };
    }

    /** Elenco aree presenti, ordinate alfabeticamente (accent/case insensitive), uniche. */
    public List<String> areasFrom(List<SampleRecord> all) {
        return all.stream()
                .map(SampleRecord::area)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(alphaInsensitive())
                .collect(Collectors.toList());
    }

    /** Elenco colture presenti, ordinate alfabeticamente (accent/case insensitive), uniche. */
    public List<String> cropsFrom(List<SampleRecord> all) {
        return all.stream()
                .map(SampleRecord::crop)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(alphaInsensitive())
                .collect(Collectors.toList());
    }

    // ========= Utility private =========

    private static <T> T coalesce(T v, T fallback) {
        return (v != null) ? v : fallback;
    }

    private static int bound(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String trimOrNull(String s) {
        return s == null ? null : s.trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String normalizeNullable(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")   // rimuove diacritici
                .toLowerCase();
        // normalizza anche spazi/punteggiatura
        n = n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return n;
    }

    private static Comparator<String> alphaInsensitive() {
        return Comparator.comparing(s -> normalizeNullable(Objects.toString(s, "")));
    }

    private static int quarterOfDate(LocalDate d) {
        int m = d.getMonthValue();
        if (m <= 3) return 1;
        if (m <= 6) return 2;
        if (m <= 9) return 3;
        return 4;
    }
}
