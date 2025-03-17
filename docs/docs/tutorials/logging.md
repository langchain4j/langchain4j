---
sidebar_position: 30
---

# Logging

LangChain4j uses [SLF4J](https://www.slf4j.org/) for logging,
allowing you to plug in any logging backend you prefer,
such as [Logback](https://logback.qos.ch/) or [Log4j](https://logging.apache.org/log4j/2.x/index.html)).

## Pure Java

You can enable logging of each request and response to the LLM by setting
`.logRequests(true)` and `.logResponses(true)` when creating an instance of the model:
```java
OpenAiChatModel.builder()
    ...
    .logRequests(true)
    .logResponses(true)
    .build();
```

Make sure you have one of the SLF4J logging backends in your dependencies, for example, Logback:
```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.8</version>
</dependency>
```

## Quarkus

When using [Quarkus integration](/tutorials/quarkus-integration),
logging is configured in the `application.properties` file:

```properties
...
quarkus.langchain4j.openai.chat-model.log-requests = true
quarkus.langchain4j.openai.chat-model.log-responses = true
quarkus.log.console.enable = true
quarkus.log.file.enable = false
```

These properties can also be set and changed in the Quarkus Dev UI,
when running the application in dev mode (`mvn quarkus:dev`).
The Dev UI is then available at `http://localhost:8080/q/dev-ui`.

## Spring Boot

When using [Spring Boot integration](/tutorials/spring-boot-integration),
logging is configured in the `application.properties` file:

```properties
...
langchain4j.open-ai.chat-model.log-requests = true
langchain4j.open-ai.chat-model.log-responses = true
logging.level.dev.langchain4j = DEBUG
```
