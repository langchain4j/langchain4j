package dev.langchain4j.model.zhipu.chat;

import java.util.Objects;

import static dev.langchain4j.model.zhipu.chat.Role.USER;

public final class UserMessage implements Message {

    private final Role role = USER;
    private final String content;
    private final String name;

    private UserMessage(Builder builder) {
        this.content = builder.content;
        this.name = builder.name;
    }

    public static UserMessage from(String text) {
        return UserMessage.builder()
                .content(text)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof UserMessage
                && equalTo((UserMessage) another);
    }

    private boolean equalTo(UserMessage another) {
        return Objects.equals(role, another.role)
                && Objects.equals(content, another.content)
                && Objects.equals(name, another.name);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(role);
        h += (h << 5) + Objects.hashCode(content);
        h += (h << 5) + Objects.hashCode(name);
        return h;
    }

    @Override
    public String toString() {
        return "UserMessage{"
                + "role=" + role
                + ", content=" + content
                + ", name=" + name
                + "}";
    }

    public static final class Builder {

        private String content;
        private String name;

        private Builder() {
        }

        public Builder content(String content) {
            if (content != null) {
                this.content = content;
            }
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public UserMessage build() {
            return new UserMessage(this);
        }
    }
}