package dev.langchain4j.model.qianfan.client.chat;


public class Examples {

    private final Role role;
    private final String name;
    private final String content;
    private final FunctionCall functionCall;

    private Examples(Builder builder) {
        this.name = builder.name;
        this.role = builder.role;
        this.content = builder.content;
        this.functionCall = builder.functionCall;
    }

    public Role getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public FunctionCall getFunctionCall() {
        return functionCall;
    }




    @Override
    public String toString() {
        return "Examples{" +
                "role=" + role +
                ", name='" + name + '\'' +
                ", content='" + content + '\'' +
                ", functionCall=" + functionCall +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Role role;
        private String name;
        private String content;
        private FunctionCall functionCall;

        private Builder() {
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder functionCall(FunctionCall functionCall) {
            this.functionCall = functionCall;
            return this;
        }
    }
}
