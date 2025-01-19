---
sidebar_position: 29
---

# Kotlin Support

[Kotlin](https://kotlinlang.org) is a statically-typed language targeting the JVM (and other platforms), enabling concise and elegant code with seamless [interoperability](https://kotlinlang.org/docs/reference/java-interop.html) with Java libraries.
LangChain4j utilizes Kotlin [extensions](https://kotlinlang.org/docs/extensions.html) and [type-safe builders](https://kotlinlang.org/docs/type-safe-builders.html) to enhance Java APIs with Kotlin-specific conveniences. This allows users to extend existing Java classes with additional functionality tailored for Kotlin.

:::note
LangChain4j does not require Kotlin libraries as runtime dependencies but allows users to leverage Kotlin's coroutine capabilities for non-blocking execution, enhancing performance and efficiency.
:::

## ChatLanguageModel Extensions

This Kotlin code demonstrates how to use [coroutines and suspend functions](https://kotlinlang.org/docs/coroutines-basics.html) and [type-safe builders](https://kotlinlang.org/docs/type-safe-builders.html) to interact with a [`ChatLanguageModel`](https://docs.langchain4j.dev/tutorials/chat-and-language-models) in LangChain4j.

```kotlin
val model = OpenAiChatModel.builder()
    .apiKey("YOUR_API_KEY")
    // more configuration parameters here ...
    .build()

CoroutineScope(Dispatchers.IO).launch {
    val response = model.chat {
        messages += systemMessage("You are a helpful assistant")
        messages += userMessage("Hello!")
        parameters {
            temperature = 0.7
        }
    }
    println(response.aiMessage().text())
}
```

The interaction happens asynchronously using Kotlin's **coroutines**:
- `CoroutineScope(Dispatchers.IO).launch`: Executes the process on the [IO dispatcher](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html), optimized for blocking tasks like network or file I/O. This ensures responsiveness by preventing the calling thread from being blocked.
- `model.chat` is a suspend function, that uses a builder block to structure the chat request. This approach reduces boilerplate and makes the code more readable and maintainable.

For advanced scenarios, to support custom `ChatRequestParameters`, type-safe builder function accepts custom builder:
```kotlin
fun <B : DefaultChatRequestParameters.Builder<*>> parameters(
    builder: B = DefaultChatRequestParameters.builder() as B,
    configurer: ChatRequestParametersBuilder<B>.() -> Unit
)
```
Example usage:
```kotlin
 model.chat {
    messages += systemMessage("You are a helpful assistant")
    messages += userMessage("Hello!")
    parameters(OpenAiChatRequestParameters.builder()) {
        temperature = 0.7 // DefaultChatRequestParameters.Builder property
        builder.seed(42) // OpenAiChatRequestParameters.Builder property
    }
}
```
