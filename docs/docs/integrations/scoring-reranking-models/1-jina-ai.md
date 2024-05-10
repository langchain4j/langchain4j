---
sidebar_position: 1
---

# Jina.ai

- [Jina.ai Documentation](https://jina.ai/reranker)
- [Jina.ai API](https://api.jina.ai/redoc#tag/rerank)


### Introduction

A reranker is an advanced AI model that takes the initial set of results from a search—often provided by an embeddings/token-based search—and reevaluates them to ensure they align more closely with the user's intent. 
It looks beyond the surface-level matching of terms to consider the deeper interaction between the search query and the content of the documents.


### Usage

```java


ScoringModel scoringModel = JinaScoringModel.withApiKey(System.getenv("JINA_API_KEY"));;

        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                ... 
                .build();

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                ...
                .contentAggregator(contentAggregator)  //inject the contentaggregator
                .build();

        return AiServices.builder(Assistant.class)
                .chatLanguageModel(...)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(...)
                .build();


```

### Notes

Available reranking models:

- jina-reranker-v1-base-en
- jina-reranker-v1-turbo-en
- jina-reranker-v1-tiny-en

Default model used is jina-reranker-v1-base-en
