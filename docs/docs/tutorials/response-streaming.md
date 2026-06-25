---
sidebar_position: 5
---

# Response Streaming

:::note
This page describes response streaming with a low-level LLM API.
See [AI Services](/tutorials/ai-services#streaming) for a high-level LLM API.
:::

LLMs generate text one token at a time, so many LLM providers offer a way to stream the response
token-by-token instead of waiting for the entire text to be generated.
This significantly improves the user experience, as the user does not need to wait an unknown
amount of time and can start reading the response almost immediately.

For the `ChatModel` and `LanguageModel` interfaces, there are corresponding
`StreamingChatModel` and `StreamingLanguageModel` interfaces.
These have a similar API but can stream the responses.
They accept an implementation of the `StreamingChatResponseHandler` interface as an argument.

```java
public interface StreamingChatResponseHandler {

    default void onPartialResponse(String partialResponse) {}
    default void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {}

    default void onPartialThinking(PartialThinking partialThinking) {}
    default void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {}

    default void onPartialToolCall(PartialToolCall partialToolCall) {}
    default void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {}

    default void onCompleteToolCall(CompleteToolCall completeToolCall) {}

    default void onUnmappedRawEvent(Object rawEvent) {}

    void onCompleteResponse(ChatResponse completeResponse);

    void onError(Throwable error);
}
```

By implementing `StreamingChatResponseHandler`, you can define actions for the following events:
- When the next partial textual response is generated: either `onPartialResponse(String)`
or `onPartialResponse(PartialResponse, PartialResponseContext)` is invoked (you can implement either of these methods).
Depending on the LLM provider, partial response text can consist of a single or more tokens.
For instance, you can send the token directly to the UI as soon as it becomes available.
- When the next partial thinking/reasoning text is generated: either `onPartialThinking(PartialThinking)`
or `onPartialThinking(PartialThinking, PartialThinkingContext)` is invoked (you can implement either of these methods).
Depending on the LLM provider, partial thinking text can consist of a single or more tokens.
- When the next [partial tool call](/tutorials/tools#using-streamingchatmodel) is generated: either `onPartialToolCall(PartialToolCall)`
or `onPartialToolCall(PartialToolCall, PartialToolCallContext)` is invoked (you can implement either of these methods).
- When the LLM has completed streaming for a single tool call: `onCompleteToolCall(CompleteToolCall)` is invoked.
- When the provider emits a raw streaming event that is not already exposed through one of the typed callbacks
above: `onUnmappedRawEvent(Object)` is invoked. See [Unmapped Raw Events](#unmapped-raw-events) below.
- When the LLM has completed generation: `onCompleteResponse(ChatResponse)` is invoked.
The `ChatResponse` object contains the complete response (`AiMessage`) as well as `ChatResponseMetadata`.
- When an error occurs: `onError(Throwable error)` is invoked.

Below is an example of how to implement streaming with `StreamingChatModel`:
```java
StreamingChatModel model = OpenAiStreamingChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName(GPT_4_O_MINI)
    .build();

String userMessage = "Tell me a joke";

model.chat(userMessage, new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.println("onPartialResponse: " + partialResponse);
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking) {
        System.out.println("onPartialThinking: " + partialThinking);
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall) {
        System.out.println("onPartialToolCall: " + partialToolCall);
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        System.out.println("onCompleteToolCall: " + completeToolCall);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        System.out.println("onCompleteResponse: " + completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

A more compact way to stream the response is to use the `LambdaStreamingResponseHandler` class.
This utility class provides static methods to create a `StreamingChatResponseHandler` using lambda expressions.
The way to use lambdas to stream the response is quite simple. 
You just call the `onPartialResponse()` static method with a lambda expression that defines what to do with the partial response:

```java
import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponse;

model.chat("Tell me a joke", onPartialResponse(System.out::print));
```

The `onPartialResponseAndError()` method allows you to define actions for both
the `onPartialResponse()` and `onError()` events:

```java
import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponseAndError;

model.chat("Tell me a joke", onPartialResponseAndError(System.out::print, Throwable::printStackTrace));
```

## Unmapped Raw Events

:::note
This is an experimental feature intended for advanced use cases. The API may change in the future.
:::

Most applications only need the typed callbacks described above. However, some LLM providers emit
additional streaming events that LangChain4j does not (yet) map to a dedicated callback - for example,
the lifecycle events of OpenAI server-side tools such as `web_search`
(`response.web_search_call.in_progress`, `response.web_search_call.searching`,
`response.web_search_call.completed`).

The `onUnmappedRawEvent(Object rawEvent)` callback gives you access to such events. It is invoked
**only** for events that are **not** already exposed through one of the typed callbacks
(`onPartialResponse`, `onPartialThinking`, `onPartialToolCall`, `onCompleteToolCall`, `onCompleteResponse`).
In other words, partial responses, thinking and tool calls are **not** repeated as unmapped raw events,
so you can consume both without duplication.

The concrete type of `rawEvent` depends on the provider implementation:

| Provider | Raw event type |
|----------|----------------|
| OpenAI, Anthropic, Google AI Gemini, Mistral, Ollama | `dev.langchain4j.http.client.sse.ServerSentEvent` |
| OpenAI (official) - Responses API | `com.openai.models.responses.ResponseStreamEvent` |
| OpenAI (official) - Chat Completions API | `com.openai.models.chat.completions.ChatCompletionChunk` |
| Amazon Bedrock | `software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput` |
| Google GenAI | `com.google.genai.types.GenerateContentResponse` |

Because the event type is provider-specific, you typically inspect it with `instanceof` and cast:
```java
model.chat(userMessage, new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.println("onPartialResponse: " + partialResponse);
    }

    @Override
    public void onUnmappedRawEvent(Object rawEvent) {
        if (rawEvent instanceof ServerSentEvent sse) {
            System.out.println("Raw SSE event: " + sse.event() + " -> " + sse.data());
        }
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        System.out.println("onCompleteResponse: " + completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

When using [AI Services](/tutorials/ai-services#streaming), the same events are available via the
`TokenStream.onUnmappedRawEvent(Consumer<Object>)` callback.

## Streaming Cancellation

If you wish to cancel the streaming, you can do so from one of the following methods:
- `onPartialResponse(PartialResponse, PartialResponseContext)`
- `onPartialThinking(PartialThinking, PartialThinkingContext)`
- `onPartialToolCall(PartialToolCall, PartialToolCallContext)`

The context object contains the `StreamingHandle`, which can be used to cancel the streaming:
```java
model.chat(userMessage, new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        process(partialResponse);
        if (shouldCancel()) {
            context.streamingHandle().cancel();
        }
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        System.out.println("onCompleteResponse: " + completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

When `StreamingHandle.cancel()` is called, LangChain4j will close the connection and stop the streaming.
Once `StreamingHandle.cancel()` has been called, `StreamingChatResponseHandler` will not receive any further callbacks.
