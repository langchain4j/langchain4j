package dev.langchain4j.model.azure.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public abstract class AzureJsonSchemaElement {

    @JsonProperty
    private final String type;

    protected AzureJsonSchemaElement(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }
}
