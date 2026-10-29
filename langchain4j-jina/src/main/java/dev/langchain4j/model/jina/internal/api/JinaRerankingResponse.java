package dev.langchain4j.model.jina.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JinaRerankingResponse {

    public String model;
    public List<JinaRerankingResult> results;
    public JinaUsage usage;
}
