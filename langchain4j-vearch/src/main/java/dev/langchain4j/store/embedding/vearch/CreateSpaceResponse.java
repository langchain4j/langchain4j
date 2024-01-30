package dev.langchain4j.store.embedding.vearch;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
class CreateSpaceResponse {

    private Integer id;
    private String name;
}
