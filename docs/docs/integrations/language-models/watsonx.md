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
    <version>1.8.0-beta15</version>
</dependency>
```

## WatsonxChatModel

The `WatsonxChatModel` class allows you to create an instance of the `ChatModel` interface fully encapsulated within LangChain4j.  
To create an instance, you must specify the mandatory parameters:

- `baseUrl(...)` – IBM Cloud endpoint URL (as `String`, `URI`, or `CloudRegion`);
- `apiKey(...)` – IBM Cloud IAM API key;
- `projectId(...)` – IBM Cloud Project ID (or use `spaceId(...)`);
- `modelName(...)` – Foundation model ID for inference;

### Example

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import com.ibm.watsonx.ai.CloudRegion;

ChatModel chatModel = WatsonxChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
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
    .baseUrl(CloudRegion.FRANKFURT)
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
    .baseUrl(CloudRegion.FRANKFURT)
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

Some foundation models can include internal *reasoning* (also referred to as *thinking*) steps as part of their responses.  
Depending on the model, this reasoning may be **embedded in the same text as the final response**, or **returned separately** in a dedicated field from `watsonx.ai`.  

To correctly enable and capture this behavior, you must configure the `thinking(...)` builder method according to the model’s output format.  
This ensures that LangChain4j can automatically extract the reasoning and response content from the model output.

There are two main configuration modes:

- **`ExtractionTags`** → for models that return reasoning and response in the same text block (e.g **ibm/granite-3-3-8b-instruct**).  
- **`ThinkingEffort`** → for models that already separate reasoning and response automatically (e.g **openai/gpt-oss-120b**).  

### Models that return reasoning and response together

Use **`ExtractionTags`** when the model outputs reasoning and response in the same text string.  
The tags define XML-like markers used to separate the reasoning from the final response.

**Example tags:**

- **Reasoning tag:** `<think>` — contains the model's internal reasoning.  
- **Response tag:** `<response>` — contains the user-facing answer.  

#### Behavior

- If **both tags** are specified, they are used directly to extract reasoning and response segments.  
- If **only the reasoning tag** is specified, everything outside that tag is considered the response.  

#### Example for **ibm/granite-3-3-8b-instruct**

```java
ChatModel chatModel = WatsonxChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-3-3-8b-instruct")
    .maxOutputTokens(0)
    .thinking(ExtractionTags.of("think", "response"))
    .build();

ChatResponse chatResponse = chatModel.chat(
    UserMessage.userMessage("Why is the sky blue?")
);

AiMessage aiMessage = chatResponse.aiMessage();

System.out.println(aiMessage.thinking());
System.out.println(aiMessage.text());
```

### Models that return reasoning and response separately.

For models that already return reasoning and response as separate fields, use the **`ThinkingEffort`** to control how much reasoning the model applies during generation.
Alternatively, enable it using the boolean flag.

#### Example for **openai/gpt-oss-120b**

```java
ChatModel chatModel = WatsonxChatModel.builder()
    .baseUrl(CloudRegion.DALLAS)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("openai/gpt-oss-120b")
    .thinking(ThinkingEffort.HIGH)
    .build();
```

or

```java
ChatModel chatModel = WatsonxChatModel.builder()
    .baseUrl(CloudRegion.DALLAS)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("openai/gpt-oss-120b")
    .thinking(true)
    .build();
```

### Streaming Example

```java
StreamingChatModel model = WatsonxStreamingChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-3-3-8b-instruct")
    .thinking(ExtractionTags.of("think", "response"))
    .build();

List<ChatMessage> messages = List.of(
    UserMessage.userMessage("Why is the sky blue?")
);

ChatRequest chatRequest = ChatRequest.builder()
    .messages(messages)
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

> **Notes:**
> - Ensure that the selected model supports reasoning output.  
> - Use `ExtractionTags` for models that embed reasoning and response in a single text string.  
> - Use `ThinkingEffort` or `thinking(true)` for models that already separate reasoning and response automatically.  

## WatsonxEmbeddingModel

The `WatsonxEmbeddingModel` enables you to generate embeddings using IBM watsonx.ai and integrate them with LangChain4j's vector-based operations such as search, retrieval-augmented generation (RAG), and similarity comparison.

It implements the LangChain4j `EmbeddingModel` interface.

```java
EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-embedding-278m-multilingual")
    .build();

System.out.println(embeddingModel.embed("Hello from watsonx.ai"));
```
> 🔗 [View available embedding model IDs](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models-embed.html?context=wx&audience=wdp#embed)

## WatsonxScoringModel

The `WatsonxScoringModel` provides a LangChain4j implementation of a `ScoringModel` using IBM watsonx.ai models.

It is particularly useful for ranking a list of documents (or text segments) based on their relevance to a user query.

### Example

```java
ScoringModel scoringModel = WatsonxScoringModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("cross-encoder/ms-marco-minilm-l-12-v2")
    .build();

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

## WatsonxModerationModel

The `WatsonxModerationModel` provides a LangChain4j implementation of the `ModerationModel` interface using IBM watsonx.ai.  
It allows to automatically detect and flag sensitive, unsafe, or policy-violating content in text through **detectors**.

One or multiple **detectors** can be used to identify different types of content, such as:

- **Pii** – Detects Personally Identifiable Information (e.g., emails, phone numbers)  
- **Hap** – Detects hate, abuse, or profanity  
- **GraniteGuardian** – Detects risky or harmful language  

### Example

```java
ModerationModel model = WatsonxModerationModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .detectors(Hap.ofDefaults(), GraniteGuardian.ofDefaults())
    .build();

Response<Moderation> response = model.moderate("...");
```

### Metadata

Each moderation response includes a `metadata` map that provides additional context about the detection.  

| Key | Description | 
|-----|--------------|
| `detection` | The detected label or category assigned by the detector
| `detection_type` | The type of detector that triggered the flag 
| `start` | The starting character index of the detected segment 
| `end` | The ending character index of the detected segment 
| `score` | The confidence score of the detection 

These metadata values are available via `Response.metadata()`:

```java
Map<String, Object> metadata = response.metadata();
System.out.println("Detection type: " + metadata.get("detection_type"));
System.out.println("Score: " + metadata.get("score"));
```

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
- [WatsonxModerationModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxModerationModelTest.java)