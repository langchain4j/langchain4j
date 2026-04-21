---
sidebar_position: 33
---

# Customizing HTTP Client

## Overview

The `langchain4j-http-client` module implements an `HttpClient` SPI, which is used
by those modules to call the LLM provider's REST API.
This means the underlying HTTP client can be customized,
and any other HTTP client can be integrated by implementing the `HttpClient` SPI.

Currently, there are the following out-of-the-box implementations:
- `JdkHttpClient` from the `langchain4j-http-client-jdk` module.
It is used by default when a supported module (e.g., `langchain4j-open-ai`) is used.
- `SpringRestClient` from the `langchain4j-http-client-spring-restclient`/`langchain4j-http-client-spring-boot4-restclient` modules.
It is used by default when a supported module's Spring Boot starter (e.g., `langchain4j-open-ai-spring-boot-starter`/`langchain4j-open-ai-spring-boot4-starter`) is used.
- `ApacheHttpClient` from the `langchain4j-http-client-apache` module.
- `OkHttpClient` from the `langchain4j-http-client-okhttp` module.

## Timeout Configuration

### Migration from 1.x to 2.0

**Important:** As of 2.0, timeout configuration has been moved from `HttpClientBuilder` to the underlying HTTP client builder directly.
This change simplifies the API and makes it compatible with HTTP clients like Spring's `RestClient.Builder` that do not support setting timeouts as separate properties.

#### Before (deprecated in 2.0)

```java
ApacheHttpClientBuilder httpClientBuilder = ApacheHttpClient.builder()
    .connectTimeout(Duration.ofSeconds(30))
    .readTimeout(Duration.ofSeconds(60))
    .build();
```

#### After (2.0+)

Configure timeouts directly on the underlying HTTP client builder, then pass it to the `HttpClientBuilder`:

##### OkHttp

```java
OkHttpClient.Builder okBuilder = new OkHttpClient.Builder()
    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS);

OkHttpClientBuilder httpClientBuilder = OkHttpClient.builder()
    .okHttpClientBuilder(okBuilder)
    .build();
```

##### Apache HttpClient

```java
org.apache.hc.client5.http.impl.classic.HttpClientBuilder apacheBuilder =
    org.apache.hc.client5.http.impl.classic.HttpClientBuilder.create();
apacheBuilder.setConnectTimeout(30_000, java.util.concurrent.TimeUnit.MILLISECONDS);
apacheBuilder.setDefaultSocketConfig(
    org.apache.hc.client5.http.config.SocketConfig.custom()
        .setSoTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build());

ApacheHttpClientBuilder httpClientBuilder = ApacheHttpClient.builder()
    .httpClientBuilder(apacheBuilder)
    .build();
```

##### JDK HttpClient

```java
java.net.http.HttpClient.Builder jdkBuilder = java.net.http.HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .timeout(Duration.ofSeconds(60));

JdkHttpClientBuilder httpClientBuilder = JdkHttpClient.builder()
    .httpClientBuilder(jdkBuilder)
    .build();
```

##### Spring RestClient

```java
RestClient.Builder restBuilder = RestClient.builder()
    .requestConfigurer(request -> {
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(30_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .setSocketTimeout(60_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build();
        request.setConfig(config);
    });

SpringRestClientBuilder httpClientBuilder = SpringRestClient.builder()
    .restClientBuilder(restBuilder)
    .build();
```

#### Deprecated builder methods

The following methods on `HttpClientBuilder` are deprecated as of 2.0 (for removal):
- `HttpClientBuilder.connectTimeout(Duration)` — configure on underlying client instead
- `HttpClientBuilder.readTimeout(Duration)` — configure on underlying client instead
- `HttpClientBuilder.connectTimeout()` — getter, no longer needed
- `HttpClientBuilder.readTimeout()` — getter, no longer needed

These methods are kept for backward source compatibility but will be removed in a future version.

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
