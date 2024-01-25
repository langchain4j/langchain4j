---
sidebar_position: 4
---

# 2. Set Model Parameters
An example of specifying model parameters can be found [here](https://github.com/langchain4j/langchain4j-examples/blob/main/tutorials/src/main/java/_01_ModelParameters.java
).

## What are Parameters in LLMs
Depending on which model and which model provider you use, you can set a lot of parameters that will influence the model's output, speed, logging, etc.
Typically, you will find all the parameters and their meaning on the provider's website.


For example, OpenAI API's parameters can be found at https://platform.openai.com/docs/api-reference/chat (most up-to-date version)
and include options like

| Parameter          | Description                                                                                                                                                                                          | Type      |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| `modelName`        | The name of the model to use (gtp-3.5-turbo, gpt-4-1106-preview, ...)                                                                                                                                | `String`  |
| `temperature`      | What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more focused and deterministic.                 | `Double`  |
| `max_tokens`       | The maximum number of tokens that can be generated in the chat completion.                                                                                                                           | `Integer` |
| `frequencyPenalty` | Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.           | `Double`  |
| `...`              | ...                                                                                                                                                                                                  | `...`     |

For the full list of parameters in OpenAI LLMs, see the [OpenAI Language Model page](/docs/integrations/language-models/openai).
Full lists of parameters and default values per model can be found under the separate model pages (under Integration, Language Model and Image Model).

## Default Parameter Settings
The LangChain4j framework offers very easy model constructors with a lot of parameters set under the hood to sensible defaults. The fastest way to construct a model object is
```
ChatLanguageModel model = OpenAiChatModel.withApiKey("demo");
```
In this case of an OpenAI Chat Model for example, some of the defaults are

| Parameter      | Default Value | 
|----------------|---------------|
| `timeout`      | 60s           |
| `modelName`    | gpt-3.5-turbo |
| `temperature`  | 0.7           |
| `logRequests`  | false         |
| `logResponses` | false         |
| `...`          | ...           |

Defaults for all language and image models can be found on the pages of the respective providers under [Integrations](/docs/integrations).

## How to Set Parameter Values
When we use the builder pattern, we will be able to set all the available parameters of the model as follows:
```
ChatLanguageModel model = OpenAiChatModel.builder()
        .apiKey(ApiKeys.OPENAI_API_KEY)
        .modelName(GPT_3_5_TURBO)
        .temperature(0.3)
        .timeout(ofSeconds(60))
        .logRequests(true)
        .logResponses(true)
        .build();
```

## Parameter Settings in Quarkus
LangChain4j parameters in Quarkus applications can be set in the `application.properties` file as follows:
```
quarkus.langchain4j.openai.chat-model.temperature=0.5
quarkus.langchain4j.openai.timeout=60s
quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
```

Interestingly, for debugging, tweaking or even just knowing all the available parameters, one can have a look in the quarkus DEV UI.
In this dashboard, you can make changes that will be immediately reflected in your running instance, and your changes are automatically ported to the code.
The DEV UI can be accessed by running your Quarkus application with the command `quarkus dev`, then you can find it on localhost:8080/q/dev-ui (or wherever you deploy your application).


[![](/img/quarkus-dev-ui-parameters.png)](/docs/tutorials/set-model-parameters)

## Parameter Settings in Spring Boot
LangChain4j parameters in Spring Boot applications can be set in the `application.properties` file as follows:
```
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.model-name=gpt-4-1106-preview
langchain4j.open-ai.chat-model.temperature=0.0
langchain4j.open-ai.chat-model.timeout=PT60S
langchain4j.open-ai.chat-model.log-requests=false
langchain4j.open-ai.chat-model.log-responses=false
```