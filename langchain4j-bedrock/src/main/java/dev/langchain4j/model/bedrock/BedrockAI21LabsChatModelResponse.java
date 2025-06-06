package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockAI21LabsChatModelResponse implements BedrockChatModelResponse {

    public static class GeneratedToken {
        private String token;
        private double logprob;
        private double raw_logprob;

        public String getToken() {
            return token;
        }

        public void setToken(final String token) {
            this.token = token;
        }

        public double getLogprob() {
            return logprob;
        }

        public void setLogprob(final double logprob) {
            this.logprob = logprob;
        }

        public double getRaw_logprob() {
            return raw_logprob;
        }

        public void setRaw_logprob(final double raw_logprob) {
            this.raw_logprob = raw_logprob;
        }
    }

    public static class TextRange {
        private int start;
        private int end;

        public int getStart() {
            return start;
        }

        public void setStart(final int start) {
            this.start = start;
        }

        public int getEnd() {
            return end;
        }

        public void setEnd(final int end) {
            this.end = end;
        }
    }

    public static class Token {
        private GeneratedToken generatedToken;
        private List<GeneratedToken> topTokens;
        private TextRange textRange;

        public GeneratedToken getGeneratedToken() {
            return generatedToken;
        }

        public void setGeneratedToken(final GeneratedToken generatedToken) {
            this.generatedToken = generatedToken;
        }

        public List<GeneratedToken> getTopTokens() {
            return topTokens;
        }

        public void setTopTokens(final List<GeneratedToken> topTokens) {
            this.topTokens = topTokens;
        }

        public TextRange getTextRange() {
            return textRange;
        }

        public void setTextRange(final TextRange textRange) {
            this.textRange = textRange;
        }
    }

    public static class Prompt {
        private String text;
        private List<Token> tokens;

        public String getText() {
            return text;
        }

        public void setText(final String text) {
            this.text = text;
        }

        public List<Token> getTokens() {
            return tokens;
        }

        public void setTokens(final List<Token> tokens) {
            this.tokens = tokens;
        }
    }

    public static class CompletionReason {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(final String reason) {
            this.reason = reason;
        }
    }

    public static class Completion {
        private Prompt data;
        private CompletionReason finishReason;

        public Prompt getData() {
            return data;
        }

        public void setData(final Prompt data) {
            this.data = data;
        }

        public CompletionReason getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(final CompletionReason finishReason) {
            this.finishReason = finishReason;
        }
    }

    private int id;
    private Prompt prompt;
    private List<Completion> completions;

    @Override
    public String getOutputText() {
        return completions.get(0).data.text;
    }

    @Override
    public FinishReason getFinishReason() {
        final String finishReason = completions.get(0).getFinishReason().getReason();
        switch (finishReason) {
            case "endoftext":
                return FinishReason.STOP;
            default:
                throw new IllegalStateException("Unknown finish reason: " + finishReason);
        }
    }

    @Override
    public TokenUsage getTokenUsage() {
        return new TokenUsage(
                prompt.tokens.size(), completions.get(0).data.tokens.size());
    }

    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    public Prompt getPrompt() {
        return prompt;
    }

    public void setPrompt(final Prompt prompt) {
        this.prompt = prompt;
    }

    public List<Completion> getCompletions() {
        return completions;
    }

    public void setCompletions(final List<Completion> completions) {
        this.completions = completions;
    }
}
