package dev.langchain4j.model.zhipu.chat;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
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
