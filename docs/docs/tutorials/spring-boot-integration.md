---
sidebar_position: 27
---

# Spring Boot Integration

## Spring Boot Starters for Popular Integrations

LangChain4j provides [Spring Boot starters](https://github.com/langchain4j/langchain4j-spring) for popular integrations.

To use one of the Spring Boot starters, first import the corresponding dependency:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>0.30.0</version>
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


## LangChain4j Spring Boot Starter

LangChain4j provides a Spring Boot starter for auto-configuring
[AI Services](/tutorials/ai-services), [RAG](/tutorials/rag), [Tools](/tutorials/tools) etc.

Assuming you have already imported one of the integrations starters (see above),
import `langchain4j-spring-boot-starter`:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>0.30.0</version>
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
More details [here](https://github.com/langchain4j/langchain4j-spring/blob/main/langchain4j-spring-boot-starter/src/main/java/dev/langchain4j/service/spring/AiService.java).


## Supported Spring Boot Versions

Spring Boot 2 and 3 are supported.

## Examples
- [Low-level Spring Boot example](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/main/java/dev/langchain4j/example/lowlevel/ChatLanguageModelController.java) using [ChatLanguageModel API](/tutorials/chat-and-language-models)
- [High-level Spring Boot example](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/main/java/dev/langchain4j/example/aiservice/AssistantController.java) using [AI Services](/tutorials/ai-services)
- [Example of customer support agent using Spring Boot](https://github.com/langchain4j/langchain4j-examples/blob/main/customer-support-agent-example/src/main/java/dev/langchain4j/example/CustomerSupportAgentApplication.java)
