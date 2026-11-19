---
sidebar_position: 41
---

# Response Caching

:::note
Response caching is experimental and may change in future releases.
:::

Some applications send the same request to a model more than once: the same question from different users,
a test suite that runs the same prompts on every build, or a batch job that is restarted after a failure.
With response caching, a repeated request is answered from a cache instead of calling the model again,
which saves time and tokens.

`CachingChatModel` is a `ChatModel` that wraps another `ChatModel` and a `ChatResponseCache`.
Since it is a `ChatModel`, it can be used everywhere a chat model is used: AI Services, agents, RAG, etc.

```java
ChatModel chatModel = new CachingChatModel(openAiModel, new InMemoryChatResponseCache(1000));

Assistant assistant = AiServices.create(Assistant.class, chatModel);
```

`InMemoryChatResponseCache` keeps up to the given number of responses in memory and evicts the least recently used
one when it is full. To keep responses somewhere else, for example in Redis, implement `ChatResponseCache`.
It has two methods, `get(ChatRequest)` and `put(ChatRequest, ChatResponse)`.

## When a request is a cache hit

Only an exactly equal request is a hit. Two requests are equal when their messages and parameters are equal,
so changing the model name, temperature, tools, response format or any other parameter is a miss.
The default request parameters of the wrapped model, including the model name, are part of the cache key,
so one cache can be shared by several models.

Keep in mind that a cached response is returned even if the model would answer differently today.
Caching fits requests where any earlier answer is good enough, such as a low temperature or a fixed seed.

## Limitations

- Failed calls are not cached. The next equal request calls the model again.
- A cache hit does not call the wrapped model, so the wrapped model's listeners are not notified about it.
- `chatAsync(...)` and streaming (`StreamingChatModel`) are not supported yet.
- Only exact matches are supported. Semantic caching, where a similar request is a hit, is not supported.
