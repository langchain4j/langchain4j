---
sidebar_position: 29
---

# Kotlin Support

[Kotlin](https://kotlinlang.org) is a statically-typed language targeting the JVM (and other platforms), enabling concise and elegant code with seamless [interoperability](https://kotlinlang.org/docs/reference/java-interop.html) with Java libraries.
LangChain4j utilizes Kotlin [extensions](https://kotlinlang.org/docs/extensions.html) to enhance Java APIs with Kotlin-specific conveniences. This allows users to extend existing Java classes with additional functionality tailored for Kotlin.

For instance, Kotlin extensions can convert a [`ChatLanguageModel`](https://docs.langchain4j.dev/tutorials/chat-and-language-models) response into [Kotlin Suspending Function](https://kotlinlang.org/docs/coroutines-basics.html) response into a [Kotlin Suspending Function](https://kotlinlang.org/docs/coroutines-basics.html):

```kotlin
val model = OpenAiChatModel.builder()
    .apiKey("your-api-key")
    // more configuration parameters here ...
    .build()

CoroutineScope(Dispatchers.IO).launch {
    val response = model.chatAsync(
        ChatRequest.builder()
            .messages(
                listOf(
                    SystemMessage.from("You are a helpful assistant"),
                    UserMessage.from("Hello!")
                )
            )
    )
    println(response.aiMessage().text())
}
```

LangChain4j does not require Kotlin libraries as runtime dependencies but allows users to leverage Kotlin's coroutine capabilities for non-blocking execution, enhancing performance and efficiency.
