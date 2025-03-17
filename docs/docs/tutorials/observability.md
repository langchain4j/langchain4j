---
sidebar_position: 31
---

# Observability

## LLM Observability

[Certain](/integrations/language-models) implementations of `ChatLanguageModel` and `StreamingChatLanguageModel`
(see "Observability" column") allow configuring `ChatModelListener`(s) to listen for events such as:
- Requests to the LLM
- Response from the LLM
- Errors

These events include various attributes, as described in the
[OpenTelemetry Generative AI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/), such as:
- Request:
  - Model
  - Temperature
  - Top P
  - Max Tokens
  - Messages
  - Tools
- Response:
  - ID
  - Model
  - Token Usage
  - Finish Reason
  - Assistant Message

Here is an example of using `ChatModelListener`:
```java
ChatModelListener listener = new ChatModelListener() {

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        ChatModelRequest request = requestContext.request();
        Map<Object, Object> attributes = requestContext.attributes();
        ...
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatModelResponse response = responseContext.response();
        ChatModelRequest request = responseContext.request();
        Map<Object, Object> attributes = responseContext.attributes();
        ...
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Throwable error = errorContext.error();
        ChatModelRequest request = errorContext.request();
        ChatModelResponse partialResponse = errorContext.partialResponse();
        Map<Object, Object> attributes = errorContext.attributes();
        ...
    }
};

ChatLanguageModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(GPT_4_O_MINI)
        .listeners(List.of(listener))
        .build();

model.generate("Tell me a joke about Java");
```

The `attributes` map allows passing information between the `onRequest`, `onResponse`, and `onError` methods.
