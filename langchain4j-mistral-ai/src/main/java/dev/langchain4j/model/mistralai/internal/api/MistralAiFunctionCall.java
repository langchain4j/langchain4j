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
@JsonDeserialize(builder = MistralAiFunctionCall.MistralAiFunctionCallBuilder.class)
public class MistralAiFunctionCall {

    private String name;
    private String arguments;

    private MistralAiFunctionCall(MistralAiFunctionCallBuilder builder) {
        this.name = builder.name;
        this.arguments = builder.arguments;
    }

    public String getName() {
        return this.name;
    }

    public String getArguments() {
        return this.arguments;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 17 * hash + Objects.hashCode(this.name);
        hash = 17 * hash + Objects.hashCode(this.arguments);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MistralAiFunctionCall other = (MistralAiFunctionCall) obj;
        return Objects.equals(this.name, other.name) && Objects.equals(this.arguments, other.arguments);
    }

    public String toString() {
        return new StringJoiner(", ", "MistralAiFunctionCall [", "]")
                .add("name=" + this.getName())
                .add("arguments=" + this.getArguments())
                .toString();
    }

    public static MistralAiFunctionCall.MistralAiFunctionCallBuilder builder() {
        return new MistralAiFunctionCall.MistralAiFunctionCallBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiFunctionCallBuilder {

        private String name;
        private String arguments;

        private MistralAiFunctionCallBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiFunctionCallBuilder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiFunctionCallBuilder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        public MistralAiFunctionCall build() {
            return new MistralAiFunctionCall(this);
        }
    }
}
