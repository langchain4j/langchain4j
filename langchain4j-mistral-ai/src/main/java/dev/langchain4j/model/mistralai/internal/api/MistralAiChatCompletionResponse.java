package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiChatCompletionResponse {

    private String id;
    private String object;
    private Integer created;
    private String model;
    private List<MistralAiChatCompletionChoice> choices;
    private MistralAiUsage usage;

    public MistralAiChatCompletionResponse() {}

    public String getId() {
        return this.id;
    }

    public String getObject() {
        return this.object;
    }

    public Integer getCreated() {
        return this.created;
    }

    public String getModel() {
        return this.model;
    }

    public List<MistralAiChatCompletionChoice> getChoices() {
        return this.choices;
    }

    public MistralAiUsage getUsage() {
        return this.usage;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.id);
        hash = 97 * hash + Objects.hashCode(this.object);
        hash = 97 * hash + Objects.hashCode(this.created);
        hash = 97 * hash + Objects.hashCode(this.model);
        hash = 97 * hash + Objects.hashCode(this.choices);
        hash = 97 * hash + Objects.hashCode(this.usage);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiChatCompletionResponse other = (MistralAiChatCompletionResponse) obj;
        return Objects.equals(this.id, other.id)
                && Objects.equals(this.object, other.object)
                && Objects.equals(this.model, other.model)
                && Objects.equals(this.created, other.created)
                && Objects.equals(this.choices, other.choices)
                && Objects.equals(this.usage, other.usage);
    }

    @Override
    public String toString() {
        return "MistralAiChatCompletionResponse("
                + "id=" + this.getId()
                + ", object=" + this.getObject()
                + ", created=" + this.getCreated()
                + ", model=" + this.getModel()
                + ", choices=" + this.getChoices()
                + ", usage=" + this.getUsage()
                + ")";
    }
}
