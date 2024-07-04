package dev.langchain4j.model.sparkdesk.client.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.sparkdesk.client.chat.wss.WssResponsePayload;
import dev.langchain4j.model.sparkdesk.shared.DefaultResponse;
import dev.langchain4j.model.sparkdesk.shared.ResponseHeader;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageResponse implements DefaultResponse {
    private ResponseHeader header;
    private WssResponsePayload payload;
}
