---
sidebar_position: 22
---

# watsonx.ai

- [watsonx.ai API Reference](https://cloud.ibm.com/apidocs/watsonx-ai)
- [watsonx.ai Java SDK](https://github.com/IBM/watsonx-ai-java-sdk)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-watsonx</artifactId>
    <version>1.4.0-beta10</version>
</dependency>
```

## WatsonxChatModel

The `WatsonxChatModel` class allows you to create an instance of the `ChatModel` interface fully encapsulated within LangChain4j.  
To create an instance, you must specify the mandatory parameters:

- `url(...)` – IBM Cloud endpoint URL (as `String`, `URI`, or `CloudRegion`);
- `apiKey(...)` – IBM Cloud IAM API key;
- `projectId(...)` – IBM Cloud Project ID (or use `spaceId(...)`);
- `modelName(...)` – Foundation model ID for inference;

### Example

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import com.ibm.watsonx.ai.CloudRegion;

ChatModel chatModel = WatsonxChatModel.builder()
    .url(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-3-3-8b-instruct")
    .temperature(0.7)
    .maxOutputTokens(0)
    .build();

String answer = chatModel.chat("Hello from watsonx.ai");
System.out.println(answer);
```

### How to create an IBM Cloud API Key

You can create an API key at [https://cloud.ibm.com/iam/apikeys](https://cloud.ibm.com/iam/apikeys) by clicking **Create +**.

### How to find your Project ID

1. Visit [https://dataplatform.cloud.ibm.com/projects/?context=wx](https://dataplatform.cloud.ibm.com/projects/?context=wx)  
2. Open your project  
3. Go to the **Manage** tab  
4. Copy the **Project ID** from the **Details** section  

### How to find the model name

Available foundation models are listed [here](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx#ibm-provided).

## WatsonxStreamingChatModel

The `WatsonxStreamingChatModel` provides streaming support for IBM watsonx.ai within LangChain4j. It’s useful when you want to process tokens as they are generated, ideal for real-time applications such as chat UIs or long text generation.

Streaming uses the same configuration structure and parameters as the non-streaming [`WatsonxChatModel`](#watsonxchatmodel). The main difference is that responses are delivered incrementally through a handler interface.

### Example

```java
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.ChatResponse;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import com.ibm.watsonx.ai.CloudRegion;

StreamingChatModel model = WatsonxStreamingChatModel.builder()
    .url(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-3-3-8b-instruct")
    .maxOutputTokens(0)
    .build();

model.chat("What is the capital of Italy?", new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.println("Partial: " + partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        System.out.println("Complete: " + completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

## Tool Integration

Both `WatsonxChatModel` and `WatsonxStreamingChatModel` support **LangChain4j Tools**, allowing the model to call Java methods annotated with `@Tool`.

Here’s an example using the synchronous model (`WatsonxChatModel`), but the same approach applies to the streaming variant.

```java
static class Tools {

    @Tool
    LocalDate currentDate() {
        return LocalDate.now();
    }

    @Tool
    LocalTime currentTime() {
        return LocalTime.now();
    }
}

interface AiService {
    String chat(String userMessage);
}

ChatModel chatModel = WatsonxChatModel.builder()
    .url(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("mistralai/mistral-small-3-1-24b-instruct-2503")
    .maxOutputTokens(0)
    .build();

AiService aiService = AiServices.builder(AiService.class)
        .chatModel(model)
        .tools(new Tools())
        .build();

String answer = aiService.chat("What is the date today?");
System.out.println(answer);
```

> **NOTE:** Ensure your selected model supports tool use.
---

## Enabling Thinking / Reasoning Output

Some foundation models can include "thinking"/"reasoning" steps in their responses.  
You can capture and separate this reasoning content from the final answer by using the `thinking(...)` builder method with `ExtractionTags`.

`ExtractionTags` defines the XML-like tags used to extract different parts of the model output:

- **Reasoning tag**: typically `<think>` — contains the model's internal reasoning.
- **Response tag**: typically `<response>` — contains the user-facing answer.

### Behavior

- If **both tags** are specified, they will be used directly for extracting reasoning and response.  
- If **only the reasoning tag** is specified, everything outside it will be considered the final response.  

#### Example ChatModel

```java
ChatModel chatModel = WatsonxChatModel.builder()
    .url(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-3-3-8b-instruct")
    .maxOutputTokens(0)
    .thinking(ExtractionTags.of("think", "response"))
    .build();

ChatResponse chatResponse = chatModel.chat(
    UserMessage.userMessage("Why the sky is blue?")
);

AiMessage aiMessage = chatResponse.aiMessage();

System.out.println(aiMessage.thinking());
System.out.println(aiMessage.text());
```

#### Example StreamingChatModel

```java
StreamingChatModel model = WatsonxStreamingChatModel.builder()
    .url(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-3-3-8b-instruct")
    .maxOutputTokens(0)
    .thinking(ExtractionTags.of("think", "response"))
    .build();

List<ChatMessage> messages = List.of(
    UserMessage.userMessage("Why the sky is blue?")
);

ChatRequest chatRequest = ChatRequest.builder()
    .messages(messages)
    .maxOutputTokens(0)
    .build();

model.chat(chatRequest, new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        ...  
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking) {
        ...
    }

});
```

> **Note:** Ensure that the selected model is enabled for reasoning.

## WatsonxEmbeddingModel

The `WatsonxEmbeddingModel` enables you to generate embeddings using IBM watsonx.ai and integrate them with LangChain4j's vector-based operations such as search, retrieval-augmented generation (RAG), and similarity comparison.

It implements the LangChain4j `EmbeddingModel` interface.

```java
EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
    .url(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-embedding-278m-multilingual")
    .build();

System.out.println(embeddingModel.embed("Hello from watsonx.ai"));
```
> 🔗 [View available embedding model IDs](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models-embed.html?context=wx&audience=wdp#embed)

## WatsonxScoringModel

The `WatsonxScoringModel` provides a LangChain4j-compatible implementation of a `ScoringModel` using IBM watsonx.ai Rerank (cross-encoder) models.

It is particularly useful for ranking a list of documents (or text segments) based on their relevance to a user query.

---

### Example: LangChain4j Integration

```java
ScoringModel scoringModel = WatsonxScoringModel.builder()
    .url(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("cross-encoder/ms-marco-minilm-l-12-v2")
    .build();

ScoringModel model = new WatsonxScoringModel(rerankService);

var scores = scoringModel.scoreAll(
    List.of(
        TextSegment.from("Example_1"),
        TextSegment.from("Example_2")
    ),
    "Hello from watsonx.ai"
);

System.out.println(scores);
```

---

> 🔗 [View available rerank model IDs](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models-embed.html?context=wx&audience=wdp#rerank)

---

## Quarkus

See more details [here](https://docs.quarkiverse.io/quarkus-langchain4j/dev/watsonx-chat-model.html).

## Examples

- [WatsonxChatModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxChatModelTest.java)
- [WatsonxChatModelReasoningTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxChatModelReasoningTest.java)
- [WatsonxStreamingChatModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxStreamingChatModelTest.java)
- [WatsonxStreamingChatModelReasoningTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxStreamingChatModelTest.java)
- [WatsonxToolsTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxToolsTest.java)
- [WatsonxEmbeddingModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxEmbeddingModelTest.java)
- [WatsonxScoringModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxScoringModelTest.java)
- [WatsonxTokenCounterEstimatorTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxTokenCounterEstimatorTest.java)
