package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiParameters {
    private String type;
    private Map<String, Map<String, Object>> properties;
    private List<String> required;


    private static String $default$type() {
        return "object";
    }



    public static class MistralAiParametersBuilder {
    
        private boolean type$set;
    
        private String type$value;
    
        private Map<String, Map<String, Object>> properties;
    
        private List<String> required;

    
        MistralAiParametersBuilder() {
        }

        /**
         * @return {@code this}.
         */
    
        public MistralAiParameters.MistralAiParametersBuilder type(String type) {
            this.type$value = type;
            type$set = true;
            return this;
        }

        /**
         * @return {@code this}.
         */
    
        public MistralAiParameters.MistralAiParametersBuilder properties(Map<String, Map<String, Object>> properties) {
            this.properties = properties;
            return this;
        }

        /**
         * @return {@code this}.
         */
    
        public MistralAiParameters.MistralAiParametersBuilder required(List<String> required) {
            this.required = required;
            return this;
        }

    
        public MistralAiParameters build() {
            String type$value = this.type$value;
            if (!this.type$set) type$value = MistralAiParameters.$default$type();
            return new MistralAiParameters(type$value, this.properties, this.required);
        }

        public String toString() {
            return "MistralAiParameters.MistralAiParametersBuilder("
                    + "type$value=" + this.type$value +
                    ", properties=" + this.properties +
                    ", required=" + this.required +
                    ")";
        }
    }


    public static MistralAiParameters.MistralAiParametersBuilder builder() {
        return new MistralAiParameters.MistralAiParametersBuilder();
    }


    public String getType() {
        return this.type;
    }


    public Map<String, Map<String, Object>> getProperties() {
        return this.properties;
    }


    public List<String> getRequired() {
        return this.required;
    }


    public void setType(String type) {
        this.type = type;
    }


    public void setProperties(Map<String, Map<String, Object>> properties) {
        this.properties = properties;
    }


    public void setRequired(List<String> required) {
        this.required = required;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(this.type);
        hash = 31 * hash + Objects.hashCode(this.properties);
        hash = 31 * hash + Objects.hashCode(this.required);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiParameters other = (MistralAiParameters) obj;
        return Objects.equals(this.type, other.type)
                && Objects.equals(this.properties, other.properties)
                && Objects.equals(this.required, other.required);
    }

    public String toString() {
        return "MistralAiParameters("
                + "type=" + this.getType()
                + ", properties=" + this.getProperties()
                + ", required=" + this.getRequired()
                + ")";
    }


    public MistralAiParameters() {
        this.type = MistralAiParameters.$default$type();
    }


    public MistralAiParameters(String type, Map<String, Map<String, Object>> properties, List<String> required) {
        this.type = type;
        this.properties = properties;
        this.required = required;
    }
}
