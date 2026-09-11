package dev.langchain4j.rag.content.aggregator;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ContentAggregator} decorator that strips duplicated text from consecutive {@link TextSegment}s
 * of the same source document that were split with overlap.
 * <br>
 * When a document is split with overlap (e.g., by {@code DocumentSplitters.recursive(maxSize, overlapSize)}),
 * adjacent segments share a common suffix/prefix. When both segments of such a pair are retrieved,
 * the shared text would appear twice in the prompt. This aggregator detects the shared text by a
 * suffix/prefix match of at least {@value #MIN_OVERLAP_CHARS} characters and removes it from the
 * later segment (the one with the higher {@code "index"} metadata value). The order of the aggregated
 * list is preserved.
 * <br>
 * <br>
 * Detection is a heuristic string match, so it is deliberately conservative:
 * <pre>
 * - Segments are considered part of the same document only when their metadata maps are identical
 *   except for the {@value #SEGMENT_INDEX_KEY} key, and contain at least one other key. Segments
 *   without any identifying metadata are never modified, since document identity cannot be established.
 * - Only strictly consecutive segments (index N and N + 1) are compared.
 * - A segment is never stripped down to blank text; identical segments are left unchanged.
 * - Segments with a missing or non-numeric {@value #SEGMENT_INDEX_KEY} value are never modified.
 * </pre>
 * <br>
 * Usage:
 * <pre>
 * ContentAggregator aggregator =
 *         new OverlapRemovingContentAggregator(new DefaultContentAggregator());
 * </pre>
 *
 * @see DefaultContentAggregator
 * @see ReRankingContentAggregator
 */
public class OverlapRemovingContentAggregator implements ContentAggregator {

    /** Metadata key added by document splitters to identify each segment's position within its document. */
    static final String SEGMENT_INDEX_KEY = "index";

    /** Minimum suffix/prefix match length, in characters, required to strip an overlap. */
    static final int MIN_OVERLAP_CHARS = 10;

    private final ContentAggregator delegate;

    public OverlapRemovingContentAggregator(ContentAggregator delegate) {
        this.delegate = ensureNotNull(delegate, "delegate");
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        return removeOverlaps(delegate.aggregate(queryToContents));
    }

    static List<Content> removeOverlaps(List<Content> contents) {
        if (contents.size() < 2) {
            return contents;
        }

        // Group original list positions by document identity: all metadata except "index".
        // Segments whose metadata contains nothing besides "index" are skipped entirely,
        // otherwise segments from different metadata-less documents would collapse into
        // one group and their colliding indexes could trigger cross-document stripping.
        Map<Map<String, Object>, List<Integer>> docGroups = new LinkedHashMap<>();
        for (int i = 0; i < contents.size(); i++) {
            Map<String, Object> docKey =
                    documentKey(contents.get(i).textSegment().metadata());
            if (docKey.isEmpty()) {
                continue;
            }
            docGroups.computeIfAbsent(docKey, k -> new ArrayList<>()).add(i);
        }

        Content[] result = contents.toArray(new Content[0]);

        for (List<Integer> positions : docGroups.values()) {
            if (positions.size() < 2) {
                continue;
            }

            positions.sort(Comparator.comparing(
                    (Integer position) ->
                            segmentIndex(contents.get(position).textSegment().metadata()),
                    Comparator.nullsFirst(Comparator.naturalOrder())));

            for (int i = 0; i < positions.size() - 1; i++) {
                int posA = positions.get(i);
                int posB = positions.get(i + 1);

                Integer indexA = segmentIndex(result[posA].textSegment().metadata());
                Integer indexB = segmentIndex(result[posB].textSegment().metadata());

                // Only strip overlap between strictly consecutive segments.
                if (indexA == null || indexB == null || indexB != indexA + 1) {
                    continue;
                }

                String prevText = result[posA].textSegment().text();
                String currText = result[posB].textSegment().text();
                String stripped = stripOverlap(prevText, currText, MIN_OVERLAP_CHARS);

                // Never produce a blank segment; identical segments stay unchanged.
                if (!stripped.equals(currText) && !stripped.isBlank()) {
                    TextSegment strippedSegment = TextSegment.from(
                            stripped, result[posB].textSegment().metadata());
                    result[posB] = Content.from(strippedSegment, result[posB].metadata());
                }
            }
        }

        return Arrays.asList(result);
    }

    /**
     * Returns {@code curr} with its leading prefix removed when that prefix is also a suffix of {@code prev}
     * and is at least {@code minChars} characters long. Returns {@code curr} unchanged if no such overlap
     * is found. The longest matching overlap takes precedence.
     */
    static String stripOverlap(String prev, String curr, int minChars) {
        int maxCheck = Math.min(prev.length(), curr.length());
        for (int len = maxCheck; len >= minChars; len--) {
            if (prev.endsWith(curr.substring(0, len))) {
                return curr.substring(len).stripLeading();
            }
        }
        return curr;
    }

    private static Map<String, Object> documentKey(Metadata metadata) {
        Map<String, Object> key = new HashMap<>(metadata.toMap());
        key.remove(SEGMENT_INDEX_KEY);
        return key;
    }

    private static Integer segmentIndex(Metadata metadata) {
        Object value = metadata.toMap().get(SEGMENT_INDEX_KEY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
