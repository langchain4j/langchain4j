package dev.langchain4j.model.sparkdesk.client.chat.wss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.sparkdesk.shared.RequestHeader;
import dev.langchain4j.model.sparkdesk.shared.Parameter;
import dev.langchain4j.model.sparkdesk.shared.DefaultRequest;
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
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WssChatCompletionRequest implements DefaultRequest {
    private RequestHeader header;
    private Parameter parameter;
    private WssRequestPayload payload;
}
