---
sidebar_position: 4
---

# Model Parameters

Depending on the model and provider you choose, you can adjust numerous parameters that will define:
- The model's output: the level of creativity or determinism in the generated content (text, images),
the volume of content generated, etc.
- The connectivity: base URL, authorization keys, timeouts, retries, logging, etc.

Typically, you will find all the parameters and their meaning on the model provider's website.
For example, OpenAI API's parameters can be found at https://platform.openai.com/docs/api-reference/chat
(most up-to-date version)and include options like:

| Parameter          | Description                                                                                                                                                                                          | Type      |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| `modelName`        | The name of the model to use (gtp-3.5-turbo, gpt-4-1106-preview, ...)                                                                                                                                | `String`  |
| `temperature`      | What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more focused and deterministic.                 | `Double`  |
| `max_tokens`       | The maximum number of tokens that can be generated in the chat completion.                                                                                                                           | `Integer` |
| `frequencyPenalty` | Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.           | `Double`  |
| `...`              | ...                                                                                                                                                                                                  | `...`     |

For the full list of parameters in OpenAI LLMs, see the [OpenAI Language Model page](/integrations/language-models/open-ai).
Full lists of parameters and default values per model can be found under the separate model pages
(under Integration, Language Model and Image Model).

You can create `*Model` in two ways:
- A static factory that accepts only the mandatory parameters, such as API keys,
with all other mandatory parameters set to sensible defaults.
- A builder pattern: here, you can specify the value for each parameter.

## Static Factory
```java
OpenAiChatModel model = OpenAiChatModel.withApiKey("demo");
```
In this case of an OpenAI Chat Model for example, some of the defaults are

| Parameter      | Default Value | 
|----------------|---------------|
| `modelName`    | gpt-3.5-turbo |
| `temperature`  | 0.7           |
| `timeout`      | 60s           |
| `logRequests`  | false         |
| `logResponses` | false         |
| `...`          | ...           |

Defaults for all models can be found on the pages of the respective providers under [Integrations](/integrations).

## Builder
We can set every available parameter of the model using the builder pattern as follows:
```java
OpenAiChatModel model = OpenAiChatModel.builder()
        .apiKey("demo")
        .modelName("gpt-4")
        .temperature(0.3)
        .timeout(ofSeconds(60))
        .logRequests(true)
        .logResponses(true)
        .build();
```

## Setting Parameters in Quarkus
LangChain4j parameters in Quarkus applications can be set in the `application.properties` file as follows:
```
quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
quarkus.langchain4j.openai.chat-model.temperature=0.5
quarkus.langchain4j.openai.timeout=60s
```

Interestingly, for debugging, tweaking or even just knowing all the available parameters,
one can have a look in the quarkus DEV UI.
In this dashboard, you can make changes that will be immediately reflected in your running instance,
and your changes are automatically ported to the code.
The DEV UI can be accessed by running your Quarkus application with the command `quarkus dev`,
then you can find it on localhost:8080/q/dev-ui (or wherever you deploy your application).

[![](/img/quarkus-dev-ui-parameters.png)](/tutorials/model-parameters)

More info on Quarkus integration can be found [here](/tutorials/quarkus-integration).

## Setting Parameters in Spring Boot
If you are using one of our [Spring Boot starters](https://github.com/langchain4j/langchain4j-spring),
you can configure model parameters in the `application.properties` file as follows:
```
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.model-name=gpt-4-1106-preview
...
```
The complete list of supported properties can be found
[here](https://github.com/langchain4j/langchain4j-spring/blob/main/langchain4j-open-ai-spring-boot-starter/src/main/java/dev/langchain4j/openai/spring/AutoConfig.java).

More info on Spring Boot integration can be found [here](/tutorials/spring-boot-integration).
