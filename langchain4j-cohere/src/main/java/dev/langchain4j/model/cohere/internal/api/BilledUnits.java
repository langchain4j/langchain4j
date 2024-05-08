package dev.langchain4j.model.cohere.internal.api;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class BilledUnits {

    private Integer searchUnits;

    private Integer inputTokens;

    private Integer outputTokens;
}
