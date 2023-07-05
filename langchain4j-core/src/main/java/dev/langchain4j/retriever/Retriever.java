package dev.langchain4j.retriever;

import java.util.List;

public interface Retriever<T> {

    List<T> findRelevant(String text);
}
