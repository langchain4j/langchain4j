---
sidebar_position: 18
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
  <version>${lanchain4j-ollama.version}</version>
</dependency>

<dependency>
<groupId>org.testcontainers</groupId>
<artifactId>testcontainers</artifactId>
<version>1.19.1</version>
</dependency>

```

Try out a simple chat example code:

```java
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.testcontainers.containers.GenericContainer;

public class OllamaChatExample {

  public static void main(String[] args) {
    // The model name to use (e.g., "orca-mini", "mistral", "llama2", "codellama", "phi", or
    // "tinyllama")
    String modelName = "orca-mini";

    // Create and start the Ollama container
    GenericContainer<?> ollama =
        new GenericContainer<>("langchain4j/ollama-" + modelName + ":latest")
            .withExposedPorts(11434);
    ollama.start();

    // Build the ChatLanguageModel
    ChatLanguageModel model =
        OllamaChatModel.builder().baseUrl(baseUrl(ollama)).modelName(modelName).build();

    // Example usage
    String answer = model.generate("Provide 3 short bullet points explaining why Java is awesome");
    System.out.println(answer);

    // Stop the Ollama container
    ollama.stop();
  }

  private static String baseUrl(GenericContainer<?> ollama) {
    return String.format("http://%s:%d", ollama.getHost(), ollama.getFirstMappedPort());
  }
}

```

Try out a simple streaming chat example code:

```java
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.CompletableFuture;

public class OllamaStreamingChatExample {

  static String MODEL_NAME = "orca-mini"; // try "mistral", "llama2", "codellama" or "phi"
  static String DOCKER_IMAGE_NAME = "langchain4j/ollama-" + MODEL_NAME + ":latest";
  static Integer PORT = 11434;

  static GenericContainer<?> ollama = new GenericContainer<>(DOCKER_IMAGE_NAME)
      .withExposedPorts(PORT);

  public static void main(String[] args) {
    ollama.start();
    StreamingChatLanguageModel model = OllamaStreamingChatModel.builder()
        .baseUrl(String.format("http://%s:%d", ollama.getHost(), ollama.getMappedPort(PORT)))
        .modelName(MODEL_NAME)
        .temperature(0.0)
        .build();

    String userMessage = "Write a 100-word poem about Java and AI";

    CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();
    model.generate(userMessage, new StreamingResponseHandler<AiMessage>() {

      @Override
      public void onNext(String token) {
        System.out.print(token);
      }

      @Override
      public void onComplete(Response<AiMessage> response) {
        futureResponse.complete(response);
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

### Parameters

`OllamaChatModel` and `OllamaStreamingChatModel` classes can be instantiated with the following
params with the builder pattern:

| Parameter       | Description                                                                                                                                                                       | Type           |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| `baseUrl`       | The base URL of Ollama server.                                                                                                                                                    | `String`       |
| `modelName`     | The name of the model to use from Ollama server.                                                                                                                                  | `String`       |
| `temperature`   | Controls the randomness of the generated responses. Higher values (e.g., 1.0) result in more diverse output, while lower values (e.g., 0.2) produce more deterministic responses. | `Double`       |
| `topK`          | Specifies the number of highest probability tokens to consider for each step during generation.                                                                                   | `Integer`      |
| `topP`          | Controls the diversity of the generated responses by setting a threshold for the cumulative probability of top tokens.                                                            | `Double`       |
| `repeatPenalty` | Penalizes the model for repeating similar tokens in the generated output.                                                                                                         | `Double`       |
| `seed`          | Sets the random seed for reproducibility of generated responses.                                                                                                                  | `Integer`      |
| `numPredict`    | The number of predictions to generate for each input prompt.                                                                                                                      | `Integer`      |
| `stop`          | A list of strings that, if generated, will mark the end of the response.                                                                                                          | `List<String>` |
| `format`        | The desired format for the generated output.                                                                                                                                      | `String`       |
| `timeout`       | The maximum time allowed for the API call to complete.                                                                                                                            | `Duration`     |
| `maxRetries`    | The maximum number of retries in case of API call failure.                                                                                                                        | `Integer`      |

#### Usage Example:

```java
OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
    .baseUrl("http://your-ollama-host:your-ollama-port")
    .modelName("llama2")
    .temperature(0.8)
    .build();
```