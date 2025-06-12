---
sidebar_position: 17
---

# OVHcloud AI Endpoints

- [OVHclous AI Endpoints Documentation](https://labs.ovhcloud.com/en/ai-endpoints/)
- OVHcloud AI Endpoints API Reference:
  - [bge-base-en-v1.5](https://bge-base-en-v1-5.endpoints.kepler.ai.cloud.ovh.net/doc)
  - [multilingual-e5-base](https://multilingual-e5-base.endpoints.kepler.ai.cloud.ovh.net/doc)

## Project setup

### Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ovh-ai</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

### API Key setup
Add your OVHcloud AI API key to your project.

```java
public static final String OVHAI_AI_API_KEY = System.getenv("OVHAI_AI_API_KEY");
```
Don't forget set your API key as an environment variable.
```shell
export OVHAI_AI_API_KEY=your-api-key #For Unix OS based
SET OVHAI_AI_API_KEY=your-api-key #For Windows OS
```
More details on how to get your OVHcloud AI API key can be found [here](https://endpoints.ai.cloud.ovh.net/)

## Embedding
The OVHcloud AI Embeddings model allows you to embed sentences, and using it in your application is simple. We provide a simple example to get you started with OVHcloud AI Embeddings model integration.

Create a class and add the following code.

```java
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ovhai.OvhAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

public class OvhAiEmbeddingSimpleExample {

    public static void main(String[] args) {
        EmbeddingModel embeddingModel = OvhAiEmbeddingModel.builder()
                .apiKey(System.getenv("OVH_AI_API_KEY"))
                .baseUrl("https://multilingual-e5-base.endpoints.kepler.ai.cloud.ovh.net")
                .build();

        // For simplicity, this example uses an in-memory store, but you can choose any external compatible store for production environments.
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        TextSegment segment1 = TextSegment.from("I like football.");
        Embedding embedding1 = embeddingModel.embed(segment1).content();
        embeddingStore.add(embedding1, segment1);

        TextSegment segment2 = TextSegment.from("The weather is good today.");
        Embedding embedding2 = embeddingModel.embed(segment2).content();
        embeddingStore.add(embedding2, segment2);

        String userQuery = "What is your favourite sport?";
        Embedding queryEmbedding = embeddingModel.embed(userQuery).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(0);

        System.out.println("Question: " + userQuery); // What is your favourite sport?
        System.out.println("Response: " + embeddingMatch.embedded().text()); // I like football.
    }

}
```

For this example, we'll add 2 text segments, but LangChain4j offers built-in support for loading documents from various sources:
File System, URL, Amazon S3, Azure Blob Storage, GitHub, Tencent COS.
Additionally, LangChain4j supports parsing multiple document types:
text, pdf, doc, xls, ppt.

The output will be similar to this:

```plaintext
Question: What is your favourite sport?
Response: I like football.
```

Of course, you can combine OVHCloud  Embeddings with RAG (Retrieval-Augmented Generation) techniques.

In [RAG](/tutorials/rag) you will learn how to use RAG techniques for ingestion, retrieval and Advanced Retrieval with LangChain4j.

A lot of parameters are set behind the scenes, such as timeout, model type and model parameters.
In [Set Model Parameters](/tutorials/model-parameters) you will learn how to set these parameters explicitly.

### More examples
If you want to check more examples, you can find them in the [langchain4j-examples](https://github.com/langchain4j/langchain4j-examples) project.
