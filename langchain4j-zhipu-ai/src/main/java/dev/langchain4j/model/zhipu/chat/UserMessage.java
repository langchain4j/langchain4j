package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.internal.Utils;

import java.util.Collections;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.zhipu.chat.Role.USER;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class UserMessage implements Message {

    private Role role = USER;
    private Object content;
    private String name;

    public UserMessage(Role role, Object content, String name) {
        this.role = Utils.getOrDefault(role, USER);
        this.content = content;
        this.name = name;
    }

    public static UserMessageBuilder builder() {
        return new UserMessageBuilder();
    }

    public static UserMessage from(String text) {
        return UserMessage.builder()
                .content(text)
                .build();
    }

    public static UserMessage from(List<Content> contents) {
        return UserMessage.builder()
                .content(contents)
                .build();
    }

    public static UserMessage from(Content... contents) {
        return UserMessage.builder()
                .content(Collections.singletonList(contents))
                .build();
    }

    @Override
    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static class UserMessageBuilder {
        private Role role;
        private Object content;
        private String name;

        UserMessageBuilder() {
        }

        public UserMessageBuilder role(Role role) {
            this.role = role;
            return this;
        }

        public UserMessageBuilder content(Object content) {
            this.content = content;
            return this;
        }

        public UserMessageBuilder name(String name) {
            this.name = name;
            return this;
        }

        public UserMessage build() {
            return new UserMessage(this.role, this.content, this.name);
        }
    }
}