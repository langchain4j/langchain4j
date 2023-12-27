package dev.langchain4j.retriever;

import java.util.List;

public interface Retriever<T> {

    List<T> findRelevant(String text);

    default List<T> findRelevant(Object memoryId,String text){
        throw new RuntimeException("Not implemented");
    }
}
