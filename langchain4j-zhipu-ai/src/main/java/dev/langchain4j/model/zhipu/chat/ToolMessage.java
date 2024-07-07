package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.zhipu.chat.Role.TOOL;

@Data
@Builder
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ToolMessage implements Message {

    private final Role role = TOOL;
    private String toolCallId;
    private String content;

    public static ToolMessage from(String toolCallId, String content) {
        return ToolMessage.builder()
                .toolCallId(toolCallId)
                .content(content)
                .build();
    }
}