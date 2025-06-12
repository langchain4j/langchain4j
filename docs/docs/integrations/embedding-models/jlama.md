---
sidebar_position: 10
---

# Jlama
[Jlama Project](https://github.com/tjake/Jlama)

### Project setup

To install langchain4j to your project, add the following dependency:

For Maven project `pom.xml`

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.0.1</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-jlama</artifactId>
    <version>1.0.1-beta6</version>
</dependency>

<dependency>
    <groupId>com.github.tjake</groupId>
    <artifactId>jlama-native</artifactId>
    <!-- for faster inference. supports linux-x86_64, macos-x86_64/aarch_64, windows-x86_64 
        Use https://github.com/trustin/os-maven-plugin to detect os and arch -->
    <classifier>${os.detected.name}-${os.detected.arch}</classifier>
    <version>${jlama.version}</version> <!-- Version from langchain4j-jlama pom -->
</dependency>
```

For Gradle project `build.gradle`

```groovy
implementation 'dev.langchain4j:langchain4j:1.0.1-beta6'
implementation 'dev.langchain4j:langchain4j-jlama:1.0.1-beta6'
```

## Embedding
The Jlama Embeddings model allows you to embed sentences, and using it in your application is simple. 
We provide a simple example to get you started with Jlama Embeddings model integration.
You can use any `bert` based model from [HuggingFace](https://huggingface.co/models?library=safetensors&sort=trending), and specify them using the `owner/model-name` format.

Create a class and add the following code.

```java
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.jlama.JlamaEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

public class HelloWorld {
    public static void main(String[] args) {
        EmbeddingModel embeddingModel = JlamaEmbeddingModel
                                        .modelName("intfloat/e5-small-v2")
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

Of course, you can combine Jlama Embeddings with RAG (Retrieval-Augmented Generation) techniques.

In [RAG](/tutorials/rag) you will learn how to use RAG techniques for ingestion, retrieval and Advanced Retrieval with LangChain4j.

A lot of parameters are set behind the scenes, such as timeout, model type and model parameters.
In [Set Model Parameters](/tutorials/model-parameters) you will learn how to set these parameters explicitly.

### More examples
If you want to check more examples, you can find them in the [langchain4j-examples](https://github.com/langchain4j/langchain4j-examples) project.
