package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonDeserialize(builder = MistralAiTool.MistralAiToolBuilder.class)
public class MistralAiTool {
    private MistralAiToolType type;
    private MistralAiFunction function;

    private MistralAiTool(MistralAiToolBuilder builder) {
        this.type = builder.type;
        this.function = builder.function;
    }

    public MistralAiToolType getType() {
        return this.type;
    }

    public MistralAiFunction getFunction() {
        return this.function;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + Objects.hashCode(this.type);
        hash = 37 * hash + Objects.hashCode(this.function);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiTool other = (MistralAiTool) obj;
        return this.type == other.type && Objects.equals(this.function, other.function);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiTool [", "]")
                .add("type=" + this.getType())
                .add("function=" + this.getFunction())
                .toString();
    }

    public static MistralAiTool from(MistralAiFunction function) {
        return MistralAiTool.builder()
                .type(MistralAiToolType.FUNCTION)
                .function(function)
                .build();
    }

    public static MistralAiToolBuilder builder() {
        return new MistralAiToolBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiToolBuilder {
        private MistralAiToolType type;
        private MistralAiFunction function;

        private MistralAiToolBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiToolBuilder type(MistralAiToolType type) {
            this.type = type;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiToolBuilder function(MistralAiFunction function) {
            this.function = function;
            return this;
        }

        public MistralAiTool build() {
            return new MistralAiTool(this);
        }
    }
}
