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

For the `ChatLanguageModel` and `LanguageModel` interfaces, there are corresponding
`StreamingChatLanguageModel` and `StreamingLanguageModel` interfaces.
These have a similar API but can stream the responses.
They accept an implementation of the `StreamingResponseHandler` interface as an argument.

```java
public interface StreamingResponseHandler<T> {

    void onNext(String token);
 
    default void onComplete(Response<T> response) {}

    void onError(Throwable error);
}
```

By implementing `StreamingResponseHandler`, you can define actions for the following events:
- When the next token is generated: `onNext(String token)` is invoked.
For instance, you can send the token directly to the UI as soon as it becomes available.
- When the LLM has completed generation: `onComplete(Response<T> response)` is invoked.
Here, `T` stands for `AiMessage` in the case of `StreamingChatLanguageModel`,
and `String` for `StreamingLanguageModel`. The `Response` object contains the complete response.
- When an error occurs: `onError(Throwable error)` is invoked.

Below is an example of how to implement streaming with `StreamingChatLanguageModel`:
```java
StreamingChatLanguageModel model = OpenAiStreamingChatModel.withApiKey(System.getenv("OPENAI_API_KEY"));

String userMessage = "Tell me a joke";

model.generate(userMessage, new StreamingResponseHandler<AiMessage>() {

    @Override
    public void onNext(String token) {
        System.out.println("onNext: " + token);
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
        System.out.println("onComplete: " + response);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```