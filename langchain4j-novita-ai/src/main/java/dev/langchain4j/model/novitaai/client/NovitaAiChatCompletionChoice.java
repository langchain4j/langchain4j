package dev.langchain4j.model.novitaai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.model.novitaai.client.NovitaAiChatCompletionRequest.Message;

import java.util.Objects;
import java.util.StringJoiner;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonDeserialize(builder = NovitaAiChatCompletionChoice.NovitaAiChatCompletionChoiceBuilder.class)
public class NovitaAiChatCompletionChoice {

    private Integer index;
    private Message message;
    private String finishReason;
    private NovitaAiUsage usage; // usageInfo is returned only when the prompt is finished in stream mode

    private NovitaAiChatCompletionChoice(NovitaAiChatCompletionChoiceBuilder builder) {
        this.index = builder.index;
        this.message = builder.message;
        this.finishReason = builder.finishReason;
        this.usage = builder.usage;
    }

    public Integer getIndex() {
        return this.index;
    }

    public Message getMessage() {
        return this.message;
    }

    public String getFinishReason() {
        return this.finishReason;
    }

    public NovitaAiUsage getUsage() {
        return this.usage;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final NovitaAiChatCompletionChoice other = (NovitaAiChatCompletionChoice) obj;
        return Objects.equals(this.finishReason, other.finishReason)
                && Objects.equals(this.index, other.index)
                && Objects.equals(this.message, other.message)
                && Objects.equals(this.usage, other.usage);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.index);
        hash = 59 * hash + Objects.hashCode(this.message);
        hash = 59 * hash + Objects.hashCode(this.finishReason);
        hash = 59 * hash + Objects.hashCode(this.usage);
        return hash;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "NovitaAiChatCompletionChoice [", "]")
                .add("index=" + this.getIndex())
                .add("message=" + this.getMessage() == null ? "" : "**********")
                .add("finishReason=" + this.getFinishReason())
                .add("usage=" + this.getUsage())
                .toString();
    }

    public static NovitaAiChatCompletionChoiceBuilder builder() {
        return new NovitaAiChatCompletionChoiceBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class NovitaAiChatCompletionChoiceBuilder {

        private Integer index;
        private Message message;
        private String finishReason;
        private NovitaAiUsage usage;

        private NovitaAiChatCompletionChoiceBuilder() {}

        public NovitaAiChatCompletionChoiceBuilder index(Integer index) {
            this.index = index;
            return this;
        }

        public NovitaAiChatCompletionChoiceBuilder message(Message message) {
            this.message = message;
            return this;
        }

        public NovitaAiChatCompletionChoiceBuilder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public NovitaAiChatCompletionChoiceBuilder usage(NovitaAiUsage usage) {
            this.usage = usage;
            return this;
        }

        public NovitaAiChatCompletionChoice build() {
            return new NovitaAiChatCompletionChoice(this);
        }
    }
}
