package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import dev.langchain4j.store.embedding.filter.Filter;

public interface AzureCosmosDBNoSqlFilterMapper {
    String map(Filter filter);
}
