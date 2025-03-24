package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public abstract class JsonSchemaElement {

    @JsonProperty
    private final String type;

    protected JsonSchemaElement(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }
}
