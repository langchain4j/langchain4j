package dev.langchain4j.model.ollama;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class OllamaModelCard {

    private String license;
    private String modelfile;
    private String parameters;
    private String template;
    private OllamaModelDetails details;
    private Map<String, Object> modelInfo;

    @JsonDeserialize(using = OllamaDateDeserializer.class)
    private OffsetDateTime modifiedAt;

    private List<String> capabilities;

    OllamaModelCard() {}

    public OllamaModelCard(String modelfile, String parameters, String template, OllamaModelDetails details) {
        this.modelfile = modelfile;
        this.parameters = parameters;
        this.template = template;
        this.details = details;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getModelfile() {
        return modelfile;
    }

    public void setModelfile(String modelfile) {
        this.modelfile = modelfile;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public OllamaModelDetails getDetails() {
        return details;
    }

    public void setDetails(OllamaModelDetails details) {
        this.details = details;
    }

    public Map<String, Object> getModelInfo() {
        return modelInfo;
    }

    public void setModelInfo(Map<String, Object> modelInfo) {
        this.modelInfo = modelInfo;
    }

    public OffsetDateTime getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(OffsetDateTime modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities;
    }

    public static class Builder {

        private String license;
        private String modelfile;
        private String parameters;
        private String template;
        private OllamaModelDetails details;
        private Map<String, Object> modelInfo;
        private OffsetDateTime modifiedAt;
        private List<String> capabilities;

        public Builder license(String license) {
            this.license = license;
            return this;
        }

        public Builder modelfile(String modelfile) {
            this.modelfile = modelfile;
            return this;
        }

        public Builder parameters(String parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder template(String template) {
            this.template = template;
            return this;
        }

        public Builder details(OllamaModelDetails details) {
            this.details = details;
            return this;
        }

        public Builder modelInfo(Map<String, Object> modelInfo) {
            this.modelInfo = modelInfo;
            return this;
        }

        public Builder capabilities(List<String> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder modifiedAt(OffsetDateTime modifiedAt) {
            this.modifiedAt = modifiedAt;
            return this;
        }

        public OllamaModelCard build() {
            return new OllamaModelCard(modelfile, parameters, template, details);
        }
    }
}
