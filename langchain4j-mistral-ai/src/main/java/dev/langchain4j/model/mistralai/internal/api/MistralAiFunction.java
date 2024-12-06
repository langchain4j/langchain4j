package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiFunction {

    private String name;
    private String description;
    private MistralAiParameters parameters;

    public static class MistralAiFunctionBuilder {

        private String name;

        private String description;

        private MistralAiParameters parameters;

        MistralAiFunctionBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public MistralAiFunction.MistralAiFunctionBuilder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiFunction.MistralAiFunctionBuilder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiFunction.MistralAiFunctionBuilder parameters(MistralAiParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public MistralAiFunction build() {
            return new MistralAiFunction(this.name, this.description, this.parameters);
        }

        public String toString() {
            return "MistralAiFunction.MistralAiFunctionBuilder("
                    + "name=" + this.name
                    + ", description=" + this.description
                    + ", parameters=" + this.parameters
                    + ")";
        }
    }

    public static MistralAiFunction.MistralAiFunctionBuilder builder() {
        return new MistralAiFunction.MistralAiFunctionBuilder();
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

    public void setDescription(String description) {
        this.description = description;
    }

    public void setParameters(MistralAiParameters parameters) {
        this.parameters = parameters;
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

    public String toString() {
        return "MistralAiFunction("
                + "name=" + this.getName()
                + ", description=" + this.getDescription()
                + ", parameters=" + this.getParameters()
                + ")";
    }

    public MistralAiFunction() {
    }

    public MistralAiFunction(String name, String description, MistralAiParameters parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }
}
