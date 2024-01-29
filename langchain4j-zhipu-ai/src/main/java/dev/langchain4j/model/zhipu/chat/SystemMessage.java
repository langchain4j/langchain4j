package dev.langchain4j.model.zhipu.chat;

import java.util.Objects;

import static dev.langchain4j.model.zhipu.chat.Role.SYSTEM;

public final class SystemMessage implements Message {

    private final Role role = SYSTEM;
    private final String content;
    private final String name;

    private SystemMessage(Builder builder) {
        this.content = builder.content;
        this.name = builder.name;
    }

    public static SystemMessage from(String content) {
        return SystemMessage.builder()
                .content(content)
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
        return another instanceof SystemMessage
                && equalTo((SystemMessage) another);
    }

    private boolean equalTo(SystemMessage another) {
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
        return "SystemMessage{"
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
            this.content = content;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public SystemMessage build() {
            return new SystemMessage(this);
        }
    }
}
