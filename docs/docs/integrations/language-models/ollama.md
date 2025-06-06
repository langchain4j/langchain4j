---
sidebar_position: 14
---

# Ollama

### What is Ollama?

Ollama is an advanced AI tool that allows users to easily set up and run large language models
locally (in CPU and GPU modes). With Ollama, users can leverage powerful language models such as
Llama 2 and even customize and create their own models. Ollama bundles model weights, configuration,
and data into a single package, defined by a Modelfile. It optimizes setup and configuration
details, including GPU usage.

For more details about Ollama, check these out:

- https://ollama.ai/
- https://github.com/jmorganca/ollama

### Talks

Watch this presentation at [Docker Con 23](https://www.dockercon.com/2023/program):

<iframe width="640" height="480" src="https://www.youtube.com/embed/yPuhGtJT55o" title="Introducing Docker’s Generative AI and Machine Learning Stack (DockerCon 2023)" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

Watch this intro by [Code to the Moon](https://www.youtube.com/@codetothemoon):

<iframe width="640" height="480" src="https://www.youtube.com/embed/jib1wjgIaa4" title="this open source project has a bright future" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

### Get started

To get started, add the following dependencies to your project's `pom.xml`:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
    <version>1.0.1-beta6</version>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>ollama</artifactId>
    <version>1.19.1</version>
</dependency>
```

Try out a simple chat example code when Ollama runs in testcontainers:

```java
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.List;

public class OllamaChatExample {

  private static final Logger log = LoggerFactory.getLogger(OllamaChatExample.class);

  static final String OLLAMA_IMAGE = "ollama/ollama:latest";
  static final String TINY_DOLPHIN_MODEL = "tinydolphin";
  static final String DOCKER_IMAGE_NAME = "tc-ollama/ollama:latest-tinydolphin";

  public static void main(String[] args) {
    // Create and start the Ollama container
    DockerImageName dockerImageName = DockerImageName.parse(OLLAMA_IMAGE);
    DockerClient dockerClient = DockerClientFactory.instance().client();
    List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(DOCKER_IMAGE_NAME).exec();
    OllamaContainer ollama;
    if (images.isEmpty()) {
        ollama = new OllamaContainer(dockerImageName);
    } else {
        ollama = new OllamaContainer(DockerImageName.parse(DOCKER_IMAGE_NAME).asCompatibleSubstituteFor(OLLAMA_IMAGE));
    }
    ollama.start();

    // Pull the model and create an image based on the selected model.
    try {
        log.info("Start pulling the '{}' model ... would take several minutes ...", TINY_DOLPHIN_MODEL);
        Container.ExecResult r = ollama.execInContainer("ollama", "pull", TINY_DOLPHIN_MODEL);
        log.info("Model pulling competed! {}", r);
    } catch (IOException | InterruptedException e) {
        throw new RuntimeException("Error pulling model", e);
    }
    ollama.commitToImage(DOCKER_IMAGE_NAME);

    // Build the ChatModel
    ChatModel model = OllamaChatModel.builder()
            .baseUrl(ollama.getEndpoint())
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .modelName(TINY_DOLPHIN_MODEL)
            .build();

    // Example usage
    String answer = model.chat("Provide 3 short bullet points explaining why Java is awesome");
    System.out.println(answer);

    // Stop the Ollama container
    ollama.stop();
  }
}

```

If your Ollama runs locally, you can also try below chat example code:

```java
class OllamaChatLocalModelTest {
  static String MODEL_NAME = "llama3.2"; // try other local ollama model names
  static String BASE_URL = "http://localhost:11434"; // local ollama base url

  public static void main(String[] args) {
      ChatModel model = OllamaChatModel.builder()
              .baseUrl(BASE_URL)
              .modelName(MODEL_NAME)
              .build();
      String answer = model.chat("List top 10 cites in China");
      System.out.println(answer);

      model = OllamaChatModel.builder()
              .baseUrl(BASE_URL)
              .modelName(MODEL_NAME)
              .responseFormat(JSON)
              .build();

      String json = model.chat("List top 10 cites in US");
      System.out.println(json);
    }
}
```

Try out a simple streaming chat example code when Ollama runs in testcontainers:

```java
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OllamaStreamingChatExample {

  private static final Logger log = LoggerFactory.getLogger(OllamaStreamingChatExample.class);

  static final String OLLAMA_IMAGE = "ollama/ollama:latest";
  static final String TINY_DOLPHIN_MODEL = "tinydolphin";
  static final String DOCKER_IMAGE_NAME = "tc-ollama/ollama:latest-tinydolphin";

  public static void main(String[] args) {
    DockerImageName dockerImageName = DockerImageName.parse(OLLAMA_IMAGE);
    DockerClient dockerClient = DockerClientFactory.instance().client();
    List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(DOCKER_IMAGE_NAME).exec();
    OllamaContainer ollama;
    if (images.isEmpty()) {
        ollama = new OllamaContainer(dockerImageName);
    } else {
        ollama = new OllamaContainer(DockerImageName.parse(DOCKER_IMAGE_NAME).asCompatibleSubstituteFor(OLLAMA_IMAGE));
    }
    ollama.start();
    try {
        log.info("Start pulling the '{}' model ... would take several minutes ...", TINY_DOLPHIN_MODEL);
        Container.ExecResult r = ollama.execInContainer("ollama", "pull", TINY_DOLPHIN_MODEL);
        log.info("Model pulling competed! {}", r);
    } catch (IOException | InterruptedException e) {
        throw new RuntimeException("Error pulling model", e);
    }
    ollama.commitToImage(DOCKER_IMAGE_NAME);

    StreamingChatModel model = OllamaStreamingChatModel.builder()
            .baseUrl(ollama.getEndpoint())
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .modelName(TINY_DOLPHIN_MODEL)
            .build();

    String userMessage = "Write a 100-word poem about Java and AI";

    CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
    model.chat(userMessage, new StreamingChatResponseHandler() {

        @Override
        public void onPartialResponse(String partialResponse) {
            System.out.print(partialResponse);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            futureResponse.complete(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            futureResponse.completeExceptionally(error);
        }
    });

    futureResponse.join();
    ollama.stop();
  }
}
```

If your Ollama runs locally, you can also try below streaming chat example code:
```java
class OllamaStreamingChatLocalModelTest {
  static String MODEL_NAME = "llama3.2"; // try other local ollama model names
  static String BASE_URL = "http://localhost:11434"; // local ollama base url

  public static void main(String[] args) {
      StreamingChatModel model = OllamaStreamingChatModel.builder()
              .baseUrl(BASE_URL)
              .modelName(MODEL_NAME)
              .temperature(0.0)
              .build();
      String userMessage = "Write a 100-word poem about Java and AI";

      CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
      model.chat(userMessage, new StreamingChatResponseHandler() {

          @Override
          public void onPartialResponse(String partialResponse) {
              System.out.print(partialResponse);
          }

          @Override
          public void onCompleteResponse(ChatResponse completeResponse) {
              futureResponse.complete(completeResponse);
          }

          @Override
          public void onError(Throwable error) {
              futureResponse.completeExceptionally(error);
          }
      });

      futureResponse.join();
  }
}
```

### Parameters

`OllamaChatModel` and `OllamaStreamingChatModel` classes can be instantiated with the following
params with the builder pattern:

| Parameter        | Description                                                                                                                                                                       | Type             | Example                |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|------------------------|
| `baseUrl`        | The base URL of Ollama server.                                                                                                                                                    | `String`         | http://localhost:11434 |
| `modelName`      | The name of the model to use from Ollama server.                                                                                                                                  | `String`         |                        |
| `temperature`    | Controls the randomness of the generated responses. Higher values (e.g., 1.0) result in more diverse output, while lower values (e.g., 0.2) produce more deterministic responses. | `Double`         |                        |
| `topK`           | Specifies the number of highest probability tokens to consider for each step during generation.                                                                                   | `Integer`        |                        |
| `topP`           | Controls the diversity of the generated responses by setting a threshold for the cumulative probability of top tokens.                                                            | `Double`         |                        |
| `repeatPenalty`  | Penalizes the model for repeating similar tokens in the generated output.                                                                                                         | `Double`         |                        |
| `seed`           | Sets the random seed for reproducibility of generated responses.                                                                                                                  | `Integer`        |                        |
| `numPredict`     | The number of predictions to generate for each input prompt.                                                                                                                      | `Integer`        |                        |
| `stop`           | A list of strings that, if generated, will mark the end of the response.                                                                                                          | `List<String>`   |                        |
| `format`         | The desired format for the generated output. (**Depracated** see **responseFormat**)                                                                                              | `String`         |                        |
| `responseFormat` | The desired format for the generated output. TEXT or JSON with optional JSON Schema definition                                                                                    | `ResponseFormat` |                        |
| `supportedCapabilities` | Set of model capabilities used by `AiServices` API (only `OllamaChatModel` supported)                                                                                             | `Capability` | RESPONSE_FORMAT_JSON_SCHEMA |
| `timeout`        | The maximum time allowed for the API call to complete.                                                                                                                            | `Duration`       | PT60S                  |
| `maxRetries`     | The maximum number of retries in case of API call failure.                                                                                                                        | `Integer`        |                        |

#### Usage Example
```java
OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama3.1")
    .temperature(0.8)
    .timeout(Duration.ofSeconds(60))
    .build();
```

#### Usage Example with Spring Boot
```properties
langchain4j.ollama.chat-model.base-url=http://localhost:11434
langchain4j.ollama.chat-model.model-name=llama3.1
langchain4j.ollama.chat-model.temperature=0.8
langchain4j.ollama.chat-model.timeout=PT60S
```

### JSON mode

```java
OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama3.1")
    .responseFormat(ResponseFormat.JSON)    
    .temperature(0.8)
    .timeout(Duration.ofSeconds(60))
    .build();
```

### Structured Outputs

#### JSON schema definition using builder

```java
OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama3.1")
    .responseFormat(ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder().rootElement(JsonObjectSchema.builder()
                            .addProperty("name", JsonStringSchema.builder().build())
                            .addProperty("capital", JsonStringSchema.builder().build())
                            .addProperty(
                                    "languages",
                                    JsonArraySchema.builder()
                                            .items(JsonStringSchema.builder().build())
                                            .build())
                            .required("name", "capital", "languages")
                            .build())
                    .build())
            .build())
    .temperature(0.8)
    .timeout(Duration.ofSeconds(60))
    .build();
```

#### JSON Schema using ChatRequest API

```java
OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama3.1")
    .build();

ChatResponse chatResponse = ollamaChatModel.chat(ChatRequest.builder()
        .messages(userMessage("Tell me about Canada."))
        .responseFormat(ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder().rootElement(JsonObjectSchema.builder()
                                .addProperty("name", JsonStringSchema.builder().build())
                                .addProperty("capital", JsonStringSchema.builder().build())
                                .addProperty(
                                        "languages",
                                        JsonArraySchema.builder()
                                                .items(JsonStringSchema.builder().build())
                                                .build())
                                .required("name", "capital", "languages")
                                .build())
                        .build())
                .build())
        .build());

String jsonFormattedResponse = chatResponse.aiMessage().text();

/* jsonFormattedResponse value:

  {
    "capital" : "Ottawa",
    "languages" : [ "English", "French" ],
    "name" : "Canada"
  }

 */


```


### Json Schema with AiServices

When `OllamaChatModel` is created with supported capability `RESPONSE_FORMAT_JSON_SCHEMA`, `AIService` will automatically generate schema from interface return value. More about it in [Structured Outputs](/tutorials/structured-outputs.md#using-json-schema-with-ai-services)

```java
OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
    .baseUrl("...")
    .modelName("...")
    .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)    
    .build();
