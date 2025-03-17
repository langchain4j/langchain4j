package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.internal.Utils;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.zhipu.chat.Role.SYSTEM;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SystemMessage implements Message {

    private Role role;
    private String content;
    private String name;

    public SystemMessage(Role role, String content, String name) {
        this.role = Utils.getOrDefault(role, SYSTEM);
        this.content = content;
        this.name = name;
    }

    public static SystemMessage from(String content) {
        return SystemMessage.builder()
                .content(content)
                .build();
    }

    public static SystemMessageBuilder builder() {
        return new SystemMessageBuilder();
    }

    @Override
    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static class SystemMessageBuilder {
        private Role role;
        private String content;
        private String name;

        SystemMessageBuilder() {
        }

        public SystemMessageBuilder role(Role role) {
            this.role = role;
            return this;
        }

        public SystemMessageBuilder content(String content) {
            this.content = content;
            return this;
        }

        public SystemMessageBuilder name(String name) {
            this.name = name;
            return this;
        }

        public SystemMessage build() {
            return new SystemMessage(this.role, this.content, this.name);
        }
    }
}
