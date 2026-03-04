---
sidebar_position: 33
---

# Customizable HTTP Client

Some LangChain4j modules (currently OpenAI and Ollama) support customizing the HTTP clients used
to call the LLM provider API.

The `langchain4j-http-client` module implements an `HttpClient` SPI, which is used
by those modules to call the LLM provider's REST API.
This means the underlying HTTP client can be customized,
and any other HTTP client can be integrated by implementing the `HttpClient` SPI.

Currently, there are 3 out-of-the-box implementations:
- `JdkHttpClient` in the `langchain4j-http-client-jdk` module.
It is used by default when a supported module (e.g., `langchain4j-open-ai`) is used.
- `SpringRestClient` in the `langchain4j-http-client-spring-restclient`.
It is used by default when a supported module's Spring Boot starter (e.g., `langchain4j-open-ai-spring-boot-starter`) is used.
- `ApacheHttpClient` in the `langchain4j-http-client-apache` module.
It can be used by adding the `langchain4j-http-client-apache` dependency to the project.

## Customizing JDK's `HttpClient`

```java
HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
        .sslContext(...);

JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
        .httpClientBuilder(httpClientBuilder);

OpenAiChatModel model = OpenAiChatModel.builder()
        .httpClientBuilder(jdkHttpClientBuilder)
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .build();
```

## Customizing Spring's `RestClient`

```java
RestClient.Builder restClientBuilder = RestClient.builder()
        .requestFactory(new HttpComponentsClientHttpRequestFactory());

SpringRestClientBuilder springRestClientBuilder = SpringRestClient.builder()
        .restClientBuilder(restClientBuilder)
        .streamingRequestExecutor(new VirtualThreadTaskExecutor());

OpenAiChatModel model = OpenAiChatModel.builder()
        .httpClientBuilder(springRestClientBuilder)
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .build();
```

## Customizing Apache's `HttpClient`

```java
org.apache.hc.client5.http.impl.classic.HttpClientBuilder httpClientBuilder = org.apache.hc.client5.http.impl.classic.HttpClientBuilder.create();

ApacheHttpClientBuilder apacheHttpClientBuilder = ApacheHttpClient.builder()
        .httpClientBuilder(httpClientBuilder);

OpenAiChatModel model = OpenAiChatModel.builder()
        .httpClientBuilder(apacheHttpClientBuilder)
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .build();
```
