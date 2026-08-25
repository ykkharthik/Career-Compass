package util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small generic ranking utilities. Extracted because "sort a map's entries
 * by value, descending" and "find the single best entry" kept recurring —
 * TrendsService had two private, String-specific copies of the first, and
 * NaiveBayesClassifier had its own hand-rolled version of the second — so
 * this is one generic, bounded-type implementation each now shares instead.
 */
public final class Rankings {

    private Rankings() {}

    /**
     * Sorts a map's entries by value, descending, keeping at most
     * {@code limit} of them ({@code limit <= 0} means "keep all").
     */
    public static <K, V extends Comparable<V>> Map<K, V> topByValue(Map<K, V> map, int limit) {
        Map<K, V> sorted = new LinkedHashMap<>();
        long keep = limit <= 0 ? Long.MAX_VALUE : limit;
        map.entrySet().stream()
                .sorted(Map.Entry.<K, V>comparingByValue().reversed())
                .limit(keep)
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /** The key whose value is greatest, or null if the map is empty. */
    public static <K, V extends Comparable<V>> K argMax(Map<K, V> map) {
        return map.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
