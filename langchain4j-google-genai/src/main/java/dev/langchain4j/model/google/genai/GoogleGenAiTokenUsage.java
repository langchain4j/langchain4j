package dev.langchain4j.model.google.genai;

import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

/**
 * Google GenAI-specific {@link TokenUsage} that additionally exposes the token counts
 * reported by Gemini for cached content, for thinking and for tool results fed back to the model.
 * <p>
 * {@link #cachedContentTokenCount()} is a subset of {@link #inputTokenCount()}, whereas
 * {@link #toolUsePromptTokenCount()} and {@link #thoughtsTokenCount()} are counted on top of it:
 * Gemini defines the total as input + output + tool use prompt + thoughts. None of the three can be
 * derived from the inherited counts. See
 * <a href="https://ai.google.dev/gemini-api/docs/generate-content/thinking">thinking</a> and
 * <a href="https://ai.google.dev/gemini-api/docs/caching">context caching</a>.
 * <p>
 * Instances are returned as the {@code tokenUsage} of {@link GoogleGenAiChatResponseMetadata}:
 * <pre>{@code
 * GoogleGenAiTokenUsage tokenUsage = (GoogleGenAiTokenUsage) chatResponse.metadata().tokenUsage();
 * Integer thoughtsTokenCount = tokenUsage.thoughtsTokenCount();
 * }</pre>
 */
public class GoogleGenAiTokenUsage extends TokenUsage {

    private final Integer cachedContentTokenCount;
    private final Integer thoughtsTokenCount;
    private final Integer toolUsePromptTokenCount;

    private GoogleGenAiTokenUsage(Builder builder) {
        super(builder.inputTokenCount, builder.outputTokenCount, builder.totalTokenCount);
        this.cachedContentTokenCount = builder.cachedContentTokenCount;
        this.thoughtsTokenCount = builder.thoughtsTokenCount;
        this.toolUsePromptTokenCount = builder.toolUsePromptTokenCount;
    }

    /**
     * Returns the number of tokens read from the cached content, or {@code null} if the model
     * did not report it. These tokens are already included in {@link #inputTokenCount()}.
     *
     * @return the cached content token count
     */
    public Integer cachedContentTokenCount() {
        return cachedContentTokenCount;
    }

    /**
     * Returns the number of tokens the model generated while thinking, or {@code null} if the model
     * did not report it. These tokens are generated and billed in addition to
     * {@link #outputTokenCount()}.
     *
     * @return the thoughts token count
     */
    public Integer thoughtsTokenCount() {
        return thoughtsTokenCount;
    }

    /**
     * Returns the number of tokens in the tool results that were fed back to the model as input,
     * or {@code null} if the model did not report it. These tokens are <b>not</b> included in
     * {@link #inputTokenCount()}; Gemini reports them as a separate part of the total.
     *
     * @return the tool use prompt token count
     */
    public Integer toolUsePromptTokenCount() {
        return toolUsePromptTokenCount;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The cached content, thoughts and tool use prompt token counts are summed only when
     * {@code that} is a {@code GoogleGenAiTokenUsage} as well; otherwise the counts of this
     * instance are kept.
     */
    @Override
    public GoogleGenAiTokenUsage add(TokenUsage that) {
        if (that == null) {
            return this;
        }

        return GoogleGenAiTokenUsage.builder()
                .inputTokenCount(sum(this.inputTokenCount(), that.inputTokenCount()))
                .outputTokenCount(sum(this.outputTokenCount(), that.outputTokenCount()))
                .totalTokenCount(sum(this.totalTokenCount(), that.totalTokenCount()))
                .cachedContentTokenCount(addCachedContentTokenCount(that))
                .thoughtsTokenCount(addThoughtsTokenCount(that))
                .toolUsePromptTokenCount(addToolUsePromptTokenCount(that))
                .build();
    }

    private Integer addCachedContentTokenCount(TokenUsage that) {
        if (that instanceof GoogleGenAiTokenUsage thatGoogleGenAiTokenUsage) {
            return sum(this.cachedContentTokenCount, thatGoogleGenAiTokenUsage.cachedContentTokenCount);
        }
        return this.cachedContentTokenCount;
    }

    private Integer addThoughtsTokenCount(TokenUsage that) {
        if (that instanceof GoogleGenAiTokenUsage thatGoogleGenAiTokenUsage) {
            return sum(this.thoughtsTokenCount, thatGoogleGenAiTokenUsage.thoughtsTokenCount);
        }
        return this.thoughtsTokenCount;
    }

    private Integer addToolUsePromptTokenCount(TokenUsage that) {
        if (that instanceof GoogleGenAiTokenUsage thatGoogleGenAiTokenUsage) {
            return sum(this.toolUsePromptTokenCount, thatGoogleGenAiTokenUsage.toolUsePromptTokenCount);
        }
        return this.toolUsePromptTokenCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GoogleGenAiTokenUsage that = (GoogleGenAiTokenUsage) o;
        return Objects.equals(cachedContentTokenCount, that.cachedContentTokenCount)
                && Objects.equals(thoughtsTokenCount, that.thoughtsTokenCount)
                && Objects.equals(toolUsePromptTokenCount, that.toolUsePromptTokenCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cachedContentTokenCount, thoughtsTokenCount, toolUsePromptTokenCount);
    }

    @Override
    public String toString() {
        return "GoogleGenAiTokenUsage {" + " inputTokenCount = "
                + inputTokenCount() + ", outputTokenCount = "
                + outputTokenCount() + ", totalTokenCount = "
                + totalTokenCount() + ", cachedContentTokenCount = "
                + cachedContentTokenCount + ", thoughtsTokenCount = "
                + thoughtsTokenCount + ", toolUsePromptTokenCount = "
                + toolUsePromptTokenCount + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer inputTokenCount;
        private Integer outputTokenCount;
        private Integer totalTokenCount;
        private Integer cachedContentTokenCount;
        private Integer thoughtsTokenCount;
        private Integer toolUsePromptTokenCount;

        public Builder inputTokenCount(Integer inputTokenCount) {
            this.inputTokenCount = inputTokenCount;
            return this;
        }

        public Builder outputTokenCount(Integer outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
            return this;
        }

        public Builder totalTokenCount(Integer totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
            return this;
        }

        public Builder cachedContentTokenCount(Integer cachedContentTokenCount) {
            this.cachedContentTokenCount = cachedContentTokenCount;
            return this;
        }

        public Builder thoughtsTokenCount(Integer thoughtsTokenCount) {
            this.thoughtsTokenCount = thoughtsTokenCount;
            return this;
        }

        public Builder toolUsePromptTokenCount(Integer toolUsePromptTokenCount) {
            this.toolUsePromptTokenCount = toolUsePromptTokenCount;
            return this;
        }

        public GoogleGenAiTokenUsage build() {
            return new GoogleGenAiTokenUsage(this);
        }
    }
}
