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
public class MistralAiResponseFormat {
    private Object type;

    public static MistralAiResponseFormat fromType(MistralAiResponseFormatType type) {
        return MistralAiResponseFormat.builder().type(type.toString()).build();
    }



    public static class MistralAiResponseFormatBuilder {
    
        private Object type;

    
        MistralAiResponseFormatBuilder() {
        }

        /**
         * @return {@code this}.
         */
    
        public MistralAiResponseFormat.MistralAiResponseFormatBuilder type(Object type) {
            this.type = type;
            return this;
        }

    
        public MistralAiResponseFormat build() {
            return new MistralAiResponseFormat(this.type);
        }

        public String toString() {
            return "MistralAiResponseFormat.MistralAiResponseFormatBuilder(type=" + this.type + ")";
        }
    }


    public static MistralAiResponseFormat.MistralAiResponseFormatBuilder builder() {
        return new MistralAiResponseFormat.MistralAiResponseFormatBuilder();
    }


    public Object getType() {
        return this.type;
    }


    public void setType(Object type) {
        this.type = type;
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

    public String toString() {
        return "MistralAiResponseFormat(type=" + this.getType() + ")";
    }


    public MistralAiResponseFormat() {
    }


    public MistralAiResponseFormat(Object type) {
        this.type = type;
    }
}
