---
sidebar_position: 32
---

# Observability

## AI Service Observability

:::note
AI Service observability is an experimental feature. Its API and behavior may change in future versions.
:::

AI Service observability mechanisms allow users to track what is happening during an `AiService` invocation. A single invocation may involve multiple LLM invocations, any of which may succeed or fail. AI Service observability allows users to track the full sequence of invocations and their outcomes.

:::note
The AI Service observability capabilities are only available when using [AI Services](/tutorials/ai-services). They are a higher-level construct that can not be applied to a `ChatModel` or `StreamingChatModel`.
:::

The implementation was originally implemented in the [Quarkus LangChain4j extension](https://docs.quarkiverse.io/quarkus-langchain4j/dev/) and was backported here.

### Types of events

Each type of event has a unique identifier, which can be used to correlate events across multiple invocations.
Each type of event includes information encapsulated inside an
[`InvocationContext`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/invocation/InvocationContext.java).

The following types of events are currently available:

| Event Name                                                                                                                                                                                          | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`AiServiceStartedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceStartedEvent.java)                   | Invoked when an LLM invocation has started.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| [`AiServiceRequestIssuedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceRequestIssuedEvent.java)       | Invoked just before a request to an LLM is sent. Contains the details of the request being made. It is important to note that this can be invoked multiple times during a single AiService invocation when tools or guardrails exist.<br/><br/> Contains information such as the system message and the user message.                                                                                                                                                                                                                                                                                                           |
| [`AiServiceResponseReceivedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceResponseReceivedEvent.java) | Invoked when a response from an LLM is received. Contains the LLM response along with the corresponding request. It is important to note that this can be invoked multiple times during a single AiService invocation when tools or guardrails exist.<br/><br/> Contains information such as the system message and the user message.<br/><br/>Not every invocation will receive this event. If an invocation fails it will receive an [`AiServiceErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceErrorEvent.java) instead. |
| [`AiServiceErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceErrorEvent.java)                       | Fired when an invocation with an LLM fails. The failure could be because of network failure, AiService unavailable, input/output guardrails blocking the request, or many other reasons.<br/><br/>Contains information about the failure that occurred.                                                                                                                                                                                                                                                                                                                                                                       |
| [`AiServiceCompletedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceCompletedEvent.java)               | Invoked when an LLM invocation has completed successfully.<br/><br/>Not every invocation will receive this event. If an invocation fails it will receive an [`AiServiceErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceErrorEvent.java) instead.<br/><br/>Contains information about the result of the invocation.                                                                                                                                                                                                          |
| [`ToolExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/ToolExecutedEvent.java)                           | Invoked when a tool invocation has completed. It is important to note that this can be invoked multiple times within a single LLM invocation.<br/><br/>Contains information about the tool request and result.                                                                                                                                                                                                                                                                                                                                                                                                                |
| [`InputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/InputGuardrailExecutedEvent.java)       | Invoked when an [input guardrail](https://docs.langchain4j.dev/tutorials/guardrails#input-guardrails) validation has been executed. One of these events will be fired for each invocation of a guardrail.<br/><br/>Contains information about the input to an individual input guardrail, its output (i.e. was it successful or a failure?), and the execution duration.                                                                                                                                                                                                                                                      |
| [`OutputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/OutputGuardrailExecutedEvent.java)     | Invoked when an [output guardrail](https://docs.langchain4j.dev/tutorials/guardrails#output-guardrails) validation has been executed. One of these events will be fired for each invocation of a guardrail.<br/><br/>Contains information about the input to an individual output guardrail, its output (i.e. was it successful? failure? a retry? reprompt?), and the execution duration.                                                                                                                                                                                                                                    |

### Listening for an event

Each of the [types of events](#types-of-events) has its own listener that can be implemented to receive the event. You can pick and choose which events you want to listen for.

To listen for an event, create your own class implementing the listener interface you'd like to listen to. These are the available listener interfaces:

| Listener Name                                                                                                                                                                                                | Event                                                                                                                                                                                               |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`AiServiceStartedListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/AiServiceStartedListener.java)                   | [`AiServiceStartedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceStartedEvent.java)                   |
| [`AiServiceRequestIssuedListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/AiServiceRequestIssuedListener.java)       | [`AiServiceRequestIssuedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceRequestIssuedEvent.java)       |
| [`AiServiceResponseReceivedListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/AiServiceResponseReceivedListener.java) | [`AiServiceResponseReceivedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceResponseReceivedEvent.java) |
| [`AiServiceErrorListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/AiServiceErrorListener.java)                       | [`AiServiceErrorEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceErrorEvent.java)                       |
| [`AiServiceCompletedListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/AiServiceCompletedListener.java)               | [`AiServiceCompletedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceCompletedEvent.java)               |
| [`ToolExecutedEventListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/ToolExecutedEventListener.java)                 | [`ToolExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/ToolExecutedEvent.java)                           |
| [`InputGuardrailExecutedListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/InputGuardrailExecutedListener.java)       | [`InputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/InputGuardrailExecutedEvent.java)       |
| [`OutputGuardrailExecutedListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/OutputGuardrailExecutedListener.java)     | [`OutputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/OutputGuardrailExecutedEvent.java)     |

Once you've defined your listener(s), register them when you create your [AI Services](/tutorials/ai-services). There are various `registerListener` method variants on the [`AiServices` class](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java).

For example, you could do the following to create and register a listener for an `AiServiceCompletedEvent`:

```java
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.invocation.InvocationContext;

public class MyAiServiceCompletedListener implements AiServiceCompletedListener {
    @Override
    public void onEvent(AiServiceCompletedEvent event) {
        InvocationContext invocationContext = event.invocationContext();
        Optional<Object> result = event.result();

        // The invocationId will be the same for all events related to the same LLM invocation
        UUID invocationId = invocationContext.invocationId();
        String aiServiceInterfaceName = invocationContext.interfaceName();
        String aiServiceMethodName = invocationContext.methodName();
        List<Object> aiServiceMethodArgs = invocationContext.methodArguments();
        Object chatMemoryId = invocationContext.chatMemoryId();
        Instant eventTimestamp = invocationContext.timestamp();

        // Do something with the data
    }
}

// When creating your AI Service
MyAiServiceCompletedListener myListener = new MyAiServiceCompletedListener();

var myService = AiServices.builder(MyAiService.class)
        .chatModel(chatModel)  // Could also be .streamingChatModel(...)
        .registerListener(myListener)
        .build();
```

### Creating your own events and listeners

The AI Service observability capabilities are designed to be extensible. If you'd like to create your own events, you can do so by implementing the [`AiServiceEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceEvent.java) interface to define your own event.

Then, create your own event listener by implementing the [`AiServiceListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/AiServiceListener.java) interface.

Once you have your event and listener, you need to fire the event by obtaining/managing an instance of `AiServiceListenerRegistrar` and calling the `fireEvent(event)` method.

Once the event is getting fired, you can then create listeners and register your listeners just like you would with the built-in events.

### Extension points

You can also create your own custom [`AiServiceListenerRegistrar`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/AiServiceListenerRegistrar.java) by implementing the [`AiServiceListenerRegistrarFactory`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/spi/observability/AiServiceListenerRegistrarFactory.java) and registering it with the [Java Service Provider Interface (Java SPI)](https://www.baeldung.com/java-spi).

This could be useful if you want to manage the way you register/unregister your listeners and/or how you want to fire your events.

## Chat Model Observability

[Certain](/integrations/language-models) implementations of `ChatModel` and `StreamingChatModel`
(see "Observability" column) allow configuring `ChatModelListener`(s) to listen for events such as:
- Requests to the LLM
- Response from the LLM
- Errors

These events include various attributes, as described in the
[OpenTelemetry Generative AI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/), such as:
- Request:
  - Messages
  - Model
  - Temperature
  - Top P
  - Max Tokens
  - Tools
  - Response Format
  - etc
- Response:
  - Assistant Message
  - ID
  - Model
  - Token Usage
  - Finish Reason
  - etc

Here is an example of using `ChatModelListener`:
```java
ChatModelListener listener = new ChatModelListener() {

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        ChatRequest chatRequest = requestContext.chatRequest();

        List<ChatMessage> messages = chatRequest.messages();
        System.out.println(messages);

        ChatRequestParameters parameters = chatRequest.parameters();
        System.out.println(parameters.modelName());
        System.out.println(parameters.temperature());
        System.out.println(parameters.topP());
        System.out.println(parameters.topK());
        System.out.println(parameters.frequencyPenalty());
        System.out.println(parameters.presencePenalty());
        System.out.println(parameters.maxOutputTokens());
        System.out.println(parameters.stopSequences());
        System.out.println(parameters.toolSpecifications());
        System.out.println(parameters.toolChoice());
        System.out.println(parameters.responseFormat());

        if (parameters instanceof OpenAiChatRequestParameters openAiParameters) {
            System.out.println(openAiParameters.maxCompletionTokens());
            System.out.println(openAiParameters.logitBias());
            System.out.println(openAiParameters.parallelToolCalls());
            System.out.println(openAiParameters.seed());
            System.out.println(openAiParameters.user());
            System.out.println(openAiParameters.store());
            System.out.println(openAiParameters.metadata());
            System.out.println(openAiParameters.serviceTier());
            System.out.println(openAiParameters.reasoningEffort());
        }

        System.out.println(requestContext.modelProvider());

        Map<Object, Object> attributes = requestContext.attributes();
        attributes.put("my-attribute", "my-value");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatResponse chatResponse = responseContext.chatResponse();

        AiMessage aiMessage = chatResponse.aiMessage();
        System.out.println(aiMessage);

        ChatResponseMetadata metadata = chatResponse.metadata();
        System.out.println(metadata.id());
        System.out.println(metadata.modelName());
        System.out.println(metadata.finishReason());

        if (metadata instanceof OpenAiChatResponseMetadata openAiMetadata) {
            System.out.println(openAiMetadata.created());
            System.out.println(openAiMetadata.serviceTier());
            System.out.println(openAiMetadata.systemFingerprint());
        }

        TokenUsage tokenUsage = metadata.tokenUsage();
        System.out.println(tokenUsage.inputTokenCount());
        System.out.println(tokenUsage.outputTokenCount());
        System.out.println(tokenUsage.totalTokenCount());
        if (tokenUsage instanceof OpenAiTokenUsage openAiTokenUsage) {
            System.out.println(openAiTokenUsage.inputTokensDetails().cachedTokens());
            System.out.println(openAiTokenUsage.outputTokensDetails().reasoningTokens());
        }

        ChatRequest chatRequest = responseContext.chatRequest();
        System.out.println(chatRequest);

        System.out.println(responseContext.modelProvider());

        Map<Object, Object> attributes = responseContext.attributes();
        System.out.println(attributes.get("my-attribute"));
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Throwable error = errorContext.error();
        error.printStackTrace();

        ChatRequest chatRequest = errorContext.chatRequest();
        System.out.println(chatRequest);

        System.out.println(errorContext.modelProvider());

        Map<Object, Object> attributes = errorContext.attributes();
        System.out.println(attributes.get("my-attribute"));
    }
};

ChatModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(GPT_4_O_MINI)
        .listeners(List.of(listener))
        .build();

model.chat("Tell me a joke about Java");
```

The `attributes` map allows passing information between the `onRequest`, `onResponse`, and `onError` methods of the same
`ChatModelListener`, as well as between multiple `ChatModelListener`s.

### How listeners work

- Listeners are specified as a `List<ChatModelListener>` and are called in the order of iteration.
- Listeners are called synchronously and in the same thread. See more details about the streaming case below.
  The second listener is not called until the first one returns.
- The `ChatModelListener.onRequest()` method is called right before calling the LLM provider API.
- The `ChatModelListener.onRequest()` method is called only once per request.
  If an error occurs while calling the LLM provider API and a retry happens,
  `ChatModelListener.onRequest()` will **_not_** be called for every retry.
- The `ChatModelListener.onResponse()` method is called only once,
  immediately after receiving a successful response from the LLM provider.
- The `ChatModelListener.onError()` method is called only once.
  If an error occurs while calling the LLM provider API and a retry happens,
  `ChatModelListener.onError()` will **_not_** be called for every retry.
- If an exception is thrown from one of the `ChatModelListener` methods,
  it will be logged at the `WARN` level. The execution of subsequent listeners will continue as usual.
- The `ChatRequest` provided via `ChatModelRequestContext`, `ChatModelResponseContext`, and `ChatModelErrorContext`
  is the final request, containing both the default `ChatRequestParameters` configured on the `ChatModel`
  and the request-specific `ChatRequestParameters` merged together.
- For `StreamingChatModel`, the `ChatModelListener.onResponse()` and `ChatModelListener.onError()`
  are called on a different thread than the `ChatModelListener.onRequest()`.
  The thread context is currently not propagated automatically, so you might want to use the `attributes` map
  to propagate any necessary data from `ChatModelListener.onRequest()` to `ChatModelListener.onResponse()` or `ChatModelListener.onError()`.
- For `StreamingChatModel`, the `ChatModelListener.onResponse()` is called before the
  `StreamingChatResponseHandler.onCompleteResponse()` is called. The `ChatModelListener.onError()` is called
  before the `StreamingChatResponseHandler.onError()` is called.

## RAG Observability (EmbeddingModel, EmbeddingStore and ContentRetriever)

`EmbeddingModel`, `EmbeddingStore` and `ContentRetriever` can be instrumented with listeners to observe:
- Latency (measure duration using `attributes`)
- Payloads (e.g., `EmbeddingSearchRequest.queryEmbedding()` and retrieved matches/contents)
- Errors

### EmbeddingModel listener

Implement `EmbeddingModelListener`:

```java
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;

public class MyEmbeddingModelListener implements EmbeddingModelListener {

    @Override
    public void onRequest(EmbeddingModelRequestContext requestContext) {
        requestContext.attributes().put("startNanos", System.nanoTime());
    }

    @Override
    public void onResponse(EmbeddingModelResponseContext responseContext) {
        long startNanos = (long) responseContext.attributes().get("startNanos");
        long durationNanos = System.nanoTime() - startNanos;
        // Do something with duration and/or responseContext.response()
    }

    @Override
    public void onError(EmbeddingModelErrorContext errorContext) {
        // Do something with errorContext.error()
    }
}
```

Attach listeners using `EmbeddingModel#addListener(s)`:

```java
EmbeddingModel observedModel = embeddingModel.addListener(new MyEmbeddingModelListener());

observedModel.embed("hello");
```

### EmbeddingStore listener

Implement `EmbeddingStoreListener`:

```java
import dev.langchain4j.store.embedding.listener.EmbeddingStoreListener;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreRequestContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreResponseContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreErrorContext;

public class MyEmbeddingStoreListener implements EmbeddingStoreListener {

    @Override
    public void onRequest(EmbeddingStoreRequestContext<?> requestContext) {
        requestContext.attributes().put("startNanos", System.nanoTime());
    }

    @Override
    public void onResponse(EmbeddingStoreResponseContext<?> responseContext) {
        long startNanos = (long) responseContext.attributes().get("startNanos");
        long durationNanos = System.nanoTime() - startNanos;
        // Do something with duration and/or the response payload (if any), e.g.:
        if (responseContext instanceof EmbeddingStoreResponseContext.Search<?> search) {
            // Do something with search.searchResult()
        }
    }

    @Override
    public void onError(EmbeddingStoreErrorContext<?> errorContext) {
        // Do something with errorContext.error()
    }
}
```

Attach listeners using `EmbeddingStore#addListener(s)`:

```java
EmbeddingStore<TextSegment> observedStore = embeddingStore.addListener(new MyEmbeddingStoreListener());

// Use observedStore as usual, e.g. in EmbeddingStoreIngestor / EmbeddingStoreContentRetriever
```

### ContentRetriever listener

Implement `ContentRetrieverListener`:

```java
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverRequestContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverResponseContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverErrorContext;

public class MyContentRetrieverListener implements ContentRetrieverListener {

    @Override
    public void onRequest(ContentRetrieverRequestContext requestContext) {
        requestContext.attributes().put("startNanos", System.nanoTime());
    }

    @Override
    public void onResponse(ContentRetrieverResponseContext responseContext) {
        long startNanos = (long) responseContext.attributes().get("startNanos");
        long durationNanos = System.nanoTime() - startNanos;
        // Do something with duration and/or responseContext.contents()
    }

    @Override
    public void onError(ContentRetrieverErrorContext errorContext) {
        // Do something with errorContext.error()
    }
}
```

Attach listeners using `ContentRetriever#addListener(s)`:

```java
ContentRetriever observedRetriever = contentRetriever.addListener(new MyContentRetrieverListener());

observedRetriever.retrieve(Query.from("my query"));
```

### How listeners work

- Listeners are specified as a `List` and are called in the order of iteration.
- Listeners are called synchronously and in the same thread.
- `onRequest()` is called right before executing the underlying operation.
- `onResponse()` is called once after successful completion.
- `onError()` is called once if an exception is thrown by the underlying operation.
- If an exception is thrown from one of the listener methods, it will be logged at the `WARN` level and ignored.
- The `attributes` map allows passing information between the `onRequest`, `onResponse`, and `onError` methods of the same
  listener, as well as between multiple listeners.


## Observability in Spring Boot Application

See more details [here](/tutorials/spring-boot-integration#observability).

## Third-party Integrations

- [Arize Phoenix](https://github.com/Arize-ai/phoenix)

### OpenTelemetry GenAI instrumentation

The community-maintained [otel-genai-bridges](https://github.com/dineshkumarkummara/otel-genai-bridges) project ships a Spring Boot starter that auto-instruments LangChain4j chat applications using the [OpenTelemetry Generative AI semantic conventions](https://github.com/open-telemetry/semantic-conventions/tree/main/docs/gen-ai).

#### Why use it?

- Wraps any `ChatLanguageModel` bean and emits spans, events, and metrics.
- Captures prompts, completions, tool calls, latency, token usage, cost, and RAG retrieval latency out of the box.
- Provides Docker Compose samples (Collector → Tempo/Prometheus → Grafana) with prebuilt Grafana dashboards.

#### Getting started

Add the starter to your Spring Boot project:

```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.dineshkumarkummara.otel</groupId>
  <artifactId>langchain4j-otel</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Enable the starter via `application.yaml`:

```yaml
otel:
  langchain4j:
    enabled: true
    system: openai
    default-model: gpt-4o
    capture-prompts: true
    capture-completions: true
    cost:
      enabled: true
      input-per-thousand: 0.0005
      output-per-thousand: 0.0015
```

The nested `cost` stanza is optional; include it when you want cost-per-token metrics.

With the dependency on the classpath, the starter locates `ChatLanguageModel` beans automatically and wraps them with telemetry.

#### Observability view

![Grafana latency panel](https://github.com/dineshkumarkummara/otel-genai-bridges/raw/main/docs/screenshots/grafana-latency.png)

For a full working example (including the observability stack and Semantic Kernel parity), see [dineshkumarkummara/otel-genai-bridges](https://github.com/dineshkumarkummara/otel-genai-bridges).
