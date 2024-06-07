package dev.langchain4j.model.sensenova.chat;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static dev.langchain4j.model.sensenova.chat.Role.USER;


@ToString
@EqualsAndHashCode
public final class UserMessage implements Message {

    private final Role role = USER;
    @Getter
    private final String content;
    @Getter
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