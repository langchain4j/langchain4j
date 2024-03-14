---
sidebar_position: 27
---

# Spring Boot Integration

LangChain4j provides [Spring Boot starters](https://github.com/langchain4j/langchain4j-spring) for popular integrations.

To use one of the Spring Boot starters, first import the corresponding dependency:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>0.28.0</version>
</dependency>
```

Then, you can configure model parameters in the `application.properties` file as follows:

```
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
...
```

The complete list of supported properties can be found
[here](https://github.com/langchain4j/langchain4j-spring/blob/main/langchain4j-open-ai-spring-boot-starter/src/main/java/dev/langchain4j/openai/spring/AutoConfig.java).

In this case, an instance of `OpenAiChatModel` will be automatically created,
and you can autowire it where needed.

## Supported Versions

Spring Boot 2 and 3 are supported.

## Examples
- [Example of customer support agent using Spring Boot](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/test/java/dev/example/CustomerSupportApplicationTest.java)
