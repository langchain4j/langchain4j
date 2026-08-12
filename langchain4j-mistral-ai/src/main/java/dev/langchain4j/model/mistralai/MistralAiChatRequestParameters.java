package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Objects;

/**
 * Mistral AI specific {@link ChatRequestParameters}.
 * <p>
 * In addition to the common {@link ChatRequestParameters}, this class exposes the Mistral AI specific
 * options ({@code safePrompt}, {@code randomSeed}, {@code sendThinking}, {@code returnThinking}) so that
 * they can be overridden per {@link dev.langchain4j.model.chat.request.ChatRequest}, not only at model
 * build time.
 *
 * @see MistralAiChatModel
 * @see MistralAiStreamingChatModel
 */
public class MistralAiChatRequestParameters extends DefaultChatRequestParameters {

    public static final MistralAiChatRequestParameters EMPTY =
            MistralAiChatRequestParameters.builder().build();

    private final Boolean safePrompt;
    private final Integer randomSeed;
    private final Boolean sendThinking;
    private final Boolean returnThinking;

    private MistralAiChatRequestParameters(Builder builder) {
        super(builder);
        this.safePrompt = builder.safePrompt;
        this.randomSeed = builder.randomSeed;
        this.sendThinking = builder.sendThinking;
        this.returnThinking = builder.returnThinking;
    }

    /**
     * @return whether to inject a safety prompt before all conversations (Mistral {@code safe_prompt}).
     */
    public Boolean safePrompt() {
        return safePrompt;
    }

    /**
     * @return the seed to use for random sampling (Mistral {@code random_seed}).
     */
    public Integer randomSeed() {
        return randomSeed;
    }

    /**
     * @return whether to send thinking/reasoning text to the LLM in follow-up requests.
     */
    public Boolean sendThinking() {
        return sendThinking;
    }

    /**
     * @return whether to parse and return the thinking/reasoning text from the API response
     * inside {@link dev.langchain4j.data.message.AiMessage#thinking()}.
     */
    public Boolean returnThinking() {
        return returnThinking;
    }

    @Override
    public MistralAiChatRequestParameters overrideWith(ChatRequestParameters that) {
        return MistralAiChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public MistralAiChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return MistralAiChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MistralAiChatRequestParameters that = (MistralAiChatRequestParameters) o;
        return Objects.equals(safePrompt, that.safePrompt)
                && Objects.equals(randomSeed, that.randomSeed)
                && Objects.equals(sendThinking, that.sendThinking)
                && Objects.equals(returnThinking, that.returnThinking);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), safePrompt, randomSeed, sendThinking, returnThinking);
    }

    @Override
    public String toString() {
        return "MistralAiChatRequestParameters{" + "modelName="
                + modelName() + ", temperature="
                + temperature() + ", topP="
                + topP() + ", topK="
                + topK() + ", frequencyPenalty="
                + frequencyPenalty() + ", presencePenalty="
                + presencePenalty() + ", maxOutputTokens="
                + maxOutputTokens() + ", stopSequences="
                + stopSequences() + ", toolSpecifications="
                + toolSpecifications() + ", toolChoice="
                + toolChoice() + ", responseFormat="
                + responseFormat() + ", safePrompt="
                + safePrompt + ", randomSeed="
                + randomSeed + ", sendThinking="
                + sendThinking + ", returnThinking="
                + returnThinking + '}';
    }

    public Builder toBuilder() {
        return builder().overrideWith(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Boolean safePrompt;
        private Integer randomSeed;
        private Boolean sendThinking;
        private Boolean returnThinking;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof MistralAiChatRequestParameters mistralParameters) {
                safePrompt(getOrDefault(mistralParameters.safePrompt(), safePrompt));
                randomSeed(getOrDefault(mistralParameters.randomSeed(), randomSeed));
                sendThinking(getOrDefault(mistralParameters.sendThinking(), sendThinking));
                returnThinking(getOrDefault(mistralParameters.returnThinking(), returnThinking));
            }
            return this;
        }

        public Builder modelName(MistralAiChatModelName modelName) {
            return super.modelName(modelName == null ? null : modelName.toString());
        }

        /**
         * @param safePrompt whether to inject a safety prompt before all conversations (Mistral {@code safe_prompt}).
         * @return {@code this}.
         */
        public Builder safePrompt(Boolean safePrompt) {
            this.safePrompt = safePrompt;
            return this;
        }

        /**
         * @param randomSeed the seed to use for random sampling (Mistral {@code random_seed}).
         * @return {@code this}.
         */
        public Builder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        /**
         * @param sendThinking whether to send thinking/reasoning text to the LLM in follow-up requests.
         * @return {@code this}.
         */
        public Builder sendThinking(Boolean sendThinking) {
            this.sendThinking = sendThinking;
            return this;
        }

        /**
         * @param returnThinking whether to parse and return the thinking/reasoning text from the API response.
         * @return {@code this}.
         */
        public Builder returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return this;
        }

        @Override
        public MistralAiChatRequestParameters build() {
            return new MistralAiChatRequestParameters(this);
        }
    }
}
