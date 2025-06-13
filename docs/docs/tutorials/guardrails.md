---
sidebar_position: 12
toc_max_heading_level: 5
---

import useBaseUrl from '@docusaurus/useBaseUrl';
import ThemedImage from '@theme/ThemedImage';
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Guardrails

Guardrails are mechanisms that let you validate the input and output of the LLM to ensure it meets your expectations. You can do some of the following things with guardrails:
- Verify the user input is not out of scope
- Ensure the input meets some criteria before calling the LLM (i.e. guard against a [prompt injection attack](https://genai.owasp.org/llmrisk/llm01-prompt-injection/))
- Ensure the output format is correct (i.e. it is a JSON document with the correct schema)
- Ensure the LLM output is coherent with business rules and constraints (i.e. if this is a chatbot of company X, the response should not contain any reference to a competitor Y).
- Detect hallucinations

Those are just examples. You can do many other things with guardrails.

:::note
Guardrails are only available when using [AI Services](/tutorials/ai-services). They are a higher-level construct that can not be applied to a `ChatModel` or `StreamingChatModel`.
:::

<ThemedImage
  alt="Guardrails"
  sources={{
    light: useBaseUrl('/img/guardrails-light-bg.png'),
    dark: useBaseUrl('/img/guardrails-dark-bg.png'),
  }}
/>;

The implementation was originally done in the [Quarkus LangChain4j extension](https://docs.quarkiverse.io/quarkus-langchain4j/dev/) and was backported here. 

## Implementing Guardrails

Ideally, guardrail implementations should follow the [single responsibility principle](https://en.wikipedia.org/wiki/Single-responsibility_principle), meaning that each guardrail class should validate one thing. Then, chain guardrails together to guard against multiple things.

The order of guardrails in the chain is important. The first guardrail in the chain to fail will trigger the overall failure. Ensure guardrails that catch the most failures are early in the chain, whereas more specific guardrails that may fail very infrequently are towards the end of the chain.

Also keep in mind that guardrails can themselves call other services or even invoke other LLM interactions. If these kinds of guardrails have an execution penalty or monetary cost associated with them, make sure you take that into account. You might want to put more expensive guardrails towards the end of the chain.

:::note
The term _expensive_ can mean that something takes some time to execute or has a monetary value associated with it.
:::

## Input Guardrails

Input guardrails are functions invoked before the LLM is called. Failing an input guardrail prevents the LLM from being called. Input guardrails are the last step prior to calling the LLM. They are invoked _after_ any [RAG](/tutorials/rag) operations have happened.

### Implementing Input Guardrails

Input guardrails are implemented by implementing the [`InputGuardrail`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/InputGuardrail.java) interface. The `InputGuardrail` interface has two variants of the `validate` method, at least one of which needs to be implemented:

```java
InputGuardrailResult validate(UserMessage userMessage);
InputGuardrailResult validate(InputGuardrailRequest params);
```

The first variant is used for simple guardrails, or when the guardrail only needs access to the [`UserMessage`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/data/message/UserMessage.java).

The second variant is for more complex guardrails that need more information, such as the chat memory/history, user message template, augmentation results, or variables that were passed to the template. See [`InputGuardrailRequest`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/InputGuardrailRequest.java) for more information.

Some examples of things you could do:
- Check that there are enough documents in the augmentation results
- Ensure the user is not asking the same question multiple times
- Mitigate potential prompt injection attack

Input guardrails can be used whether the operation is synchronous or asynchronous/streaming.

### Input Guardrail Outcomes

Input guardrails can have the following outcomes. There are helper methods on the `InputGuardrail` interface that can provide the outcomes:

| Outcome                             | Helper method on `InputGuardrail`                 | Description                                                                                                                                                                |
|:------------------------------------|:--------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **_success_**                       | `success()`                                       | - The input is valid.<br/> - The next guardrail in the chain is executed.<br/> - The LLM is called if the last guardrail passes.                                           |
| **_success with alternate result_** | `successWith(String)`                             | Similar to **_success_** except the user message is altered before proceeding to the next step (next guardrail in the chain or calling the LLM).                           |
| **_failure_**                       | `failure(String)` or `failure(String, Throwable)` | - The input is invalid but the next guardrails in the chain continue to be executed in order to accumulate all possible validation problems.<br/> - The LLM is not called. |
| **_fatal_**                         | `fatal(String)` or `fatal(String, Throwable)`     | - The input is invalid and execution is halted with an `InputGuardrailException`.<br/> - The LLM is not called.                                                            |

### Declaring Input Guardrails

There are several ways to declare input guardrails, listed here in order of precedence:
1. [`InputGuardrail`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/InputGuardrail.java) implementation class names or instances set directly on the [`AiServices`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java) builder.
2. [`@InputGuardrails` annotations](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/guardrail/InputGuardrails.java) placed on an individual [AI Service](/tutorials/ai-services) method.
3. [`@InputGuardrails` annotation](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/guardrail/InputGuardrails.java) placed on an [AI Service](/tutorials/ai-services) class.
Regardless of how they are declared, input guardrails are always executed in the order they appear in the list.

#### `AiServices` builder

[`InputGuardrail`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/InputGuardrail.java) implementation class names or instances set directly on the [`AiServices`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java) builder have the highest precedence, meaning if it is declared in any other ways, the one declared directly on the builder will be the one used.

```java
public interface Assistant {
    String chat(String question);
    String doSomethingElse(String question);
}

var assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .inputGuardrailClasses(FirstInputGuardrail.class, SecondInputGuardrail.class)
    .build();
```

or

```java
public interface Assistant {
    String chat(String question);
    String doSomethingElse(String question);
}

var assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .inputGuardrails(new FirstInputGuardrail(), new SecondInputGuardrail())
    .build();
```

In the first scenario, classes that implement `InputGuardrail` are passed. New instances of these classes are created dynamically using reflection.

:::info
The way classes are converted to instances can be customized. For example, frameworks that use dependency injection (like [Quarkus](https://quarkus.io) or [Spring](https://spring.io)) can use [extension points](#extension-points) to provide instances based on how they manage class instances rather than creating new instances via reflection each time.
:::

#### Annotation on individual AI Service methods

[`@InputGuardrails` annotations](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/guardrail/InputGuardrails.java) placed on an individual [AI Service](/tutorials/ai-services) methods have the next highest precedence.

```java
public interface Assistant {
    @InputGuardrails({ FirstInputGuardrail.class, SecondInputGuardrail.class })
    String chat(String question);
    
    String doSomethingElse(String question);
}

var assistant = AiServices.create(Assistant.class, chatModel);
```

In this example, only the `chat` method has guardrails.
- On the `chat` method, `FirstInputGuardrail` is invoked first.
- Only if it is successful will the LLM be called.
- `SecondInputGuardrail` will only be invoked if `FirstInputGuardrail` does not result in a **_fatal_** result.
- Either `FirstInputGuardrail` or `SecondInputGuardrail` could re-write the user message.
- If `FirstInputGuardrail` re-writes the user message, then `SecondInputGuardrail` will receive the new user message as input.

The `doSomethingElse` method does not have any guardrails.

#### Annotation on the AI Service class

[`@InputGuardrails` annotation](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/guardrail/InputGuardrails.java) placed on an [AI Service](/tutorials/ai-services) class has the lowest precedence.

```java
@InputGuardrails({ FirstInputGuardrail.class, SecondInputGuardrail.class })
public interface Assistant {
    String chat(String question);
    String doSomethingElse(String question);
}

var assistant = AiServices.create(Assistant.class, chatModel);
```

In this example, both the `chat` and `doSomethingElse` methods have the guardrails.
- Just like in the previous example, `FirstInputGuardrail` is invoked first.
- Only if it is successful will the LLM be called.
- `SecondInputGuardrail` will only be invoked if `FirstInputGuardrail` does not result in a **_fatal_** result.
- Either `FirstInputGuardrail` or `SecondInputGuardrail` could re-write the user message.
- If `FirstInputGuardrail` re-writes the user message, then `SecondInputGuardrail` will receive the new user message as input.

### Unit Testing Input Guardrails

There are some unit testing utilities based on [AssertJ](https://assertj.github.io/doc/) in the `langchain4j-test` module.

<Tabs>
  <TabItem value="maven" label="Maven" default>
    ```xml
    <dependency>
      <groupId>dev.langchain4j</groupId>
      <artifactId>langchain4j-test</artifactId>
      <scope>test</scope>
    </dependency>
    ```
  </TabItem>
  <TabItem value="gradleGroovy" label="Gradle (Groovy)">
    ```groovy
    testImplementation 'dev.langchain4j:langchain4j-test'
    ```
  </TabItem>
  <TabItem value="gradleKotlin" label="Gradle (Kotlin)">
    ```kotlin
    testImplementation("dev.langchain4j:langchain4j-test")
    ```
  </TabItem>
</Tabs>

Once you have the dependency, you can perform these kinds of validations:

```java
import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailResult.Result;

class Tests { 
    MyInputGuardrail inputGuardrail = new MyInputGuardrail();
    
    @Test 
    void test() {
        var userMessage = UserMessage.from("Some user message");
        var result = inputGuardrail.validate(userMessage);
        
        // These are just some examples of what you can do
        assertThat(result)
                .isSuccessful()
                .hasResult(Result.FATAL)
                .hasFailures()
                .hasSingleFailureWithMessage("Prompt injection detected")
                .assertSingleFailureSatisfied(failure -> assertThat(failure)...)
                .withFailures().....
    }
}
```

:::info
See the [`GuardrailAssertions`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-test/src/main/java/dev/langchain4j/test/guardrail/GuardrailAssertions.java) and [`InputGuardrailResultAssert`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-test/src/main/java/dev/langchain4j/test/guardrail/InputGuardrailResultAssert.java) classes for more details.
:::

## Output Guardrails

Output guardrails are functions executed after the LLM has produced its output. Failing an output guardrail allows for more advanced scenarios, such as [retrying](#retry) or [reprompting](#reprompt), to help improve the response. They are invoked _after_ all other operations, including function/tool calls, have happened.

### Implementing Output Guardrails

Similar to input guardrails, output guardrails are implemented by implementing the [`OutputGuardrail`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/OutputGuardrail.java) interface. The `OutputGuardrail` interface has two variants of the `validate` method, at least one of which needs to be implemented:

```java
OutputGuardrailResult validate(AiMessage responseFromLLM);
OutputGuardrailResult validate(OutputGuardrailRequest params);
```

The first variant is used for simple guardrails, or when the guardrail only needs access to the resulting [`AiMessage`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/data/message/AiMessage.java).

The second variant is for more complex guardrails that need more information, such as the entire chat response, chat memory/history, user message template, or variables that were passed to the template. See [`OutputGuardrailRequest`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/OutputGuardrailRequest.java) for more information.

Some examples of things you could do:
- Ensure the output format is correct (i.e. it is a JSON document with the correct schema)
- Detect an LLM hallucination
- Validate that the LLM response contains certain information

### Output Guardrail Outcomes

Output guardrails can have the following outcomes. There are helper methods on the `OutputGuardrail` interface that can provide the outcomes:

| Outcome                    | Helper method on `OutputGuardrail`                                  | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|:---------------------------|:--------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **_success_**              | `success()`                                                         | - The output is valid.<br/> - The next guardrail in the chain is executed. If the last guardrail passes the output is returned to the caller.                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **_success with rewrite_** | `successWith(String)` or `successWith(String, Object)`              | -Similar to **_success_** except the output isn't valid in its original form and has been rewritten to make it valid.<br/> - The next guardrail is executed against the rewritten output. If the last guardrail passes the output is returned to the caller.                                                                                                                                                                                                                                                                                                                                                          |
| **_failure_**              | `failure(String)` or `failure(String, Throwable)`                   | - The output is invalid but the next guardrails in the chain continue to be executed in order to accumulate all possible validation problems.<br/> - The validation failure is returned to the user as an `OutputGuardrailException`.                                                                                                                                                                                                                                                                                                                                                                                 |
| **_fatal_**                | `fatal(String)` or `fatal(String, Throwable)`                       | The output is invalid and execution is halted with an `OutputGuardrailException` thrown to the caller.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **_fatal with retry_**     | `retry(String)` or `retry(String, Throwable)`                       | - Similar to **_fatal_** except the LLM is called again with the same prompt and chat history as the original call.<br/> - If the failure persists after a [configurable number of retries](#configuration) then execution is halted with an `OutputGuardrailException` thrown to the caller.<br/> - If the guardrail passes after a retry, the entire chain of guardrails are re-executed from the beginning.                                                                                                                                                                                                        |
| **_fatal with reprompt_**  | `reprompt(String, String)` or `reprompt(String, Throwable, String)` | - Similar to **_fatal with retry_** except the LLM is called again with a new prompt supplied by the guardrail.<br/> - In this situation, the guardrail supplies an additional message to append to the previous user message, then sends a new request to the LLM with the new user message and original chat history.<br/> - If the failure persists after a [configurable number of retries](#configuration) then execution is halted with an `OutputGuardrailException` thrown to the caller.<br/> - If the guardrail passes after a reprompt, the entire chain of guardrails are re-executed from the beginning. |

### Declaring Output Guardrails

There are several ways to declare output guardrails, listed here in order of precedence:
1. [`OutputGuardrail`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/OutputGuardrail.java) implementation class names or instances set directly on the [`AiServices`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java) builder.
2. [`@OutputGuardrails` annotations](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/guardrail/OutputGuardrails.java) placed on an individual [AI Service](/tutorials/ai-services) method.
3. [`@OutputGuardrails` annotation](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/guardrail/OutputGuardrails.java) placed on an [AI Service](/tutorials/ai-services) class.

Regardless of how they are declared, output guardrails are always executed in the order they appear in the list.

#### `AiServices` builder

[`OutputGuardrail`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/OutputGuardrail.java) implementation class names or instances set directly on the [`AiServices`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java) builder have the highest precedence, meaning if it is declared in any other ways, the one declared on the builder will be the one used.

```java
public interface Assistant {
    String chat(String question);
    String doSomethingElse(String question);
}

var assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .outputGuardrailClasses(FirstOutputGuardrail.class, SecondOutputGuardrail.class)
    .build();
```

or

```java
public interface Assistant {
    String chat(String question);
    String doSomethingElse(String question);
}

var assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .outputGuardrails(new FirstOutputGuardrail(), new SecondOutputGuardrail())
    .build();
```

In the first scenario, classes that implement `OutputGuardrail` are passed. New instances of these classes are created dynamically using reflection.

:::info
The way classes are converted to instances can be customized. For example, frameworks that use dependency injection (like [Quarkus](https://quarkus.io) or [Spring](https://spring.io)) can use [extension points](#extension-points) to provide instances based on how they manage class instances rather than creating new instances via reflection each time.
:::

#### Annotation on individual AI Service methods

[`@OutputGuardrails` annotations](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/guardrail/OutputGuardrails.java) placed on ndividual [AI Service](/tutorials/ai-services) methods have the next highest precendence.

```java
public interface Assistant {
    @OutputGuardrails({ FirstOutputGuardrail.class, SecondOutputGuardrail.class })
    String chat(String question);
    
    String doSomethingElse(String question);
}

var assistant = AiServices.create(Assistant.class, chatModel);
```

In this example, only the `chat` method has guardrails.
- On the `chat` method, `FirstOutputGuardrail` is invoked first.
- Only if it is successful will the result be returned to the caller. `SecondOutputGuardrail` will only be invoked if `FirstOutputGuardrail` does not result in a **_fatal_**, **_fatal with retry_**, or **_fatal with reprompt_** result.
- `SecondOutputGuardrail` will receive the output of `FirstOutputGuardrail`.
- If `SecondOutputGuardrail` succeeds after a retry or reprompt, then both `FirstOutputGuardrail` and `SecondOutputGuardrail` are re-executed.

The `doSomethingElse` method does not have any guardrails.

#### Annotation on the AI Service class

[`@OutputGuardrails` annotation](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/guardrail/OutputGuardrails.java) placed on an [AI Service](/tutorials/ai-services) class has the lowest precedence.

```java
@OutputGuardrails({ FirstOutputGuardrail.class, SecondOutputGuardrail.class })
public interface Assistant {
    String chat(String question);
    String doSomethingElse(String question);
}

var assistant = AiServices.create(Assistant.class, chatModel);
```

In this example, both the `chat` and `doSomethingElse` methods have the guardrails.
- Just like in the previous example, `FirstOutputGuardrail` is invoked first.
- Only if it is successful will the result be returned to the caller. `SecondOutputGuardrail` will only be invoked if `FirstOutputGuardrail` does not result in a **_fatal_**, **_fatal with retry_**, or **_fatal with reprompt_** result. 
- `SecondOutputGuardrail` will receive the output of `FirstOutputGuardrail`.
- If `SecondOutputGuardrail` succeeds after a retry or reprompt, then both `FirstOutputGuardrail` and `SecondOutputGuardrail` are re-executed.

#### Configuration

Output guardrails have the following additional configuration that can be supplied:

| Configuration | Description                                                                                                                                                |
|:--------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `maxRetries`  | - The maximum number of retries for an output guardrail when performing a retry or reprompt.<br/> - Defaults to `2`.<br/> - Set to `0` to disable retries. |

##### Annotation on individual AI Service methods

```java
public interface MethodLevelAssistant {
    @OutputGuardrails(
            value = { FirstOutputGuardrail.class, SecondOutputGuardrail.class },
            maxRetries = 10
    )
    String chat(String question);
}

var assistant = AiServices.create(MethodLevelAssistant.class, chatModel);
```

##### Annotation on the AI Service class

```java
@OutputGuardrails(
        value = { FirstOutputGuardrail.class, SecondOutputGuardrail.class },
        maxRetries = 10
)
public interface ClassLevelAssistant {
    String chat(String question);
}

var assistant = AiServices.create(ClassLevelAssistant.class, chatModel);
```

##### `AiServices` builder

```java
public interface Assistant {
    String chat(String message);
}

var outputGuardrailsConfig = OutputGuardrailsConfig.builder()
        .maxRetries(10)
        .build();

var assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .outputGuardrailsConfig(outputGuardrailsConfig)
        .outputGuardrailClasss(FirstOutputGuardrail.class, SecondOutputGuardrail.class)
        .build();
```

### Output Guardrails on Streaming Responses

Output guardrails can also work for operations with streaming responses:

```java
public interface StreamingAssistant {
    @OutputGuardrails({ FirstOutputGuardrail.class, SecondOutputGuardrail.class })
    TokenStream streamingChat(String message);
}
```

In this scenario, the output guardrails will be executed once the entire stream is complete, or more specifically, when `TokenStream.onCompleteResponse` is called. `onPartialResponse` will be buffered and replayed once the guardrails succeed.

In the situation where a **_retry_** or **_reprompt_** in the chain eventually succeeds, then the entire chain is re-executed _synchronously_. Each guardrail will be re-executed one after the other in the original order. Once the chain completes the result is passed into `TokenStream.onCompleteResponse`.

### Out-of-the-box Output Guardrails

There are several common use cases where implementations of an output guardrail are provided by LangChain4j:

| Guardrail class                                                                                                                                                                   | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`JsonExtractorOutputGuardrail`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/JsonExtractorOutputGuardrail.java) | An output guardrail that will check whether or not a response can be successfully deserialized from JSON to an object of a certain type.<br/> - Uses a [Jackson ObjectMapper](https://github.com/FasterXML/jackson-databind) to try and deserialize an object.<br/> - The LLM is reprompted if the response can't be deserialized into the expected object type.<br/> - Can be used as-is, or can be extended and customized (there are several `protected` methods that can be overridden to customize behavior). |

### Unit Testing Output Guardrails

There are some unit testing utilities based on [AssertJ](https://assertj.github.io/doc/) in the `langchain4j-test` module.

<Tabs>
  <TabItem value="maven" label="Maven" default>
    ```xml
    <dependency>
      <groupId>dev.langchain4j</groupId>
      <artifactId>langchain4j-test</artifactId>
      <scope>test</scope>
    </dependency>
    ```
  </TabItem>
  <TabItem value="gradleGroovy" label="Gradle (Groovy)">
    ```groovy
    testImplementation 'dev.langchain4j:langchain4j-test'
    ```
  </TabItem>
  <TabItem value="gradleKotlin" label="Gradle (Kotlin)">
    ```kotlin
    testImplementation("dev.langchain4j:langchain4j-test")
    ```
  </TabItem>
</Tabs>

Once you have the dependency, you can perform these kinds of validations:

```java
import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.GuardrailResult.Result;

class Tests { 
    MyOutputGuardrail outputGuardrail = new MyOutputGuardrail();
    
    @Test 
    void test() {
        var aiMessage = AiMessage.from("Some output");
        var result = outputGuardrail.validate(aiMessage);
        
        // These are just some examples of what you can do
        assertThat(result)
                .isSuccessful()
                .hasResult(Result.FATAL)
                .hasFailures()
                .hasSingleFailureWithMessage("Hallucination detected!")
                .hasSingleFailureWithMessageAndReprompt("Hallucination detected!", "Please LLM don't hallucinate!")
                .assertSingleFailureSatisfied(failure -> assertThat(failure)...)
                .withFailures().....
    }
}
```

:::info
See the [`GuardrailAssertions`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-test/src/main/java/dev/langchain4j/test/guardrail/GuardrailAssertions.java) and [`OutputGuardrailResultAssert`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-test/src/main/java/dev/langchain4j/test/guardrail/OutputGuardrailResultAssert.java) classes for more details.
:::

## Mixing and matching

You can mix and match input and output guardrails however you like!

```java
public class MyObjectJsonOutputGuardrail extends JsonExtractorOutputGuardrail<MyObject> {
    public MyObjectJsonOutputGuardrail() {
        super(MyObject.class);
    }
}

@InputGuardrails({ FirstInputGuardrail.class, SecondInputGuardrail.class })
@OutputGuardrails(value = SomeOutputGuardrail.class, maxRetries = 5)
public interface Assistant {
    String chat(String message);
    
    @InputGuardrails(PromptInjectionGuardrail.class)
    @OutputGuardrails(MyObjectJsonOutputGuardrail.class)
    MyObject chatAndReturnJson(String message);
}

var outputGuardrailsConfig = OutputGuardrailsConfig.builder()
        .maxRetries(10)
        .build();

var assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .inputGuardrails(new AnotherInputGuardrail())
        .outputGuardrailsConfig(outputGuardrailsConfig)
        .build();
```

In this example, all the methods on the `Assistant` have a single input guardrail, `AnotherInputGuardrail`, because it is set on the `AiServices` builder. Additionally, all the output guardrails have a `maxRetries` value == `10`, because the config is also set on the `AiServices` builder.

The `chat` method has a single output guardrail, `SomeOutputGuardrail`, with a `maxRetries` value == `10`.

The `chatAndReturnJson` method a single output guardrail, `MyObjectJsonOutputGuardrail` with a `maxRetries` value == `10`.

## Extension points

The guardrail system was built in a composable way so it can be extended and reused in other downstream frameworks (such as [Quarkus](https://quarkus.io) or [Spring Boot](https://spring.io/projects/spring-boot)). This section describes some of the extension points or "hooks" that are provided.

All of these extension points utilize the [Java Service Provider Interface (Java SPI)](https://www.baeldung.com/java-spi).

| Extension point interface                                                                                                                                                                                    | Purpose                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`ClassInstanceFactory`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/spi/classloading/ClassInstanceFactory.java)                                     | Provides instanceos of classes.<br/> - Intended to delegate instance creation/retrieval to some other means.<br/> - If not provided, uses reflection to create an instance using the default constructor.<br/> - Other frameworks (like Quarkus or Spring) may use their own bean containers to provide instances of classes. Those frameworks would provide an implementation.<br/> - A Quarkus implementation may look something like [`CDIClassInstanceFactory`](https://github.com/langchain4j/langchain4j/blob/main/integration-tests/integration-tests-class-instance-loader/integration-tests-class-instance-loader-quarkus/src/main/java/com/example/CDIClassInstanceFactory.java)<br/> - A Spring implementation may look something like [`ApplicationContextClassInstanceFactory`](https://github.com/langchain4j/langchain4j/blob/main/integration-tests/integration-tests-class-instance-loader/integration-tests-class-instance-loader-spring/src/main/java/com/example/classes/ApplicationContextClassInstanceFactory.java) |
| [`ClassMetadataProviderFactory`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/spi/classloading/ClassMetadataProviderFactory.java)                     | Provides access to class metadata.<br/> - Used to scan the methods on `AiService` interfaces, and find and process the `@InputGuardrails`/`@OutputGuardrails` annotations.<br/> - [`ReflectionBasedClassMetadataProviderFactory`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/classloading/ReflectionBasedClassMetadataProviderFactory.java) is the default implementation if no others are found, providing class metadata using reflection.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| [`InputGuardrailsConfigBuilderFactory`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/spi/guardrail/config/InputGuardrailsConfigBuilderFactory.java)   | - SPI for overriding and/or extending the default [`InputGuardrailsConfigBuilder`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/config/InputGuardrailsConfigBuilder.java)<br/> - Other frameworks may provide their own implementation with extra additional configuration for input guardrails.<br/> - Would also allow other frameworks to drive input guardrail configuration via some other mechanism (i.e. a properties file).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| [`OutputGuardrailsConfigBuilderFactory`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/spi/guardrail/config/OutputGuardrailsConfigBuilderFactory.java) | - SPI for overriding and/or extending the default [`OutputGuardrailsConfigBuilder`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/config/OutputGuardrailsConfigBuilder.java)<br/> - Other frameworks may provide their own implementation with extra additional configuration for output guardrails.<br/> - Would also allow other frameworks to drive output guardrail configuration via some other mechanism (i.e. a properties file).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |

