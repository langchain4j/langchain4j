---
sidebar_position: 7
---

# Google Vertex AI PaLM 2

## Get started

To get started follow the steps outlined in the `Get started` section of [Vertex AI Gemini integration tutorial](/integrations/language-models/google-gemini) to create a 
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

[An example of using Vertex AI Embedding Model](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/model/VertexAiEmbeddingModelExample.java)

The `PROJECT_ID` field represents the variable you set when creating a new Google Cloud project.

```java
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.VertexAiChatModel;

public class ChatLanguageModel {

    private static final String PROJECT_ID = "YOUR-PROJECT-ID";
    // `chat-bison` means PaLM2 general purpose chat model
    private static final String MODEL_NAME = "chat-bison";

    public static void main(String[] args) {
        ChatLanguageModel model = VertexAiChatModel.builder()
            .endpoint("us-central1-aiplatform.googleapis.com:443")
            .location("us-central1")
            .publisher("google")
            .project(PROJECT_ID)
            .modelName(MODEL_NAME)
            .temperature(0.0)
            .build();

        Response<AiMessage> response = model.generate(
            UserMessage.from(
                "Describe in several sentences what language model you are: \n" +
                "Describe in several sentences what is your code name: "
            )
        );
        System.out.println(response.content().text());

        // I am a large language model, trained by Google. 
        // I am a transformer-based language model that has been trained 
        //     on a massive dataset of text and code. 
        // I am able to understand and generate human language, 
        //     and I can also write code in a variety of programming languages.
        //
        // My code name is PaLM 2, which stands for Pathways Language Model 2.

    }

}
```

### Available chat models

Chat models are optimized for multi-turn chat, where the model keeps track of previous messages in the chat and uses it as context for generating new responses.

|Model name|Description| Properties                                                    |
|----------|-----------|---------------------------------------------------------------|
|chat-bison|Fine-tuned for multi-turn conversation use cases.| Maximum input tokens: 8192. Maximum output tokens: 2048       |
|chat-bison-32k|Fine-tuned for multi-turn conversation use cases.| Max tokens (input + output): 32,768. Max output tokens: 8,192 |
|codechat-bison|A model fine-tuned for chatbot conversations that help with code-related questions.| Maximum input tokens: 6144. Maximum output tokens: 1024       |
|codechat-bison-32k|A model fine-tuned for chatbot conversations that help with code-related questions.| Max tokens (input + output): 32,768. Max output tokens: 8,192 |

You can use bare model name e.g. `chat-bison` or specify a stable version,
like `chat-bison@002`.

### Available text models

Text models are optimized for performing natural language tasks, such as classification, summarization, extraction, content creation, and ideation.

Use the `VertexAiLanguageModel` [class](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-vertex-ai/src/test/java/dev/langchain4j/model/vertexai/VertexAiLanguageModelIT.java) for text models such as `text-bison`, `text-bison-32k`, and `text-unicorn`.

## Reference

[Google Codelab on Vertex AI PaLM 2 Model](https://codelabs.developers.google.com/codelabs/genai-text-gen-java-palm-langchain4j)

[PaLM2 generation models](https://cloud.google.com/vertex-ai/generative-ai/docs/learn/models#palm-models)

[Model naming explanation](https://cloud.google.com/vertex-ai/generative-ai/docs/language-model-overview#model_naming_scheme)

[Available PalM stable versions](https://cloud.google.com/vertex-ai/generative-ai/docs/learn/model-versioning#palm-stable-versions-available)
