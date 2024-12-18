package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiToolCall {
    private String id;
    private MistralAiToolType type;
    private MistralAiFunctionCall function;

    private static MistralAiToolType $default$type() {
        return MistralAiToolType.FUNCTION;
    }

    public MistralAiToolCall() {
        this.type = MistralAiToolCall.$default$type();
    }

    public MistralAiToolCall(MistralAiToolCallBuilder builder) {
        this.id = builder.id;
        this.type = builder.type$value;
        this.function = builder.function;
    }

    public String getId() {
        return this.id;
    }

    public MistralAiToolType getType() {
        return this.type;
    }

    public MistralAiFunctionCall getFunction() {
        return this.function;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setType(MistralAiToolType type) {
        this.type = type;
    }

    public void setFunction(MistralAiFunctionCall function) {
        this.function = function;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.id);
        hash = 29 * hash + Objects.hashCode(this.type);
        hash = 29 * hash + Objects.hashCode(this.function);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiToolCall other = (MistralAiToolCall) obj;
        return Objects.equals(this.id, other.id)
                && this.type == other.type
                && Objects.equals(this.function, other.function);
    }

    @Override
    public String toString() {
        return "MistralAiToolCall("
                + "id=" + this.getId()
                + ", type=" + this.getType()
                + ", function=" + this.getFunction()
                + ")";
    }

    public static MistralAiToolCallBuilder builder() {
        return new MistralAiToolCallBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiToolCallBuilder {
        private String id;
        private boolean type$set;
        private MistralAiToolType type$value;
        private MistralAiFunctionCall function;

        private MistralAiToolCallBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiToolCallBuilder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiToolCallBuilder type(MistralAiToolType type) {
            this.type$value = type;
            type$set = true;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiToolCallBuilder function(MistralAiFunctionCall function) {
            this.function = function;
            return this;
        }

        public MistralAiToolCall build() {
            MistralAiToolType type$value = this.type$value;
            if (!this.type$set) {
                this.type$value = MistralAiToolCall.$default$type();
            }
            return new MistralAiToolCall(this);
        }

        @Override
        public String toString() {
            return "MistralAiToolCall.MistralAiToolCallBuilder("
                    + "id=" + this.id
                    + ", type$value=" + this.type$value
                    + ", function=" + this.function
                    + ")";
        }
    }
}
