package dev.langchain4j.model.dashscope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.agent.tool.ToolParameters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class QwenParameters {
    private String type = "object";
    private Map<String, Map<String, Object>> properties;
    private List<String> required;

    private static final QwenParameters EMPTY_PARAMETERS_INSTANT = QwenParameters.builder().build();

    public static QwenParameters from(ToolParameters toolParameters) {
        if (toolParameters == null) {
            return EMPTY_PARAMETERS_INSTANT;
        }

        return QwenParameters.builder()
                .properties(toolParameters.properties())
                .required(toolParameters.required())
                .build();
    }
}