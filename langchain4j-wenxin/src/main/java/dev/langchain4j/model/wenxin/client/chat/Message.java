package dev.langchain4j.model.wenxin.client.chat;

import java.util.Objects;

public final class Message {
    private final Role role;
    private final String content;
    private final String name;
    private final FunctionCall function_call;

    private Message(Builder builder) {
        this.role = builder.role;
        this.content = builder.content;
        this.name = builder.name;
        this.function_call = builder.function_call;
    }

    public Role role() {
        return this.role;
    }

    public String content() {
        return this.content;
    }

    public String name() {
        return this.name;
    }

    public FunctionCall functionCall() {
        return this.function_call;
    }

    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof Message && this.equalTo((Message)another);
        }
    }

    private boolean equalTo(Message another) {
        return Objects.equals(this.role, another.role) && Objects.equals(this.content, another.content) && Objects.equals(this.name, another.name) && Objects.equals(this.function_call, another.function_call);
    }

    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.role);
        h += (h << 5) + Objects.hashCode(this.content);
        h += (h << 5) + Objects.hashCode(this.name);
        h += (h << 5) + Objects.hashCode(this.function_call);
        return h;
    }

    public String toString() {
        return "Message{role=" + this.role + ", content=" + this.content + ", name=" + this.name + ", function_call=" + this.function_call + "}";
    }

    public static Message systemMessage(String content) {
        return builder().role(Role.SYSTEM).content(content).build();
    }

    public static Message userMessage(String content) {
        return builder().role(Role.USER).content(content).build();
    }

    public static Message assistantMessage(String content) {
        return builder().role(Role.ASSISTANT).content(content).build();
    }

    public static Message functionMessage(String name, String content) {
        return builder().role(Role.FUNCTION).name(name).content(content).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Role role;
        private String content;
        private String name;
        private FunctionCall function_call;

        private Builder() {
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder role(String role) {
            return this.role(Role.from(role));
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder functionCall(FunctionCall functionCall) {
            this.function_call = functionCall;
            return this;
        }

        public Message build() {
            return new Message(this);
        }
    }
}

