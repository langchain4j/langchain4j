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

Currently, there are the following out-of-the-box implementations:
- `JdkHttpClient` from the `langchain4j-http-client-jdk` module.
It is used by default when a supported module (e.g., `langchain4j-open-ai`) is used.
- `SpringRestClient` from the `langchain4j-http-client-spring-boot4-restclient`/`langchain4j-http-client-spring-restclient` modules.
It is used by default when a supported module's Spring Boot starter (e.g., `langchain4j-open-ai-spring-boot4-starter`/`langchain4j-open-ai-spring-boot-starter`) is used.
- `ApacheHttpClient` from the `langchain4j-http-client-apache` module.
- `OkHttpClient` from the `langchain4j-http-client-okhttp` module.

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

### HTTP version

By default, `JdkHttpClient` uses HTTP/2 for `https://` URLs (when the server supports it) and HTTP/1.1 for
plain `http://` URLs. HTTP/2 over plain HTTP would require an `Upgrade: h2c` request, which many servers
do not handle correctly. For example, Python servers running on uvicorn with the `httptools` parser
(such as vLLM or Xinference) ignore the request body, and Java servers with HTTP/2 enabled on Tomcat or
Reactor Netty reject larger requests or stop responding.
Typical symptoms are a server error saying the request body is missing (e.g., `Field required`),
`Unsupported upgrade request`, `413 Request Entity Too Large`, or a request that hangs until it times out.

:::note
Before version 1.23.0, `JdkHttpClient` used HTTP/2 for plain `http://` URLs as well.
If you use an older version and see the symptoms above, provide an `HttpClient.Builder`
with `.version(HttpClient.Version.HTTP_1_1)` as shown below.
:::

When you provide your own `HttpClient.Builder` (for example, to configure a proxy or an `SSLContext`),
the HTTP version configured on it is used for all URLs. If you do not set a version on it, the JDK default
is HTTP/2, so plain `http://` requests send the `Upgrade: h2c` request again.
To use HTTP/1.1 in that case, set the version explicitly:

```java
HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
        .proxy(...)
        .version(HttpClient.Version.HTTP_1_1);

JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
        .httpClientBuilder(httpClientBuilder);
```

Conversely, if your plain-HTTP server supports the `Upgrade: h2c` request and you want to use HTTP/2 with it,
provide a builder with `.version(HttpClient.Version.HTTP_2)`.

:::note
An `HttpClient` implementation can also provide non-blocking counterparts: `executeAsync(...)` for a single
response and `stream(...)` for a cold `Flow.Publisher` of parsed server-sent events. The bundled JDK, OkHttp and
Apache clients implement both, and so does `SpringRestClient` when `spring-webflux` is on the classpath.
See [Non-blocking and Reactive](/tutorials/non-blocking).
:::

## Customizing Spring's `RestClient`

```java
SpringRestClientBuilder springRestClientBuilder = SpringRestClient.builder()
        .restClientBuilder(RestClient.builder())
        .clientHttpRequestFactoryBuilder(ClientHttpRequestFactoryBuilder.httpComponents())
        .streamingRequestExecutor(new VirtualThreadTaskExecutor());

OpenAiChatModel model = OpenAiChatModel.builder()
        .httpClientBuilder(springRestClientBuilder)
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .build();
```

`SpringRestClient` sends blocking requests with `RestClient` and non-blocking requests (`executeAsync(...)` and
`stream(...)`) with `WebClient`, which needs `spring-webflux` on the classpath. Because `spring-webflux` is optional,
the `WebClient.Builder` is passed in a `WebClientBuilderHolder`:

```java
SpringRestClientBuilder springRestClientBuilder = SpringRestClient.builder()
        .restClientBuilder(RestClient.builder())
        .webClientBuilder(WebClientBuilderHolder.of(WebClient.builder()))
        .clientHttpConnectorBuilder(ClientHttpConnectorBuilder.reactor()); // optional: pins the connector
```

`SpringRestClient` builds the request factory and the connector itself, to apply its timeouts. So a request factory
set on the `RestClient.Builder`, or a connector set on the `WebClient.Builder`, is not used: choose the HTTP client
with `clientHttpRequestFactoryBuilder(...)` and `clientHttpConnectorBuilder(...)` instead.

To apply Spring Boot's HTTP client settings, such as the SSL bundle and redirects, inject the settings beans that
Spring Boot configures and pass them on: `HttpClientSettings` with `httpClientSettings(...)` on Spring Boot 4, or
`ClientHttpRequestFactorySettings` with `clientHttpRequestFactorySettings(...)` and `ClientHttpConnectorSettings`
with `clientHttpConnectorSettings(...)` on Spring Boot 3. The timeouts in these settings are not used by LangChain4j
models, which always set their own: set the timeout on the model builder instead.

### HTTP version with the JDK client

When the JDK `HttpClient` is used, whether Spring picks it (no Apache HttpComponents, Jetty or Reactor Netty on the
classpath) or you set it with `clientHttpRequestFactoryBuilder(...)` or `clientHttpConnectorBuilder(...)`,
`SpringRestClient` behaves like `JdkHttpClient`: plain `http://` requests are sent over HTTP/1.1, and `https://`
requests use HTTP/2 when the server supports it. This applies to both blocking and non-blocking requests.
See [HTTP version](#http-version) for why plain HTTP does not use HTTP/2.

:::note
Before version 1.23.0-beta33, `SpringRestClient` used HTTP/2 for plain `http://` URLs as well.
If you use an older version and see the symptoms described in [HTTP version](#http-version),
set the version explicitly:

```java
SpringRestClientBuilder springRestClientBuilder = SpringRestClient.builder()
        .clientHttpRequestFactoryBuilder(ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1)))
        .clientHttpConnectorBuilder(ClientHttpConnectorBuilder.jdk() // only needed for non-blocking requests
                .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1)));
```
:::

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
