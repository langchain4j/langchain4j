package dev.langchain4j.store.embedding.vearch.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class SearchResponse {

    private Integer code;
    private String msg;
    private List<SearchedDocument> documents;

    @Getter
    @Setter
    @Builder
    public static class SearchedDocument {

        private String _id;
        private Double _score;
        private Map<String, Object> _source;
    }
}
