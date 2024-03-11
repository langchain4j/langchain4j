package dev.langchain4j.model.zhipu.chat;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static dev.langchain4j.model.zhipu.chat.Role.SYSTEM;

@ToString
@EqualsAndHashCode
public final class SystemMessage implements Message {

    private final Role role = SYSTEM;
    @Getter
    private final String content;
    @Getter
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