```

### Custom Messages

The `OllamaChatModel` and `OllamaStreamingChatModel` support custom chat messages in addition to the standard chat message types.
Custom messages can be used to specify a message with arbitrary attributes. This can be useful for
some models like [Granite Guardian](https://ollama.com/library/granite3-guardian) that make use of 
non-standard messages to assess the retrieved context used for Retrieval-Augmented Generation (RAG).

Let's see how we can use a `CustomMessage` to specify a message with arbitrary attributes:

```java
OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("granite3-guardian")
    .build();
 
String retrievedContext = "One significant part of treaty making is that signing a treaty implies recognition that the other side is a sovereign state and that the agreement being considered is enforceable under international law. Hence, nations can be very careful about terming an agreement to be a treaty. For example, within the United States, agreements between states are compacts and agreements between states and the federal government or between agencies of the government are memoranda of understanding.";

List<ChatMessage> messages = List.of(
    SystemMessage.from("context_relevance"),
    UserMessage.from("What is the history of treaty making?"),
    CustomMessage.from(Map.of(
        "role", "context",
        "content", retrievedContext
    ))
);

ChatResponse chatResponse = ollamaChatModel.chat(ChatRequest.builder().messages(messages).build());

System.out.println(chatResponse.aiMessage().text()); // "Yes" (meaning risk detected by Granite Guardian)
```
