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
public class MistralAiChatMessage {

    private MistralAiRole role;
    private String content;
    private String name;
    private List<MistralAiToolCall> toolCalls;

    public static class MistralAiChatMessageBuilder {

        private MistralAiRole role;

        private String content;

        private String name;

        private List<MistralAiToolCall> toolCalls;

        MistralAiChatMessageBuilder() {
        }

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
        public MistralAiChatMessage.MistralAiChatMessageBuilder toolCalls(List<MistralAiToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public MistralAiChatMessage build() {
            return new MistralAiChatMessage(this.role, this.content, this.name, this.toolCalls);
        }

        public String toString() {
            return "MistralAiChatMessage.MistralAiChatMessageBuilder("
                    + "role=" + this.role
                    + ", content=" + this.content
                    + ", name=" + this.name
                    + ", toolCalls="
                    + this.toolCalls
                    + ")";
        }
    }

    public static MistralAiChatMessage.MistralAiChatMessageBuilder builder() {
        return new MistralAiChatMessage.MistralAiChatMessageBuilder();
    }

    public String toString() {
        return "MistralAiChatMessage("
                + "role=" + this.getRole()
                + ", content=" + this.getContent()
                + ", name=" + this.getName()
                + ", toolCalls=" + this.getToolCalls()
                + ")";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.role);
        hash = 97 * hash + Objects.hashCode(this.content);
        hash = 97 * hash + Objects.hashCode(this.name);
        hash = 97 * hash + Objects.hashCode(this.toolCalls);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiChatMessage other = (MistralAiChatMessage) obj;
        return Objects.equals(this.content, other.content)
                && Objects.equals(this.name, other.name)
                && this.role == other.role
                && Objects.equals(this.toolCalls, other.toolCalls);
    }

    public MistralAiChatMessage() {
    }

    public MistralAiChatMessage(MistralAiRole role, String content, String name, List<MistralAiToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.name = name;
        this.toolCalls = toolCalls;
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

    public List<MistralAiToolCall> getToolCalls() {
        return this.toolCalls;
    }

    public void setRole(MistralAiRole role) {
        this.role = role;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setToolCalls(List<MistralAiToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }
}
