package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiChatMessage {

    private MistralAiRole role;
    private String content;
    private String name;
    private List<MistralAiToolCall> toolCalls;
    private String toolCallId;

    public MistralAiChatMessage() {}

    public MistralAiChatMessage(MistralAiChatMessageBuilder builder) {
        this.role = builder.role;
        this.content = builder.content;
        this.name = builder.name;
        this.toolCalls = builder.toolCalls;
        this.toolCallId = builder.toolCallId;
    }

    public MistralAiRole getRole() {
        return this.role;
    }

    public String getContent() {
        return this.content;
    }

    public String getName() {
        return this.name;
    }

    public String getToolCallId() {
        return this.toolCallId;
    }

    public List<MistralAiToolCall> getToolCalls() {
        return this.toolCalls;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.role);
        hash = 97 * hash + Objects.hashCode(this.content);
        hash = 97 * hash + Objects.hashCode(this.name);
        hash = 97 * hash + Objects.hashCode(this.toolCallId);
        hash = 97 * hash + Objects.hashCode(this.toolCalls);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MistralAiChatMessage other = (MistralAiChatMessage) obj;
        return Objects.equals(this.content, other.content)
                && Objects.equals(this.name, other.name)
                && this.role == other.role
                && Objects.equals(this.toolCallId, other.toolCallId)
                && Objects.equals(this.toolCalls, other.toolCalls);
    }

    @Override
    public String toString() {
        return "MistralAiChatMessage("
                + "role=" + this.getRole()
                + ", content=" + this.getContent()
                + ", name=" + this.getName()
                + ", toolCallId=" + this.getToolCallId()
                + ", toolCalls=" + this.getToolCalls()
                + ")";
    }

    public static MistralAiChatMessageBuilder builder() {
        return new MistralAiChatMessageBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiChatMessageBuilder {

        private MistralAiRole role;
        private String content;
        private String name;
        private String toolCallId;
        private List<MistralAiToolCall> toolCalls;

        private MistralAiChatMessageBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiChatMessage.MistralAiChatMessageBuilder role(MistralAiRole role) {
            this.role = role;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatMessage.MistralAiChatMessageBuilder content(String content) {
            this.content = content;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatMessage.MistralAiChatMessageBuilder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatMessage.MistralAiChatMessageBuilder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatMessage.MistralAiChatMessageBuilder toolCalls(List<MistralAiToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public MistralAiChatMessage build() {
            return new MistralAiChatMessage(this);
        }

        @Override
        public String toString() {
            return "MistralAiChatMessage.MistralAiChatMessageBuilder("
                    + "role=" + this.role
                    + ", content=" + this.content
                    + ", name=" + this.name
                    + ", toolCallId=" + this.toolCallId
                    + ", toolCalls="
                    + this.toolCalls
                    + ")";
        }
    }
}
