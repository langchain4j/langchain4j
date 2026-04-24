package dev.langchain4j.model.openai.internal.models;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;

import java.util.List;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelsListResponse {

    @JsonProperty("object")
    private String object;

    @JsonProperty("data")
    private List<OpenAiModelInfo> data;

    public ModelsListResponse() {}

    public ModelsListResponse(String object, List<OpenAiModelInfo> data) {
        this.object = object;
        this.data = data;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<OpenAiModelInfo> getData() {
        return data;
    }

    public void setData(List<OpenAiModelInfo> data) {
        this.data = data;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelsListResponse)) return false;
        ModelsListResponse that = (ModelsListResponse) o;
        return Objects.equals(object, that.object) && Objects.equals(data, that.data);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(object, data);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "ModelsListResponse{" + "object='" + object + '\'' + ", data=" + data + '}';
    }
}
