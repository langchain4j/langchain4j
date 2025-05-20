package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockTitanChatModelResponse implements BedrockChatModelResponse {

    @Override
    public String getOutputText() {
        if (results.isEmpty()) {
            throw new IllegalStateException("No results returned");
        }

        return results.get(0).outputText;
    }

    @Override
    public FinishReason getFinishReason() {
        if (results.isEmpty()) {
            throw new IllegalStateException("No results returned");
        }

        final Result result = results.get(0);
        switch (result.completionReason) {
            case "FINISH":
                return FinishReason.STOP;
            default:
                return FinishReason.LENGTH;
        }
    }

    @Override
    public TokenUsage getTokenUsage() {
        if (results.isEmpty()) {
            throw new IllegalStateException("No results returned");
        }

        final Result result = results.get(0);
        return new TokenUsage(inputTextTokenCount, result.tokenCount);
    }

    public static class Result {
        private int tokenCount;
        private String outputText;
        private String completionReason;

        public int getTokenCount() {
            return tokenCount;
        }

        public void setTokenCount(final int tokenCount) {
            this.tokenCount = tokenCount;
        }

        public String getOutputText() {
            return outputText;
        }

        public void setOutputText(final String outputText) {
            this.outputText = outputText;
        }

        public String getCompletionReason() {
            return completionReason;
        }

        public void setCompletionReason(final String completionReason) {
            this.completionReason = completionReason;
        }
    }

    private int inputTextTokenCount;
    private List<Result> results;

    public int getInputTextTokenCount() {
        return inputTextTokenCount;
    }

    public void setInputTextTokenCount(final int inputTextTokenCount) {
        this.inputTextTokenCount = inputTextTokenCount;
    }

    public List<Result> getResults() {
        return results;
    }

    public void setResults(final List<Result> results) {
        this.results = results;
    }
}
