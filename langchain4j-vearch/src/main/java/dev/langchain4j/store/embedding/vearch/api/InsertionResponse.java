package dev.langchain4j.store.embedding.vearch.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class InsertionResponse {

    private Integer code;
    private String msg;
    private Integer total;
    private List<InsertedDocument> documentIds;

    @Getter
    @Setter
    @Builder
    public static class InsertedDocument {

        private String _id;
        private Integer status;
        private String error;
    }
}
