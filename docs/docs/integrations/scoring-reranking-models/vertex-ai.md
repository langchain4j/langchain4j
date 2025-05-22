---
sidebar_position: 4
---

# Google Cloud Vertex AI Ranking API

- [Google Cloud Vertex AI Ranking documentation](https://cloud.google.com/generative-ai-app-builder/docs/ranking)
- [Google Cloud Vertex AI Ranking API description](https://cloud.google.com/generative-ai-app-builder/docs/reference/rest/v1/projects.locations.rankingConfigs/rank)


### Introduction

The Google Cloud Vertex AI Ranking API is a powerful tool that enhances search results by refining the relevance of
retrieved documents to a given query. Unlike traditional search methods, it leverages advanced machine learning 
algorithms to understand the semantic context of both the query and the documents, delivering more precise and relevant 
results. By analyzing the semantic relationship between the query and each document, the API can reorder the candidate 
documents based on their calculated relevance scores, ensuring that the most relevant results appear at the top of the 
search results page.

### Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-vertex-ai</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

### Usage

To configure the model, you'll have to specify:
* the Google Cloud project ID, 
* the project number, 
* the location (ex. `us-central1`, `europe-west1`), 
* and the model you want to use.

> Note: You can find the project number in the Google Cloud console, or by running `gcloud projects describe your-project-id`.

You can score a single string or `TextSegment` against a query 
thanks to the `score(text, query)` and `score(segment, query)` methods.

It is also possible to score several strings or `TextSegment`s against the query, 
with the `scoreAll(segments, query)` method:

```java
VertexAiScoringModel scoringModel = VertexAiScoringModel.builder()
    .projectId(System.getenv("GCP_PROJECT_ID"))
    .projectNumber(System.getenv("GCP_PROJECT_NUM"))
    .projectLocation(System.getenv("GCP_LOCATION"))
    .model("semantic-ranker-512")
    .build();

Response<List<Double>> score = scoringModel.scoreAll(Stream.of(
        "The sky appears blue due to a phenomenon called Rayleigh scattering. " +
            "Sunlight is comprised of all the colors of the rainbow. Blue light has shorter " +
            "wavelengths than other colors, and is thus scattered more easily.",

        "A canvas stretched across the day,\n" +
            "Where sunlight learns to dance and play.\n" +
            "Blue, a hue of scattered light,\n" +
            "A gentle whisper, soft and bright."
        ).map(TextSegment::from).collect(Collectors.toList()),
    "Why is the sky blue?");

// [0.8199999928474426, 0.4300000071525574]
```

If you pass `TextSegment`s which have a particular `title` key, the Ranker model can take this metadata into account in its calculation.
To specify a custom title key, you can use the `titleMetadataKey()` builder method.`

You can use scoring models with `AiServices` and its `contentAgregator()` method, 
which takes a `ContentAggregator` class that can specify a scoring model:

```java
VertexAiScoringModel scoringModel = VertexAiScoringModel.builder()
    .projectId(System.getenv("GCP_PROJECT_ID"))
    .projectNumber(System.getenv("GCP_PROJECT_NUM"))
    .projectLocation(System.getenv("GCP_LOCATION"))
    .model("semantic-ranker-512")
    .build();

ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
    .scoringModel(scoringModel)
    ... 
    .build();

RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
    ...
    .contentAggregator(contentAggregator)
    .build();

return AiServices.builder(Assistant.class)
    .chatModel(...)
    .retrievalAugmentor(retrievalAugmentor)
    .build();
```
