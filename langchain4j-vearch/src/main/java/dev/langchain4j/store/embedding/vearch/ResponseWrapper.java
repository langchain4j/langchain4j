package dev.langchain4j.store.embedding.vearch;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
class ResponseWrapper<T> {

    private Integer code;
    private String msg;
    private T data;
}
