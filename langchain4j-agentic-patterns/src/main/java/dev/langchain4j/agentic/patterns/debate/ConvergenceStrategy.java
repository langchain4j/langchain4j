package dev.langchain4j.agentic.patterns.debate;

import java.util.Collection;
import java.util.Objects;

@FunctionalInterface
public interface ConvergenceStrategy {

    boolean hasConverged(Collection<Object> positions);

    static ConvergenceStrategy unanimous() {
        return positions -> {
            if (positions.isEmpty()) {
                return true;
            }
            Object first = positions.iterator().next();
            return positions.stream().allMatch(p -> Objects.equals(first, p));
        };
    }

    static ConvergenceStrategy unanimousLastWord() {
        return positions -> {
            String firstVerdict = null;
            for (Object p : positions) {
                String text = p.toString().trim();
                String[] tokens = text.split("\\s+");
                String lastWord = tokens[tokens.length - 1]
                        .replaceAll("^[^\\p{Alnum}]+|[^\\p{Alnum}]+$", "")
                        .toUpperCase();
                if (firstVerdict == null) {
                    firstVerdict = lastWord;
                } else if (!firstVerdict.equals(lastWord)) {
                    return false;
                }
            }
            return firstVerdict != null;
        };
    }
}
