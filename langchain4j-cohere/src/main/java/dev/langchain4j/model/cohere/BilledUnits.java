package dev.langchain4j.model.cohere;

import lombok.Getter;

@Getter
class BilledUnits {

    private Integer inputTokens;
    private Integer outputTokens;
    private Integer searchUnits;
}
