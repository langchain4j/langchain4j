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
    <version>1.19.0-beta29</version>
</dependency>
```

## APIs

- `AzureAiSearchEmbeddingStore` - supports vector search
- `AzureAiSearchContentRetriever` - supports vector, full-text, hybrid searches and re-ranking


## Metadata from a bring-your-own index

By default `AzureAiSearchContentRetriever` and `AzureAiSearchEmbeddingStore` read segment metadata from the
nested `metadata` field they write themselves. When you point them at a pre-existing index
(`createOrUpdateIndex(false)`) whose fields are stored at the document root, use `metadataFieldNames` on
either builder to list the top-level fields to expose as metadata:

```java
ContentRetriever retriever = AzureAiSearchContentRetriever.builder()
        .endpoint(endpoint)
        .tokenCredential(tokenCredential)
        .indexName(indexName)
        .createOrUpdateIndex(false)
        .queryType(AzureAiSearchQueryType.FULL_TEXT)
        .metadataFieldNames(List.of("sourcepage", "weburl", "topic", "role"))
        .maxResults(4)
        .build();
```

Each listed field is copied into the segment `Metadata` when its value is a type `Metadata` supports
(`String`, `Integer`, `Long`, `Float`, `Double`, or `UUID`). Fields that are absent, `null` or of another
type (for example the embedding vector) are skipped. A field must be marked as retrievable in the Azure
index for its value to be returned, and when a field name matches a key in the nested `metadata`, the
top-level value takes precedence.

This option only configures metadata extraction; the existing requirements for the index's core ID,
content, vector, and semantic-search fields remain unchanged.

## Examples

- [AzureAiSearchEmbeddingStoreIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-azure-ai-search/src/test/java/dev/langchain4j/store/embedding/azure/search/AzureAiSearchEmbeddingStoreIT.java)
- [AzureAiSearchContentRetrieverIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-azure-ai-search/src/test/java/dev/langchain4j/rag/content/retriever/azure/search/AzureAiSearchContentRetrieverIT.java)
