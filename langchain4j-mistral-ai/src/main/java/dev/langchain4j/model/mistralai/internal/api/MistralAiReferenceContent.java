package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MistralAiReferenceContent extends MistralAiMessageContent {

    List<Integer> referenceIds;

    protected MistralAiReferenceContent() {
        super("reference");
    }

    public MistralAiReferenceContent(List<Integer> referenceIds) {
        super("reference");
        this.referenceIds = referenceIds;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MistralAiReferenceContent that = (MistralAiReferenceContent) o;
        return Objects.equals(referenceIds, that.referenceIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), referenceIds);
    }

    @Override
    public String toString() {
        return "MistralAiReferenceContent{" + "referenceIds="
                + referenceIds + ", type='"
                + type + '\'' + '}';
    }
}
