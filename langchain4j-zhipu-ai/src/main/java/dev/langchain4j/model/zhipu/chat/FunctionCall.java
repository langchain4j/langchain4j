package dev.langchain4j.model.zhipu.chat;

import java.util.Objects;

public final class FunctionCall {

    private final String name;
    private final String arguments;

    private FunctionCall(Builder builder) {
        this.name = builder.name;
        this.arguments = builder.arguments;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getArguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof FunctionCall && this.equalTo((FunctionCall) another);
        }
    }

    private boolean equalTo(FunctionCall another) {
        return Objects.equals(this.name, another.name)
                && Objects.equals(this.arguments, another.arguments);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.name);
        h += (h << 5) + Objects.hashCode(this.arguments);
        return h;
    }

    @Override
    public String toString() {
        return "FunctionCall{"
                + "name=" + this.name
                + ", arguments=" + this.arguments
                + "}";
    }

    public static final class Builder {
        private String name;
        private String arguments;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        public FunctionCall build() {
            return new FunctionCall(this);
        }
    }
}
