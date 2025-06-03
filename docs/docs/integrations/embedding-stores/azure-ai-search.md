---
sidebar_position: 3
---

# Azure AI Search

https://azure.microsoft.com/en-us/products/ai-services/ai-search/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-ai-search</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

## APIs

- `AzureAiSearchEmbeddingStore` - supports vector search
- `AzureAiSearchContentRetriever` - supports vector, full-text, hybrid searches and re-ranking


## Examples

- [AzureAiSearchEmbeddingStoreIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-azure-ai-search/src/test/java/dev/langchain4j/store/embedding/azure/search/AzureAiSearchEmbeddingStoreIT.java)
- [AzureAiSearchContentRetrieverIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-azure-ai-search/src/test/java/dev/langchain4j/rag/content/retriever/azure/search/AzureAiSearchContentRetrieverIT.java)
