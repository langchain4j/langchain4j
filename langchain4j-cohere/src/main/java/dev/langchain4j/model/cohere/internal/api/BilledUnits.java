package dev.langchain4j.model.cohere.internal.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BilledUnits {

    private Integer searchUnits;

    private Integer inputTokens;

    private Integer outputTokens;
}
