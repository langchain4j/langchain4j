---
sidebar_position: 6
---

# DeepSeek

[DeepSeek Documentation](https://api-docs.deepseek.com/)

DeepSeek exposes an OpenAI-compatible API, so it can be used with LangChain4j through
[`langchain4j-open-ai`](/integrations/language-models/openai-compatible) by pointing
`baseUrl` to `https://api.deepseek.com`.

## Project setup

For Maven project `pom.xml`

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.22.0</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.22.0</version>
</dependency>
```

For Gradle project `build.gradle`

```groovy
implementation 'dev.langchain4j:langchain4j-open-ai:1.22.0'
implementation 'dev.langchain4j:langchain4j:1.22.0'
```

### API Key setup

Add your DeepSeek API key to your project, you can create a class `ApiKeys.java` with the following code

```java
public class ApiKeys {
    public static final String DEEPSEEK_API_KEY = System.getenv("DEEPSEEK_API_KEY");
}
```

Don't forget to set your API key as an environment variable:

```shell
export DEEPSEEK_API_KEY=sk-... #For Unix OS based
SET DEEPSEEK_API_KEY=sk-... #For Windows OS
```

You can create a DeepSeek API key [here](https://platform.deepseek.com/api_keys).

### Model Selection

| Model name | Description |
|---|---|
| `deepseek-flash` | Fast, general-purpose chat model. Use it for most tasks. |
| `deepseek-v4-pro` | Larger, more capable model. It returns its chain of thought in a `reasoning_content` field, which LangChain4j exposes as reasoning content. |

See [Models & Pricing](https://api-docs.deepseek.com/quick_start/pricing) for the current
list of models, context length, and peak/off-peak rates.

## Examples

### Chat model

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

ChatModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .baseUrl("https://api.deepseek.com")
        .modelName("deepseek-flash")
        .build();

String answer = model.chat("Explain in one sentence why Java is platform independent");
```

Use `deepseek-v4-pro` as `modelName` to get the larger reasoning-capable model.

### Streaming chat model

```java
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

StreamingChatModel model = OpenAiStreamingChatModel.builder()
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .baseUrl("https://api.deepseek.com")
        .modelName("deepseek-flash")
        .build();
```

### Tool calling

Tool calling works the same way as with OpenAI models:

```java
ChatModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .baseUrl("https://api.deepseek.com")
        .modelName("deepseek-flash")
        .build();

interface Assistant {
    String chat(String userMessage);
}

Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(model)
        .tools(new BookingTools())
        .build();
```

:::note
DeepSeek sends the complete tool call ID in every streaming chunk,
so when you use streaming together with tools, set `accumulateToolCallId(false)`:

```java
OpenAiStreamingChatModel.builder()
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .baseUrl("https://api.deepseek.com")
        .modelName("deepseek-flash")
        .accumulateToolCallId(false)
        .build();
```
:::

## Examples repository

Complete, runnable examples are available in
[langchain4j-examples](https://github.com/langchain4j/langchain4j-examples/tree/main/deepseek-examples).
