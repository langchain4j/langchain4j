package dev.langchain4j.model.sparkdesk.client.chat.wss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.sparkdesk.shared.RequestPayload;
import dev.langchain4j.model.sparkdesk.client.chat.wss.function.Functions;
import dev.langchain4j.model.sparkdesk.shared.RequestMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WssRequestPayload implements RequestPayload {
    private RequestMessage message;
    private Functions functions;
}
