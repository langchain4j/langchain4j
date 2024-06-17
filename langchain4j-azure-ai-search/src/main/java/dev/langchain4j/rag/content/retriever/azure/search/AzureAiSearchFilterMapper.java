package dev.langchain4j.rag.content.retriever.azure.search;

import dev.langchain4j.store.embedding.filter.Filter;

public interface AzureAiSearchFilterMapper {
    String map(Filter filter);
}
