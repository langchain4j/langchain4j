package dev.langchain4j.model.tei.client;

import lombok.Builder;
import lombok.ToString;

import java.util.List;

@Builder
@ToString
public class RerankRequest {

    private final String query;

    private final boolean raw_scores;

    private final boolean return_text;

    private final boolean truncate;

    private final List<String> texts;

}
