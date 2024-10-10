package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class OllamaModelDetails {

    private String format;
    private String family;
    private List<String> families;
    private String parameterSize;
    private String quantizationLevel;

    OllamaModelDetails() {
    }

    public OllamaModelDetails(String format, String family, List<String> families, String parameterSize, String quantizationLevel) {
        this.format = format;
        this.family = family;
        this.families = families;
        this.parameterSize = parameterSize;
        this.quantizationLevel = quantizationLevel;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public List<String> getFamilies() {
        return families;
    }

    public void setFamilies(List<String> families) {
        this.families = families;
    }

    public String getParameterSize() {
        return parameterSize;
    }

    public void setParameterSize(String parameterSize) {
        this.parameterSize = parameterSize;
    }

    public String getQuantizationLevel() {
        return quantizationLevel;
    }

    public void setQuantizationLevel(String quantizationLevel) {
        this.quantizationLevel = quantizationLevel;
    }

    public static class Builder {

        private String format;
        private String family;
        private List<String> families;
        private String parameterSize;
        private String quantizationLevel;

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder family(String family) {
            this.family = family;
            return this;
        }

        public Builder families(List<String> families) {
            this.families = families;
            return this;
        }

        public Builder parameterSize(String parameterSize) {
            this.parameterSize = parameterSize;
            return this;
        }

        public Builder quantizationLevel(String quantizationLevel) {
            this.quantizationLevel = quantizationLevel;
            return this;
        }

        public OllamaModelDetails build() {
            return new OllamaModelDetails(format, family, families, parameterSize, quantizationLevel);
        }
    }
}
