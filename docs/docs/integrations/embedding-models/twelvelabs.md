---
sidebar_position: 24
---

# TwelveLabs

[TwelveLabs](https://www.twelvelabs.io/) provides multimodal foundation models for video understanding.
Its [Marengo](https://docs.twelvelabs.io/) model produces embeddings in a shared vector space for text, image,
audio, and video, which makes it well suited for semantic video search and multimodal retrieval.

LangChain4j integrates with TwelveLabs through the `TwelveLabsEmbeddingModel`, which exposes Marengo's text
embedding capability through the standard LangChain4j `EmbeddingModel` contract.

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-twelvelabs</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Or, you can use BOM to manage dependencies consistently:

```xml

<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-bom</artifactId>
        <version>${latest version here}</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```

## Configurable Parameters

`TwelveLabsEmbeddingModel` has the following parameters to configure when you initialize it:

| Property   | Description                                                            | Default Value                     |
|------------|-----------------------------------------------------------------------|-----------------------------------|
| baseUrl    | The base URL of the TwelveLabs API.                                   | https://api.twelvelabs.io/v1.3/   |
| apiKey     | The TwelveLabs API key (required).                                    |                                   |
| modelName  | The Marengo embedding model to use (required), e.g. `marengo3.0`.     |                                   |
| timeout    | The HTTP request timeout.                                             | 60 seconds                        |
| maxRetries | The maximum number of retries on transient failures.                  |                                   |
| logRequests  | Whether to log requests.                                            | false                             |
| logResponses | Whether to log responses.                                           | false                             |

## Creating a `TwelveLabsEmbeddingModel`

```java
EmbeddingModel embeddingModel = TwelveLabsEmbeddingModel.builder()
        .apiKey(System.getenv("TWELVELABS_API_KEY"))
        .modelName("marengo3.0")
        .build();

Response<Embedding> response = embeddingModel.embed("Welcome to LangChain4j!");
Embedding embedding = response.content();
```

`marengo3.0` returns 512-dimensional embeddings. The model implements `DimensionAwareEmbeddingModel`, so
`embeddingModel.dimension()` reports the known dimension for the configured model.

## Examples

- [TwelveLabsEmbeddingModelIT](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-twelvelabs/src/test/java/dev/langchain4j/community/model/twelvelabs/TwelveLabsEmbeddingModelIT.java)
