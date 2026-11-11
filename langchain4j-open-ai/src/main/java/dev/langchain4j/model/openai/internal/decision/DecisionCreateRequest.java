package dev.langchain4j.model.openai.internal.decision;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.List;
import java.util.Map;

/**
 * The body of {@code POST /decisions}.
 */
@Internal
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionCreateRequest {

    public String model;

    /**
     * A {@link String}, or a {@link List} of {@link InputMessage}s.
     */
    public Object input;

    public List<Question> questions;

    public String promptCacheKey;

    @JsonIgnore
    public Map<String, Object> customParameters;

    @JsonAnyGetter
    Map<String, Object> customParameters() {
        return customParameters;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Question {

        public String type;
        public String name;
        public String instructions;
        public List<Choice> choices;
        public List<Level> levels;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {

        public String value;
        public String description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Level {

        public String label;
        public String description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputMessage {

        public String type = "message";
        public String role = "user";
        public List<InputPart> content;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputPart {

        public String type;
        public String text;
        public String imageUrl;
        public String detail;
    }
}
