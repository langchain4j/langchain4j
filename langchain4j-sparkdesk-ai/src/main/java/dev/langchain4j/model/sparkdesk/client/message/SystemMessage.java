package dev.langchain4j.model.sparkdesk.client.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.sparkdesk.client.Role;
import lombok.Builder;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.sparkdesk.client.Role.SYSTEM;

@Data
@Builder
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SystemMessage implements Message {

    private final Role role = SYSTEM;
    private String content;

    public static SystemMessage from(String content) {
        return SystemMessage.builder()
                .content(content)
                .build();
    }
}
