package dev.langchain4j.model.qianfan.client.chat;


public class Examples {

    private final Role role;
    private final String name;
    private final String content;
    private final FunctionCall function_call;

    private Examples(Builder builder) {
        this.name = builder.name;
        this.role = builder.role;
        this.content = builder.content;
        this.function_call = builder.function_call;
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

    public FunctionCall getfunction_call() {
        return function_call;
    }




    @Override
    public String toString() {
        return "Examples{" +
                "role=" + role +
                ", name='" + name + '\'' +
                ", content='" + content + '\'' +
                ", function_call=" + function_call +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Role role;
        private String name;
        private String content;
        private FunctionCall function_call;

        private Builder() {
        }

        public Builder Role(Role role) {
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

        public Builder function_call(FunctionCall function_call) {
            this.function_call = function_call;
            return this;
        }
    }
}
