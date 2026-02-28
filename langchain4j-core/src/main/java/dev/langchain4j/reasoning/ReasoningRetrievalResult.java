package dev.langchain4j.reasoning;

import static java.util.Collections.unmodifiableList;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.List;

/**
 * The result of a retrieval operation from a {@link ReasoningBank}.
 *
 * @since 1.11.0
 */
@Experimental
public class ReasoningRetrievalResult {

    private final List<ReasoningMatch> matches;

    /**
     * Creates a new retrieval result.
     *
     * @param matches The list of matches.
     */
    public ReasoningRetrievalResult(List<ReasoningMatch> matches) {
        this.matches = matches != null ? unmodifiableList(new ArrayList<>(matches)) : List.of();
    }

    /**
     * Returns the list of matches.
     *
     * @return An unmodifiable list of matches.
     */
    public List<ReasoningMatch> matches() {
        return matches;
    }

    /**
     * Returns true if there are no matches.
     *
     * @return true if empty.
     */
    public boolean isEmpty() {
        return matches.isEmpty();
    }

    /**
     * Returns the number of matches.
     *
     * @return The number of matches.
     */
    public int size() {
        return matches.size();
    }

    /**
     * Returns just the strategies from the matches.
     *
     * @return A list of strategies.
     */
    public List<ReasoningStrategy> strategies() {
        return matches.stream().map(ReasoningMatch::strategy).toList();
    }

    /**
     * Formats all matched strategies for prompt injection.
     *
     * @return A formatted string of all strategies.
     */
    public String toPromptText() {
        if (matches.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Relevant reasoning strategies from past experiences:\n\n");
        for (int i = 0; i < matches.size(); i++) {
            ReasoningMatch match = matches.get(i);
            sb.append(i + 1).append(". ").append(match.strategy().toPromptText());
            sb.append(" (confidence: ")
                    .append(String.format("%.0f%%", match.score() * 100))
                    .append(")\n\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ReasoningRetrievalResult{" + "matches=" + matches + '}';
    }

    /**
     * Creates an empty result.
     *
     * @return An empty result.
     */
    public static ReasoningRetrievalResult empty() {
        return new ReasoningRetrievalResult(List.of());
    }

    /**
     * Creates a result from a list of matches.
     *
     * @param matches The matches.
     * @return A new result.
     */
    public static ReasoningRetrievalResult from(List<ReasoningMatch> matches) {
        return new ReasoningRetrievalResult(matches);
    }
}
