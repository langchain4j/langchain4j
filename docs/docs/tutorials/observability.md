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
        }

        Map<Object, Object> attributes = requestContext.attributes();
        attributes.put("my-attribute", "my-value");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatResponse chatResponse = responseContext.chatResponse();

        AiMessage aiMessage = chatResponse.aiMessage();
        System.out.println(aiMessage);

        ChatResponseMetadata chatResponseMetadata = chatResponse.metadata();
        System.out.println(chatResponseMetadata.id());
        System.out.println(chatResponseMetadata.modelName());
        System.out.println(chatResponseMetadata.finishReason());

        TokenUsage tokenUsage = chatResponseMetadata.tokenUsage();
        System.out.println(tokenUsage.inputTokenCount());
        System.out.println(tokenUsage.outputTokenCount());
        System.out.println(tokenUsage.totalTokenCount());
        if (tokenUsage instanceof OpenAiTokenUsage openAiTokenUsage) {
            System.out.println(openAiTokenUsage.inputTokensDetails().cachedTokens());
            System.out.println(openAiTokenUsage.outputTokensDetails().reasoningTokens());
        }

        ChatRequest chatRequest = responseContext.chatRequest();
        System.out.println(chatRequest);

        Map<Object, Object> attributes = responseContext.attributes();
        System.out.println(attributes.get("my-attribute"));
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Throwable error = errorContext.error();
        error.printStackTrace();

        ChatRequest chatRequest = errorContext.chatRequest();
        System.out.println(chatRequest);

        Map<Object, Object> attributes = errorContext.attributes();
        System.out.println(attributes.get("my-attribute"));
    }
};

ChatLanguageModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(GPT_4_O_MINI)
        .listeners(List.of(listener))
        .build();

model.generate("Tell me a joke about Java");
```

The `attributes` map allows passing information between the `onRequest`, `onResponse`, and `onError` methods of the same
`ChatModelListener`, as well as between multiple `ChatModelListener`s.

## How listeners work

- Listeners are specified as a `List<ChatModelListener>` and are called in the order of iteration.
- Listeners are called synchronously and in the same thread.
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
  is the final request, containing both the default `ChatRequestParameters`
  and the request-specific `ChatRequestParameters` merged together.
- For `StreamingChatLanguageModel`, the `ChatModelListener.onResponse()`
  and `ChatModelListener.onError()` are called on a different thread than the `ChatModelListener.onRequest()`.
  before the `StreamingChatResponseHandler.onError()` is called. TODO context
- For `StreamingChatLanguageModel`, the `ChatModelListener.onResponse()` is called before the
  `StreamingChatResponseHandler.onCompleteResponse()` is called. The `ChatModelListener.onError()` is called
  before the `StreamingChatResponseHandler.onError()` is called.
