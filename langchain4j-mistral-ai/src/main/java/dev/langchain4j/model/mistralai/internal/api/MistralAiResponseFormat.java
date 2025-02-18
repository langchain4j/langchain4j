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
@JsonDeserialize(builder = MistralAiResponseFormat.MistralAiResponseFormatBuilder.class)
public class MistralAiResponseFormat {
    private Object type;

    private MistralAiResponseFormat(MistralAiResponseFormatBuilder builder) {
        this.type = builder.type;
    }

    public Object getType() {
        return this.type;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.type);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiResponseFormat other = (MistralAiResponseFormat) obj;
        return Objects.equals(this.type, other.type);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiResponseFormat [", "]")
                .add("type=" + this.getType())
                .toString();
    }

    public static MistralAiResponseFormat fromType(MistralAiResponseFormatType type) {
        return MistralAiResponseFormat.builder().type(type.toString()).build();
    }

    public static MistralAiResponseFormatBuilder builder() {
        return new MistralAiResponseFormatBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiResponseFormatBuilder {
        private Object type;

        private MistralAiResponseFormatBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiResponseFormatBuilder type(Object type) {
            this.type = type;
            return this;
        }

        public MistralAiResponseFormat build() {
            return new MistralAiResponseFormat(this);
        }
    }
}
