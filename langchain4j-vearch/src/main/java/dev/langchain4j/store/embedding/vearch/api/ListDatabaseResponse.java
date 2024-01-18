package dev.langchain4j.store.embedding.vearch.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ListDatabaseResponse {

    private Integer id;
    private String name;
}
