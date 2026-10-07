package dev.langchain4j.model.openai.internal.decision;

import dev.langchain4j.Internal;
import java.util.List;

/**
 * The body of the response to {@code POST /decisions}.
 */
@Internal
public class DecisionCreateResponse {

    public String model;
    public List<Answer> answers;
    public Usage usage;

    public static class Answer {

        public String type;
        public String name;
        public Double probability;

        /**
         * A {@link String} or a {@link Boolean}.
         */
        public Object choice;

        public Double score;
        public List<Probability> probabilities;
        public Double confidence;
    }

    public static class Probability {

        /**
         * The choice value (a {@link String} or a {@link Boolean}), or the index of the score level.
         */
        public Object value;

        public String label;
        public Double probability;
    }

    public static class Usage {

        public Integer inputTokens;
        public InputTokensDetails inputTokensDetails;
        public Integer outputTokens;
        public OutputTokensDetails outputTokensDetails;
        public Integer totalTokens;
    }

    public static class InputTokensDetails {

        public Integer cachedTokens;
        public Integer cacheWriteTokens;
    }

    public static class OutputTokensDetails {

        public Integer reasoningTokens;
    }
}
