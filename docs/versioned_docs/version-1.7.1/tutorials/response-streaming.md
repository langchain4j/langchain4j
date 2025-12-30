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

    void onPartialResponse(String partialResponse);

    default void onPartialThinking(PartialThinking partialThinking) {}

    default void onPartialToolCall(PartialToolCall partialToolCall) {}

    default void onCompleteToolCall(CompleteToolCall completeToolCall) {}

    void onCompleteResponse(ChatResponse completeResponse);

    void onError(Throwable error);
}
```

By implementing `StreamingChatResponseHandler`, you can define actions for the following events:
- When the next partial textual response is generated: `onPartialResponse(String)` is invoked.
Depending on the LLM provider, partial response text can consist of a single or more tokens.
For instance, you can send the token directly to the UI as soon as it becomes available.
- When the next partial thinking/reasoning text is generated: `onPartialThinking(PartialThinking)` is invoked.
Depending on the LLM provider, partial thinking text can consist of a single or more tokens.
- When the next [partial tool call](/tutorials/tools#using-streamingchatmodel) is generated: `onPartialToolCall(PartialToolCall)` is invoked.
- When the LLM has completed streaming for a single tool call: `onCompleteToolCall(CompleteToolCall)` is invoked.
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
