---
sidebar_position: 30
---

# Logging

LangChain4j uses [SLF4J](https://www.slf4j.org/) for logging,
allowing you to plug in any logging backend you prefer,
such as [Logback](https://logback.qos.ch/) or [Log4j](https://logging.apache.org/log4j/2.x/index.html).

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

## Sensitive data in logs

Request and response logs can contain credentials.
When an integration sends its requests through LangChain4j's own HTTP client
(for example OpenAI, Anthropic, Google AI Gemini, Mistral AI and Ollama),
`logRequests` and `logResponses` mask the most common ones:

- Headers of requests and responses: the value of every header whose name contains
`auth`, `key`, `token`, `secret`, `password`, `credential`, `cookie`, `signature` or `session`
(for example `Authorization` or `x-api-key`), ignoring case.
A value of 20 or more characters is shown as its first 5 and last 2 characters (`sk-pr...Xy`),
a shorter value is replaced by `...`.
- Request URL: the user info (`https://user:password@host` is logged as `https://***@host`)
and the value of every query parameter whose name contains one of the words above, as well as `sig` and `code`.
- Request body: the values of the JSON string fields and form fields `api_key`, `apikey`, `api-key`, `password`,
`secret`, `client_secret`, `token`, `access_token`, `refresh_token` and `id_token`.

Body field names are matched as a whole and ignoring case,
so `apiKey` is masked, but `max_tokens` is not.
Everything else, including your prompts and the model's answers, is logged as is.
The Amazon Bedrock integration masks request and response headers with the same name rule
and logs `[REDACTED]` instead of their values.

The libraries that some integrations use to talk to remote services have their own logging,
which can print credentials (for example API keys in headers or URLs).
Wherever real keys are used, keep these loggers at `INFO` or above and leave these settings off:

- Apache HttpClient 4: `org.apache.http.headers` and `org.apache.http.wire`
- Apache HttpClient 5: `org.apache.hc.client5.http.headers` and `org.apache.hc.client5.http.wire`
(the Docker MCP transport uses it and sends Docker registry credentials in a request header)
- gRPC with Netty: `io.grpc.netty`
- JDK `HttpClient`: the system property `-Djdk.httpclient.HttpClient.log=headers` (or `all`)
- google-http-client: `com.google.api.client.http.HttpTransport` at the `java.util.logging` level `CONFIG` or finer
- Spring `WebClient`: `spring.http.codecs.log-request-details=true`
(`spring.codec.log-request-details` before Spring Boot 3.5), which logs headers at `TRACE`
- AWS SDK for Java: wire logging, that is the Apache HttpClient loggers above,
or `io.netty.handler.logging` and `io.netty.handler.codec.http2` for its Netty-based client
- OpenAI Java SDK: the environment variable `OPENAI_LOG=debug`
(it hides only the standard authentication headers, not custom ones)
