---
sidebar_position: 1
---

# ModelRouter

This is the documentation for the `ModelRouter`, which acts as a router for messages to multiple ChatModel instances and uses pluggable RoutingStrategies.
The module comes with two default implementations: FailoverStrategy and LowestTokenUsageRoutingStrategy.


## Maven Dependencies

The `langchain4j-community-model-router`library is available on Maven Central.

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-model-router</artifactId>
    <version>1.12.0-beta20</version>
</dependency>
```
## FailoverStrategy

`FailoverStrategy` sends Messages to the first available model in order of registration. If this model fails (has an error) the message is immediately send to the next model. This strategy ignores failed ChatModels for a cooldown period and retries them again afterwards. The default cooldown is 1 minute. If all models fail, a `NoMatchingModelFoundException` is thrown.


```java
	ChatModel firstModel = AzureOpenAiChatModel.builder()
		...
         .build();

    ChatModel secondModel = OpenAiOfficialChatModel.builder()
		...
         .build();

    ModelRouter router = ModelRouter.builder()
        .addRoutes(firstModel, secondModel)
        .routingStrategy(new FailoverStrategy(Duration.ofMinutes(5)))
        .build();
            
   router.chat(new UserMessage("Provide 3 short bullet points explaining why Java is awesome"));
	   
	   
```

Now if the first model fails, messages are send to the second model.

:::note

As the FailoverStrategy sends messages to the next model, if one model fails, Exceptions thrown by this model are hidden from the calling code. You need to register an error listener if you want to keep track of them.

:::


## LowestTokenUsageRoutingStrategy

`LowestTokenUsageRoutingStrategy` sends Messages to the model which has the least overall token consumption. 

```java
ChatModel firstModel = AzureOpenAiChatModel.builder()
	...
     .build();

ChatModel secondModel = OpenAiOfficialChatModel.builder()
	...
     .build();

ModelRouter router = ModelRouter.builder()
        .addRoutes(firstModel, secondModel)
        .routingStrategy(new LowestTokenUsageRoutingStrategy())
        .build();


ChatResponse first = router.chat(new UserMessage("Hello")); // uses first model    

ChatResponse second = router.chat(new UserMessage("Hello")); // uses second model

ChatResponse third = router.chat(new UserMessage("Hello")); // uses first model  
	   	   
```
## Custom implementations

You can write your own strategy by implementing the functional interface ModelRoutingStrategy.

```java
interface ModelRoutingStrategy {

    /**
     * Determines the route key to use for the given chat messages.
     *
     * @param availableModels
     *            all configured models, including any routing metadata
     * @param chatRequest
     *            the incoming chat request
     * @return the key of the route to use
     */
    ChatModelWrapper route(List<ChatModelWrapper> availableModels, ChatRequest chatRequest);
}
```

For example, if you have a model which has a small context window and a more expensive one with a larger context window, you could build a simple router which takes the length of the message into account.
This example routes messages with less than 500 characters to the small model and the rest to a large model.

```java
ChatModel smallModel = AzureOpenAiChatModel.builder()
        // ...
        .build();

ChatModel largeModel = OpenAiOfficialChatModel.builder()
        // ...
        .build();

ModelRouter router = ModelRouter.builder()
        .addRoutes(smallModel, largeModel)
        .routingStrategy((availableModels, chatRequest) -> {
           int totalChars = chatRequest.messages().stream()
                    .filter(UserMessage.class::isInstance)
                    .map(UserMessage.class::cast)
                    .filter(UserMessage::hasSingleText)
                    .mapToInt(m -> m.singleText().length())
                    .sum();

            return totalChars < 500 ? availableModels.get(0) : availableModels.get(1);
        })
        .build();

ChatResponse shortMsg = router.chat(new UserMessage("Quick summary?")); // smallModel
ChatResponse longMsg = router.chat(new UserMessage("...very long prompt...")); // largeModel
```


## Examples

- [ModelRouter Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/model-router-examples/src/main/java)

