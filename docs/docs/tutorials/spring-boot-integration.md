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

The naming convention for the Spring Boot starter dependency is: `langchain4j-{integration-name}-spring-boot-starter`.

For example, for OpenAI (`langchain4j-open-ai`), the dependency name would be `langchain4j-open-ai-spring-boot-starter`:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.10.0-beta18</version>
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
import `langchain4j-spring-boot-starter`:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>1.10.0-beta18</version>
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

More details can be found [here](https://github.com/langchain4j/langchain4j-spring/blob/main/langchain4j-spring-boot-starter/src/main/java/dev/langchain4j/service/spring/AiService.java).

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

## Testing

- [An example of integration testing for a Customer Support Agent](https://github.com/langchain4j/langchain4j-examples/blob/main/customer-support-agent-example/src/test/java/dev/langchain4j/example/CustomerSupportAgentIT.java)

## Supported versions

LangChain4j Spring Boot integration requires Java 17 and Spring Boot 3.5, in line with the [Spring Boot OSS support policy](https://spring.io/projects/spring-boot#support).

Support for Spring Boot 4.x is not available yet in LangChain4j, but it's planned for a future release.

## Examples
- [Low-level Spring Boot example](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/main/java/dev/langchain4j/example/lowlevel/ChatModelController.java) using [ChatModel API](/tutorials/chat-and-language-models)
- [High-level Spring Boot example](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/main/java/dev/langchain4j/example/aiservice/AssistantController.java) using [AI Services](/tutorials/ai-services)
- [Example of customer support agent using Spring Boot](https://github.com/langchain4j/langchain4j-examples/blob/main/customer-support-agent-example/src/main/java/dev/langchain4j/example/CustomerSupportAgentApplication.java)
