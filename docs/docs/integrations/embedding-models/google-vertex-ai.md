---
sidebar_position: 5
---

# Google Vertex AI

## Get started

To get started follow the steps outlined in the `Get started` section of [Vertex AI Gemini integration tutorial](../language-models/google-gemini) to create a
Google Cloud Platform account and establish a new project with access to Vertex AI API.

## Add dependencies

Add the following dependencies to your project's `pom.xml`:

```xml
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-vertex-ai</artifactId>
  <version>{your-version}</version> <!-- Specify langchain4j version here -->
</dependency>
```

or project's `build.gradle`:

```groovy
implementation 'dev.langchain4j:langchain4j-vertex-ai:{your-version}'
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
                .endpoint("us-central1-aiplatform.googleapis.com:443")
                .project(PROJECT_ID)
                .location("us-central1")
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

| Model name                              | Description                                                |
|-----------------------------------------|------------------------------------------------------------|
| textembedding-gecko@latest              | the newest stable embedding model with enhanced AI quality |
| textembedding-gecko-multilingual@latest | optimized for a wide range of non-English languages.       |

[List of supported languages for multi lingual model](https://cloud.google.com/vertex-ai/generative-ai/docs/embeddings/get-text-embeddings#language_coverage_for_textembedding-gecko-multilingual_models)

Model names suffixed with `@latest` reference the most recent version of the model.

The API accepts a maximum of 3,072 input tokens and outputs 768-dimensional vector embeddings.

### References

[Google Codelab on Vertex AI Embedding Model](https://codelabs.developers.google.com/codelabs/genai-chat-java-palm-langchain4j)

[Available stable Embedding Models](https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/text-embeddings#model_versions)

[Latest Embedding Models version](https://cloud.google.com/vertex-ai/generative-ai/docs/learn/model-versioning#palm-latest-models)
