package dev.langchain4j.model.openai.internal.models;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;

import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiModelInfo {

    @JsonProperty("id")
    private String id;

    @JsonProperty("object")
    private String object;

    @JsonProperty("created")
    private Long created;

    @JsonProperty("owned_by")
    private String ownedBy;

    public OpenAiModelInfo() {}

    public OpenAiModelInfo(String id, String object, Long created, String ownedBy) {
        this.id = id;
        this.object = object;
        this.created = created;
        this.ownedBy = ownedBy;
    }

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String object() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Long created() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public String ownedBy() {
        return ownedBy;
    }

    public void setOwnedBy(String ownedBy) {
        this.ownedBy = ownedBy;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpenAiModelInfo)) return false;
        OpenAiModelInfo that = (OpenAiModelInfo) o;
        return Objects.equals(id, that.id)
                && Objects.equals(object, that.object)
                && Objects.equals(created, that.created)
                && Objects.equals(ownedBy, that.ownedBy);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(id, object, created, ownedBy);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "OpenAiModelInfo{" + "id='"
                + id + '\'' + ", object='"
                + object + '\'' + ", created="
                + created + ", ownedBy='"
                + ownedBy + '\'' + '}';
    }
}
