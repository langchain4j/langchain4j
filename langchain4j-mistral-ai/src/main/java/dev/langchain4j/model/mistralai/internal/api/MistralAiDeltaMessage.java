package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiDeltaMessage {

    private MistralAiRole role;
    private String content;
    private List<MistralAiToolCall> toolCalls;

    public static class MistralAiDeltaMessageBuilder {

        private MistralAiRole role;

        private String content;

        private List<MistralAiToolCall> toolCalls;

        MistralAiDeltaMessageBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public MistralAiDeltaMessage.MistralAiDeltaMessageBuilder role(MistralAiRole role) {
            this.role = role;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiDeltaMessage.MistralAiDeltaMessageBuilder content(String content) {
            this.content = content;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiDeltaMessage.MistralAiDeltaMessageBuilder toolCalls(List<MistralAiToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public MistralAiDeltaMessage build() {
            return new MistralAiDeltaMessage(this.role, this.content, this.toolCalls);
        }

        public String toString() {
            return "MistralAiDeltaMessage.MistralAiDeltaMessageBuilder("
                    + "role=" + this.role
                    + ", content=" + this.content
                    + ", toolCalls="
                    + this.toolCalls
                    + ")";
        }
    }

    public static MistralAiDeltaMessage.MistralAiDeltaMessageBuilder builder() {
        return new MistralAiDeltaMessage.MistralAiDeltaMessageBuilder();
    }

    public String toString() {
        return "MistralAiDeltaMessage("
                + "role=" + this.getRole()
                + ", content=" + this.getContent()
                + ", toolCalls=" + this.getToolCalls()
                + ")";
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 23 * hash + Objects.hashCode(this.role);
        hash = 23 * hash + Objects.hashCode(this.content);
        hash = 23 * hash + Objects.hashCode(this.toolCalls);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiDeltaMessage other = (MistralAiDeltaMessage) obj;
        return Objects.equals(this.content, other.content)
                && this.role == other.role
                && Objects.equals(this.toolCalls, other.toolCalls);
    }


    public MistralAiDeltaMessage() {
    }

    public MistralAiDeltaMessage(MistralAiRole role, String content, List<MistralAiToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
    }

    public MistralAiRole getRole() {
        return this.role;
    }

    public String getContent() {
        return this.content;
    }

    public List<MistralAiToolCall> getToolCalls() {
        return this.toolCalls;
    }

    public void setRole(MistralAiRole role) {
        this.role = role;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setToolCalls(List<MistralAiToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }
}
