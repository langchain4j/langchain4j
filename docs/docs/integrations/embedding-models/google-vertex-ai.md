---
sidebar_position: 7
---

# Google Vertex AI

## Get started

To get started follow the steps outlined in the `Get started` section of [Vertex AI Gemini integration tutorial](../language-models/google-vertex-ai-gemini) to create a
Google Cloud Platform account and establish a new project with access to Vertex AI API.

## Add dependencies

Add the following dependencies to your project's `pom.xml`:

```xml
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-vertex-ai</artifactId>
  <version>1.0.1-beta6</version>
</dependency>
```

or project's `build.gradle`:

```groovy
implementation 'dev.langchain4j:langchain4j-vertex-ai:1.0.1-beta6'
```

### Try out an example code:

[An Example of using Vertex AI Embedding Model](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/model/VertexAiEmbeddingModelExample.java)

The `PROJECT_ID` field represents the variable you set when creating a new Google Cloud project.

```java
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;

public class VertexAiEmbeddingModelExample {
    
    private static final String PROJECT_ID = "YOUR-PROJECT-ID";
    private static final String MODEL_NAME = "textembedding-gecko@latest";

    public static void main(String[] args) {

        EmbeddingModel embeddingModel = VertexAiEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location("us-central1")
                .endpoint("us-central1-aiplatform.googleapis.com:443")
                .publisher("google")
                .modelName(MODEL_NAME)
                .build();

        Response<Embedding> response = embeddingModel.embed("Hello, how are you?");
        
        Embedding embedding = response.content();

        int dimension = embedding.dimension(); // 768
        float[] vector = embedding.vector(); // [-0.06050122, -0.046411075, ...

        System.out.println(dimension);
        System.out.println(embedding.vectorAsList());
    }
}
```

### Available Embedding models

|English models|Multilingual models|
|---|---|
|`textembedding-gecko@001`|`textembedding-gecko-multilingual@001`|
|`textembedding-gecko@003`|`text-multilingual-embedding-002`|
|`text-embedding-004`|   |

[List of supported languages for multi lingual model](https://cloud.google.com/vertex-ai/generative-ai/docs/embeddings/get-text-embeddings#language_coverage_for_textembedding-gecko-multilingual_models)

Model names suffixed with `@latest` reference the most recent version of the model.

By default, most embedding models output 768-dimensional vector embeddings (except for "Matryoshka" models that accept a configurable lower dimension).
The API accepts a maximum of 2,048 input tokens per segment to embed.
You can send upto 250 text segments.
The `VertexAiEmbeddingModel` class automatically and transparently splits the requests in batches when you ask for more than 250 segments to be embedded at the same time.
The embedding API is limited to a total of 20,000 tokens per call (across all segments). When that limit is reached, `VertexAiEmbeddingModel` will again batch the requests to avoid hitting that limit.

### Configuring the embedding model

```java
EmbeddingModel embeddingModel = VertexAiEmbeddingModel.builder()
    .project(PROJECT_ID)
    .location("us-central1")
    .endpoint("us-central1-aiplatform.googleapis.com:443") // optional
    .publisher("google")
    .modelName(MODEL_NAME)
    .maxRetries(2)             // 2 by default
    .maxSegmentsPerBatch(250)  // up to 250 segments per batch
    .maxTokensPerBatch(2048)   // up to 2048 tokens per segment
    .taskType()                // see below for the different task types
    .titleMetadataKey()        // for the RETRIEVAL_DOCUMENT task, you can specify a title  
                               // for the text segment to identify its document origin
    .autoTruncate(false)       // false by default: truncates segments longer than 2,048 input tokens
    .outputDimensionality(512) // for models that support different output vector dimensions
    .build();
```

## Embedding task types

Embedding models can be used for different use cases.
To get better embedding values, you can specify a _task_ among the following ones:

* `RETRIEVAL_QUERY`
* `RETRIEVAL_DOCUMENT`
* `SEMANTIC_SIMILARITY`
* `CLASSIFICATION`
* `CLUSTERING`
* `QUESTION_ANSWERING`
* `FACT_VERIFICATION`
* `CODE_RETRIEVAL_QUERY`

See the list of [supported models](https://cloud.google.com/vertex-ai/generative-ai/docs/embeddings/task-types).

### References

[Google Codelab on Vertex AI Embedding Model](https://codelabs.developers.google.com/codelabs/genai-chat-java-palm-langchain4j)

[Available stable Embedding Models](https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/text-embeddings#model_versions)

[Latest Embedding Models version](https://cloud.google.com/vertex-ai/generative-ai/docs/learn/model-versioning#palm-latest-models)
