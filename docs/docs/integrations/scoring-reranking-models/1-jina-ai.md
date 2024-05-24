---
sidebar_position: 1
---

# Jina

- [Jina Reranker Documentation](https://jina.ai/reranker)
- [Jina Reranker API](https://api.jina.ai/redoc#tag/rerank)


### Introduction

A reranker is an advanced AI model that takes the initial set of results from a search—often provided by an embeddings/token-based search—and reevaluates them to ensure they align more closely with the user's intent. 
It looks beyond the surface-level matching of terms to consider the deeper interaction between the search query and the content of the documents.


### Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-jina</artifactId>
    <version>0.31.0</version>
</dependency>
```

### Usage

```java


ScoringModel scoringModel = JinaScoringModel.withApiKey(System.getenv("JINA_API_KEY"));;

ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
    .scoringModel(scoringModel)
    ... 
    .build();

RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
    ...
    .contentAggregator(contentAggregator)
    .build();

return AiServices.builder(Assistant.class)
    .chatLanguageModel(...)
    .retrievalAugmentor(retrievalAugmentor)
    .build();
```
