package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.BedrockChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Bedrock AI21 Labs model invoke response
 */
@Getter
@Setter
public class BedrockAI21LabsChatModelResponse implements BedrockChatModelResponse {

    @Getter
    @Setter
    public static class GeneratedToken {
        private String token;
        private double logprob;
        private double raw_logprob;
    }

    @Getter
    @Setter
    public static class TextRange {
        private int start;
        private int end;
    }

    @Getter
    @Setter
    public static class Token {
        private GeneratedToken generatedToken;
        private List<GeneratedToken> topTokens;
        private TextRange textRange;
    }

    @Getter
    @Setter
    public static class Prompt {
        private String text;
        private List<Token> tokens;
    }

    @Getter
    @Setter
    public static class CompletionReason {
        private String reason;
    }

    @Getter
    @Setter
    public static class Completion {
        private Prompt data;
        private CompletionReason finishReason;
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
        return new TokenUsage(prompt.tokens.size(), completions.get(0).data.tokens.size());
    }
}
