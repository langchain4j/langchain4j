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
@JsonDeserialize(builder = MistralAiFunction.MistralAiFunctionBuilder.class)
public class MistralAiFunction {
    private String name;
    private String description;
    private MistralAiParameters parameters;

    private MistralAiFunction(MistralAiFunctionBuilder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameters = builder.parameters;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public MistralAiParameters getParameters() {
        return this.parameters;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.name);
        hash = 79 * hash + Objects.hashCode(this.description);
        hash = 79 * hash + Objects.hashCode(this.parameters);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiFunction other = (MistralAiFunction) obj;
        return Objects.equals(this.name, other.name)
                && Objects.equals(this.description, other.description)
                && Objects.equals(this.parameters, other.parameters);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiFunction [", "]")
                .add("name=" + this.getName())
                .add("description=" + this.getDescription())
                .add("parameters=" + this.getParameters())
                .toString();
    }

    public static MistralAiFunction.MistralAiFunctionBuilder builder() {
        return new MistralAiFunction.MistralAiFunctionBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiFunctionBuilder {
        private String name;
        private String description;
        private MistralAiParameters parameters;

        private MistralAiFunctionBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiFunctionBuilder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiFunctionBuilder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiFunctionBuilder parameters(MistralAiParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public MistralAiFunction build() {
            return new MistralAiFunction(this);
        }
    }
}
