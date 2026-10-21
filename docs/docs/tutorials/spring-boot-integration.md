---
sidebar_position: 27
---

# Spring Boot Integration

LangChain4j provides [Spring Boot starters](https://github.com/langchain4j/langchain4j-spring) for:
- popular integrations
- declarative [AI Services](/tutorials/ai-services)


## Spring Boot Starters

Spring Boot starters help with creating and configuring
[language models](/category/language-models),
[embedding models](/category/embedding-models),
[embedding stores](/category/embedding-stores),
and other core LangChain4j components through properties.

To use one of the [Spring Boot starters](https://github.com/langchain4j/langchain4j-spring),
import the corresponding dependency.

The naming convention for the Spring Boot starter dependency is:
- `langchain4j-{integration-name}-spring-boot4-starter` for **Spring Boot 4**
- `langchain4j-{integration-name}-spring-boot-starter` for **Spring Boot 3**

For example, for OpenAI (`langchain4j-open-ai`):

**Spring Boot 4:**
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot4-starter</artifactId>
    <version>1.22.0-beta32</version>
</dependency>
```

**Spring Boot 3:**
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.22.0-beta32</version>
</dependency>
```

Then, you can configure model parameters in the `application.properties` file as follows:
```
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.model-name=gpt-4o
langchain4j.open-ai.chat-model.log-requests=true
langchain4j.open-ai.chat-model.log-responses=true
...
```

In this case, an instance of `OpenAiChatModel` (an implementation of a `ChatModel`) will be automatically created,
and you can autowire it where needed:
```java
@RestController
public class ChatController {

    ChatModel chatModel;

    public ChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping("/chat")
    public String model(@RequestParam(value = "message", defaultValue = "Hello") String message) {
        return chatModel.chat(message);
    }
}
```

If you need an instance of a `StreamingChatModel`,
use the `streaming-chat-model` instead of the `chat-model` properties:
```
langchain4j.open-ai.streaming-chat-model.api-key=${OPENAI_API_KEY}
...
```


## Spring Boot starter for declarative AI Services

LangChain4j provides a Spring Boot starter for auto-configuring
[AI Services](/tutorials/ai-services), [RAG](/tutorials/rag), [Tools](/tutorials/tools) etc.

Assuming you have already imported one of the integrations starters (see above),
import `langchain4j-spring-boot4-starter` (Spring Boot 4) or `langchain4j-spring-boot-starter` (Spring Boot 3):

**Spring Boot 4:**
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot4-starter</artifactId>
    <version>1.22.0-beta32</version>
</dependency>
```

**Spring Boot 3:**
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>1.22.0-beta32</version>
</dependency>
```

You can now define AI Service interface and annotate it with `@AiService`:
```java
@AiService
interface Assistant {

    @SystemMessage("You are a polite assistant")
    String chat(String userMessage);
}
```

Think of it as a standard Spring Boot `@Service`, but with AI capabilities.

When the application starts, LangChain4j starter will scan the classpath
and find all interfaces annotated with `@AiService`.
For each AI Service found, it will create an implementation of this interface
using all LangChain4j components available in the application context and will register it as a bean,
so you can auto-wire it where needed:
```java
@RestController
class AssistantController {

    @Autowired
    Assistant assistant;

    @GetMapping("/chat")
    public String chat(String message) {
        return assistant.chat(message);
    }
}
```

### Automatic Component Wiring
The following components will be automatically wired into the AI Service if available in the application context:
- `ChatModel`
- `StreamingChatModel`
- `ChatMemory`
- `ChatMemoryProvider`
- `ContentRetriever`
- `RetrievalAugmentor`
- `ToolProvider`
- `ToolExecutionErrorHandler` and `ToolArgumentsErrorHandler` (see [Error Handling](/tutorials/tools#error-handling))
- All methods of any `@Component` or `@Service` class that are annotated with `@Tool`
An example:
```java
@Component
public class BookingTools {

    private final BookingService bookingService;

    public BookingTools(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Tool
    public Booking getBookingDetails(String bookingNumber, String customerName, String customerSurname) {
        return bookingService.getBookingDetails(bookingNumber, customerName, customerSurname);
    }

    @Tool
    public void cancelBooking(String bookingNumber, String customerName, String customerSurname) {
        bookingService.cancelBooking(bookingNumber, customerName, customerSurname);
    }
}
```

:::note
If multiple components of the same type are present in the application context, the application will fail to start.
In this case, use the explicit wiring mode (explained below).
:::

### Explicit Component Wiring

If you have multiple AI Services and want to wire different LangChain4j components into each of them,
you can specify which components to use with explicit wiring mode (`@AiService(wiringMode = EXPLICIT)`).

Let's say we have two `ChatModel`s configured:
```properties
# OpenAI
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.model-name=gpt-4o-mini

# Ollama
langchain4j.ollama.chat-model.base-url=http://localhost:11434
langchain4j.ollama.chat-model.model-name=llama3.1
```

```java
@AiService(wiringMode = EXPLICIT, chatModel = "openAiChatModel")
interface OpenAiAssistant {

    @SystemMessage("You are a polite assistant")
    String chat(String userMessage);
}

@AiService(wiringMode = EXPLICIT, chatModel = "ollamaChatModel")
interface OllamaAssistant {

    @SystemMessage("You are a polite assistant")
    String chat(String userMessage);
}
```

:::note
In this case, you must explicitly specify **all** components.
:::

#### Choosing Components via Configuration

Instead of hard-coding bean names, you can use property placeholders,
so that the component wired into an AI Service can be changed via configuration (e.g., per environment or profile):
```properties
my-app.assistant.chat-model-bean=ollamaChatModel
my-app.assistant.tool-beans=weatherTools,calendarTools
```

```java
@AiService(
        wiringMode = EXPLICIT,
        chatModel = "${my-app.assistant.chat-model-bean:openAiChatModel}",
        tools = "${my-app.assistant.tool-beans}"
)
interface Assistant {

    String chat(String userMessage);
}
```

A few things to keep in mind:
- A placeholder must resolve to a **bean name** (e.g., `ollamaChatModel`), not to the name of an LLM model (e.g., `llama3.1`).
The model name is configured on the `ChatModel` bean itself (e.g., `langchain4j.ollama.chat-model.model-name`).
- A default value can be specified after a colon: `${my-app.assistant.chat-model-bean:openAiChatModel}`.
- If a placeholder cannot be resolved and has no default value, the application fails to start.
- If a placeholder resolves to an empty value, the attribute is treated as if it was not set.
- For `tools`, a placeholder can resolve to several comma-separated bean names.

More details can be found [here](https://github.com/langchain4j/langchain4j-spring/blob/main/langchain4j-spring-boot-starter/src/main/java/dev/langchain4j/service/spring/AiService.java)
(same API for the Spring Boot 4 variant).

### Listening for AI Service Registration Events

After you have completed the development of the AI Service in a declarative manner, you can listen for the
`AiServiceRegisteredEvent` by implementing the `ApplicationListener<AiServiceRegisteredEvent>` interface.
This event is triggered when AI Service is registered in the Spring context, 
allowing you to obtain information about all registered AI services and their tools at runtime. 
Here is an example:
```java
@Component
class AiServiceRegisteredEventListener implements ApplicationListener<AiServiceRegisteredEvent> {


    @Override
    public void onApplicationEvent(AiServiceRegisteredEvent event) {
        Class<?> aiServiceClass = event.aiServiceClass();
        List<ToolSpecification> toolSpecifications = event.toolSpecifications();
        for (int i = 0; i < toolSpecifications.size(); i++) {
            System.out.printf("[%s]: [Tool-%s]: %s%n", aiServiceClass.getSimpleName(), i + 1, toolSpecifications.get(i));
        }
    }
}
```

## Flux

When streaming, you can use `Flux<String>` as a return type of AI Service:
```java
@AiService
interface Assistant {

    @SystemMessage("You are a polite assistant")
    Flux<String> chat(String userMessage);
}
```
For this, please import `langchain4j-reactor` module.
See more details [here](/tutorials/ai-services#flux).

That module also provides `Mono<T>` and `Flux<AiServiceStreamingEvent>` for the
[non-blocking modes](/tutorials/non-blocking), and the starter can route LangChain4j's offloaded work through
Spring's own task executor with `langchain4j.executor.use-spring-task-executor=true` so that tracing, MDC and
security context follow an asynchronous invocation.

The OpenAI, Mistral AI and Ollama starters send requests with Spring's HTTP clients: `RestClient` for blocking
calls and `WebClient` for the non-blocking modes. So when you use the non-blocking modes with one of these starters
and a provider that supports them (see [Provider support](/tutorials/non-blocking#provider-support)), `WebClient`
has to be on the classpath. `Flux<String>` and `TokenStream` do not need it.

- **Spring Boot 4:** add `spring-boot-starter-webclient`.
- **Spring Boot 3:** add `org.springframework:spring-webflux`. If your application is not a web application (no
  `spring-boot-starter-web`), also set `spring.main.web-application-type=none`, because Spring Boot treats an
  application with `spring-webflux` on the classpath as a reactive web application.

Avoid `spring-boot-starter-webflux` for this purpose unless your application is a WebFlux application: it also
brings a web server. Without `WebClient`, a non-blocking call fails with an `AsyncNotSupportedException` that says
what to add.
See [HTTP client](#http-client) for how these requests are configured.

## HTTP client

The OpenAI, Mistral AI and Ollama starters send requests with Spring's HTTP clients, configured the way Spring Boot
configures its own. The other starters use their own HTTP client, and this section does not apply to them. A model
that you build yourself, with `OpenAiChatModel.builder()` for example, is not configured this way: pass it an HTTP
client builder as shown in [Customizable HTTP Client](/tutorials/customizable-http-client).

- **Builders:** blocking requests are built from the application's `RestClient.Builder`, non-blocking requests from
  its `WebClient.Builder`. So the filters, default headers and observability configured on each builder apply, and a
  filter that adds credentials for your own services also runs for requests to the model provider. The two builders
  are configured separately: a customization of one does not apply to the other. If the application's builder is
  `@LoadBalanced` (Spring Cloud), declare a plain `@Primary` builder as well, or the model's HTTP client builder bean
  as shown below: a load-balanced builder resolves the model provider's host name as a service name.
- **HTTP client and settings:** requests are sent with the HTTP client that Spring Boot is configured to use,
  including its customizers (for example a proxy), and with Spring Boot's HTTP client settings, such as redirects
  and the SSL bundle. LangChain4j builds the request factory and the connector itself, to apply its timeouts, so a
  `ClientHttpRequestFactory` or `ClientHttpConnector` bean of the application is not used: customize them with
  `ClientHttpRequestFactoryBuilderCustomizer` and `ClientHttpConnectorBuilderCustomizer` beans instead. The settings
  are:
  - Spring Boot 4: `spring.http.clients.*`, for both kinds of requests;
  - Spring Boot 3: `spring.http.client.*` for blocking requests and `spring.http.reactiveclient.*` for non-blocking
    requests, as for Spring Boot's own clients. In a reactive web application, Spring Boot 3 does not apply
    `spring.http.client.*`, so blocking requests use the defaults.
- **Several beans:** if the application has several beans of one of these types (the builders, Spring Boot's HTTP
  client builders and its settings) and none of them is `@Primary`, LangChain4j uses none of them: it uses a new
  builder or the defaults instead, and logs a warning.
- **Timeouts** always come from LangChain4j, for example `langchain4j.open-ai.chat-model.timeout`. When it is not
  set, the connect timeout is 15 seconds and the read timeout 60 seconds: raise it for slow models, such as reasoning
  models or local models that load on first use. Spring Boot's `connect-timeout` and `read-timeout` have no effect on
  these requests.

So an SSL bundle configured in Spring Boot has to trust the model provider's certificate as well, and a client
certificate in it is also presented to the model provider. To send the requests of a model with other settings,
declare its HTTP client builder bean yourself. Its name is that of the model bean followed by `HttpClientBuilder`,
for example `openAiChatModelHttpClientBuilder` or `ollamaEmbeddingModelHttpClientBuilder`. With Spring Boot 4:

```java
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@Bean
HttpClientBuilder openAiChatModelHttpClientBuilder() {
    return SpringRestClient.builder()
            .restClientBuilder(RestClient.builder())
            // no SSL bundle, so the JVM's default trust store is used; the same as not setting it.
            // To use another SSL bundle: HttpClientSettings.ofSslBundle(sslBundles.getBundle("..."))
            .httpClientSettings(HttpClientSettings.defaults())
            .createDefaultStreamingRequestExecutor(false); // a chat model does not stream
}
```

With Spring Boot 3, the settings of blocking and non-blocking requests are set separately:

```java
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@Bean
HttpClientBuilder openAiChatModelHttpClientBuilder() {
    return SpringRestClient.builder()
            .restClientBuilder(RestClient.builder())
            .clientHttpRequestFactorySettings(ClientHttpRequestFactorySettings.defaults())
            .clientHttpConnectorSettings(ClientHttpConnectorSettings.defaults())
            .createDefaultStreamingRequestExecutor(false);
}
```

Such a bean replaces everything the starter configures for the HTTP client of that model, not only the settings.
To keep the rest, pass it yourself:

- the application's `RestClient.Builder`, with `restClientBuilder(...)`. The examples pass a new one, so that the
  filters and interceptors of the application are not applied to requests to the model provider;
- the application's `WebClient.Builder`, for non-blocking requests, with
  `webClientBuilder(WebClientBuilderHolder.of(webClientBuilder))`. Without it, a new `WebClient.builder()` is used;
- the HTTP client chosen by Spring Boot, with its customizers: pass the `ClientHttpRequestFactoryBuilder` and
  `ClientHttpConnectorBuilder` beans to `clientHttpRequestFactoryBuilder(...)` and `clientHttpConnectorBuilder(...)`;
- for a streaming model, the task executor that carries the context (for example tracing, MDC and security) to the
  thread reading the stream, with `streamingRequestExecutor(...)`. The starter declares it as a bean named after the
  model bean followed by `TaskExecutor`, for example `openAiStreamingChatModelTaskExecutor`.

To give LangChain4j a dedicated `WebClient.Builder`, declare a `WebClientBuilderHolder` bean:

```java
import dev.langchain4j.http.client.spring.restclient.WebClientBuilderHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@Bean
WebClientBuilderHolder langchain4jWebClientBuilder() {
    return WebClientBuilderHolder.of(WebClient.builder());
}
```

## Observability

To enable observability for a `ChatModel` or `StreamingChatModel`
bean, you need to declare one or more `ChatModelListener` beans:

```java
@Configuration
class MyConfiguration {
    
    @Bean
    ChatModelListener chatModelListener() {
        return new ChatModelListener() {

            private static final Logger log = LoggerFactory.getLogger(ChatModelListener.class);

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                log.info("onRequest(): {}", requestContext.chatRequest());
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                log.info("onResponse(): {}", responseContext.chatResponse());
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                log.info("onError(): {}", errorContext.error().getMessage());
            }
        };
    }
}
```

Every `ChatModelListener` bean in the application context will be automatically
injected into all `ChatModel` and `StreamingChatModel` beans
created by one of our Spring Boot starters.

### Micrometer Metrics
Add the `langchain4j-micrometer-metrics` dependency to your project:

For Maven:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-micrometer-metrics</artifactId>
    <version>1.22.0-beta32</version>
</dependency>
```
For Gradle:
```gradle
implementation 'dev.langchain4j:langchain4j-micrometer-metrics:1.22.0-beta32'
```

#### Micrometer (Actuator) Configuration
You should also have the necessary Actuator dependency in your project.
For example, if you are using Spring Boot, you can add the following dependencies to your `pom.xml`:

For Maven:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```
For Gradle:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

Enable the `/metrics` Actuator endpoint in your properties.

application.properties:
```properties
management.endpoints.web.exposure.include=metrics
```
application.yaml:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics
```

#### Configure `MicrometerMetricsChatModelListener` bean

In a Spring Boot application, you can define the listener as a bean and inject the `MeterRegistry`:

```java
import dev.langchain4j.micrometer.metrics.listeners.MicrometerMetricsChatModelListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MicrometerMetricsChatModelListener listener(MeterRegistry meterRegistry) {
        return new MicrometerMetricsChatModelListener(meterRegistry);
    }
}
```

#### View the Metrics

You can view the metrics by visiting the `/actuator/metrics` endpoint of your application.

For example, if you are running your application on `localhost:8080`,
you can visit http://localhost:8080/actuator/metrics to view the metrics.

##### Token Usage Metric

View the token usage metric at:
```
http://localhost:8080/actuator/metrics/gen_ai.client.token.usage
```

##### Filtering by Token Type

The `gen_ai.token.type` tag indicates whether the tokens were used for input or output:

| Token Type | Endpoint |
|------------|----------|
| Input tokens | `/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:input` |
| Output tokens | `/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:output` |

> **Note**: The `gen_ai.client.token.usage` metric is a histogram (DistributionSummary). The endpoint without any tags shows aggregated statistics (count, total, max) across all token types, models, and providers.

### Micrometer Observation API

This implements the `ChatModelListener` using the [Micrometer Observation API](https://docs.micrometer.io/micrometer/reference/observation.html) allowing transparent generation of Metrics and Traces by adding the following dependency:

For Maven:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-observation</artifactId>
</dependency>
```
For Gradle:
```gradle
implementation 'dev.langchain4j:langchain4j-observation'
```

You need to instantiate the Observation listener as follows...

#### Configure the ObservationChatModelListener bean

```java
@Configuration
public class ObservationConfig {

    @Bean
    public ObservationChatModelListener listener(ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        return new ObservationChatModelListener(observationRegistry, meterRegistry);
    }
}
```

This dependency requires the configuration of the [SpringBoot Actuator](spring-boot-integration.md#micrometer-actuator-configuration), as described above.

For additional observability requirements on a SpringBoot application please follow:
[Building Your First Observed Application](https://spring.io/blog/2022/10/12/observability-with-spring-boot-3#building-your-first-observed-application)

For more details about the `langchain4j-observation` library, please check the [Observability documentation](observability.md#micrometer-observation-api). 


## Testing

- [An example of integration testing for a Customer Support Agent](https://github.com/langchain4j/langchain4j-examples/blob/main/customer-support-agent-example/src/test/java/dev/langchain4j/example/CustomerSupportAgentIT.java)

## Supported versions

LangChain4j Spring Boot integration requires Java 17 and supports both:
- **Spring Boot 4** (4.0+) — use starters with the `-spring-boot4-starter` suffix
- **Spring Boot 3** (3.5+) — use starters with the `-spring-boot-starter` suffix, in line with the [Spring Boot OSS support policy](https://spring.io/projects/spring-boot#support)

Both families are released together and share the same version number. Choose the set of starters that matches the Spring Boot version in your project.

## Examples
- [Low-level Spring Boot example](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/main/java/dev/langchain4j/example/lowlevel/ChatModelController.java) using [ChatModel API](/tutorials/chat-and-language-models)
- [High-level Spring Boot example](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/main/java/dev/langchain4j/example/aiservice/AssistantController.java) using [AI Services](/tutorials/ai-services)
- [Example of customer support agent using Spring Boot](https://github.com/langchain4j/langchain4j-examples/blob/main/customer-support-agent-example/src/main/java/dev/langchain4j/example/CustomerSupportAgentApplication.java)
