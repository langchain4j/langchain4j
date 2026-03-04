---
sidebar_position: 9
---

# Google Vertex AI Anthropic

Google Vertex AI provides access to Anthropic's Claude models through Google Cloud Platform. This integration allows you to use Claude's advanced language capabilities while leveraging Google Cloud's infrastructure and security features.

## Get started

### Create Google Cloud Account

If you're new to Google Cloud, you can create a new account by clicking on the `[create an account]` button located under `Get set up on Google Cloud` dropdown menu on the following page:

[Create an account](https://cloud.google.com/vertex-ai/generative-ai/docs/start/quickstarts/quickstart-multimodal#new-to-google-cloud)

### Create a project within your Google Cloud Platform account

Within your Google Cloud Account create a new project and enable the Vertex AI APIs by following the steps outlined below:

[Create a new project](https://cloud.google.com/vertex-ai/docs/start/cloud-environment#set_up_a_project)

Note your `PROJECT_ID` as it will be required for future API calls.

### Enable Claude models in Vertex AI Model Garden

Claude models need to be enabled in your Google Cloud project:

1. Go to the [Vertex AI Model Garden](https://console.cloud.google.com/vertex-ai/model-garden)
2. Search for "Claude" models
3. Enable the Claude models you want to use (e.g., Claude 3.5 Sonnet, Claude 3 Opus)

### Select the Google Cloud authentication strategy

There are several ways on how your application authenticates to Google Cloud services and APIs. For example, you can create a [service account](https://cloud.google.com/docs/authentication/provide-credentials-adc#local-key) and set up environment variable `GOOGLE_APPLICATION_CREDENTIALS` to the path of the JSON file that contains your credentials.

You can discover all the authentication strategies [here](https://cloud.google.com/docs/authentication/provide-credentials-adc). But for simplicity of local testing we will be using authentication via `gcloud` utility.

### Install Google Cloud CLI (Optional)

To access your cloud projects locally, you can install `gcloud` tool by following the [installation instructions](https://cloud.google.com/sdk/docs/install). For GNU/Linux operating systems, the installation steps are as follows:

1. Download SDK:

```bash
curl -O https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-cli-467.0.0-linux-x86_64.tar.gz
```

2. Extract an archive:

```bash
tar -xf google-cloud-cli-467.0.0-linux-x86_64.tar.gz
```

3. Run an installation script:

```bash
cd google-cloud-sdk/
./install.sh
```

4. Run the following command to set up a default project and authentication credentials:

```bash
gcloud auth application-default login
```

This authentication method is compatible with the `langchain4j-vertex-ai-anthropic` package.

## Add dependencies

To get started, add the following dependencies to your project's `pom.xml`:

```xml
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-vertex-ai-anthropic</artifactId>
  <version>1.11.0-beta19</version>
</dependency>
```

or project's `build.gradle`:

```groovy
implementation 'dev.langchain4j:langchain4j-vertex-ai-anthropic:1.5.0-beta11'
```

### Try out an example code

The `PROJECT_ID` field represents the variable you set when creating a new Google Cloud project.

```java
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicChatModel;

public class VertexAiAnthropicExample {

    private static final String PROJECT_ID = "YOUR-PROJECT-ID";
    private static final String LOCATION = "us-central1";
    private static final String MODEL_NAME = "claude-3-5-sonnet-v2@20241022";

    public static void main(String[] args) {
        ChatModel model = VertexAiAnthropicChatModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .modelName(MODEL_NAME)
                .maxTokens(1000)
                .temperature(0.7)
                .build();

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello, Claude!")))
                .build());

        System.out.println(response.aiMessage().text());
    }
}
```

Streaming is also supported thanks to the `VertexAiAnthropicStreamingChatModel` class:

```java
import dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatResponseHandler;

var model = VertexAiAnthropicStreamingChatModel.builder()
        .project(PROJECT_ID)
        .location(LOCATION)
        .modelName(MODEL_NAME)
        .build();

model.

chat(ChatRequest.builder()
    .

messages(List.of(UserMessage.from("Tell me a story")))
        .

build(), new

StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse (String partialResponse){
        System.out.print(partialResponse);
    }

    @Override
    public void onCompleteResponse (ChatResponse completeResponse){
        System.out.println("\nDone!");
    }

    @Override
    public void onError (Throwable error){
        error.printStackTrace();
    }
});
```

You can use the shortcut `onPartialResponse()` and `onPartialResponseAndError()` utility functions from `LambdaStreamingResponseHandler`:

```java
import static dev.langchain4j.model.chat.response.streaming.LambdaStreamingResponseHandler.onPartialResponse;
import static dev.langchain4j.model.chat.response.streaming.LambdaStreamingResponseHandler.onPartialResponseAndError;

model.chat(ChatRequest.builder()
    .messages(List.of(UserMessage.from("Why is the sky blue?")))
    .build(), onPartialResponse(System.out::print));

model.chat(ChatRequest.builder()
    .messages(List.of(UserMessage.from("Why is the sky blue?")))
    .build(), onPartialResponseAndError(System.out::print, Throwable::printStackTrace));
```

### Available models

List of available models for [Vertex AI](https://cloud.google.com/vertex-ai/generative-ai/docs/partner-models/claude).
You can learn about the models in the [Claude model documentation](https://docs.anthropic.com/en/docs/about-claude/models).

## Configuration

```java
ChatModel model = VertexAiAnthropicChatModel.builder()
    .project(PROJECT_ID)            // your Google Cloud project ID
    .location(LOCATION)             // the region where AI inference should take place
    .modelName(MODEL_NAME)          // the Claude model used
    .maxTokens(4096)               // the maximum number of tokens to generate
    .temperature(0.7)              // temperature (between 0 and 1)
    .topP(0.95)                    // topP (between 0 and 1) — cumulative probability
    .topK(40)                      // topK (positive integer) — pick from top K tokens
    .stopSequences(Arrays.asList("Human:", "Assistant:")) // stop sequences
    .enablePromptCaching(true)     // enable prompt caching for cost/latency optimization
    .credentials(credentials)      // custom Google Cloud credentials
    .logRequests(true)             // log input requests
    .logResponses(true)            // log output responses
    .build();
```

The same parameters are also available on the streaming chat model.

## More examples

Claude is a powerful multimodal model that accepts both text and images as input.

### Vision capabilities

```java
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

ChatModel model = VertexAiAnthropicChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName("claude-3-5-sonnet-v2@20241022")
    .build();

Image image = Image.builder()
    .base64Data("base64-encoded-image-data")
    .mimeType("image/jpeg")
    .build();

UserMessage userMessage = UserMessage.from(
    ImageContent.from(image),
    TextContent.from("What do you see in this image?")
);

ChatResponse response = model.chat(ChatRequest.builder()
    .messages(List.of(userMessage))
    .build());

System.out.println(response.aiMessage().text());
```

### Tool calling

```java
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.output.structured.JsonObjectSchema;

ChatModel model = VertexAiAnthropicChatModel.builder()
        .project(PROJECT_ID)
        .location(LOCATION)
        .modelName("claude-3-5-sonnet-v2@20241022")
        .build();

ToolSpecification weatherToolSpec = ToolSpecification.builder()
        .name("getWeatherForecast")
        .description("Get the weather forecast for a location")
        .parameters(JsonObjectSchema.builder()
                .addStringProperty("location", "the location to get the weather forecast for")
                .required("location")
                .build())
        .build();

ChatRequest request = ChatRequest.builder()
        .messages(List.of(UserMessage.from("What is the weather in Paris?")))
        .toolSpecifications(List.of(weatherToolSpec))
        .build();

ChatResponse response = model.chat(request);
```

The model will reply back with a tool execution request instead of a text message.
Your responsibility will be to provide the model with the response of that execution request,
by sending a `ToolExecutionResultMessage` back to the model.
The model will then be able to reply with a text response.

### Tool support with AiServices

You can use `AiServices` to create your own assistants powered by tools.
The following example shows a `Calculator` tool to do some math calculations,
an `Assistant` interface to specify the contract of our assistant,
then we configure `AiServices` to use Claude, with a chat memory, and the calculator tool.

```java
import dev.langchain4j.service.AiServices;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

static class Calculator {
    @Tool("Adds two given numbers")
    double add(double a, double b) {
        return a + b;
    }

    @Tool("Multiplies two given numbers")
    String multiply(double a, double b) {
        return String.valueOf(a * b);
    }
}

interface Assistant {
    String chat(String userMessage);
}

Calculator calculator = new Calculator();

Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(model)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
        .tools(calculator)
        .build();

String answer = assistant.chat("How much is 74589613588 + 4786521789?");
```

### Prompt caching

Claude supports prompt caching to reduce costs and improve response times for repeated or lengthy prompts:

```java
import dev.langchain4j.data.message.SystemMessage;

ChatModel model = VertexAiAnthropicChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName("claude-3-5-sonnet-v2@20241022")
    .enablePromptCaching(true)
    .build();

SystemMessage systemMessage = SystemMessage.from(
    "You are an expert software engineer with deep knowledge of Java, " +
    "Spring Boot, microservices architecture, and cloud-native development. " +
    "Always provide detailed, production-ready code examples with proper " +
    "error handling, logging, and best practices."
);

UserMessage userMessage = UserMessage.from("How do I implement JWT authentication?");

ChatResponse response = model.chat(ChatRequest.builder()
    .messages(List.of(systemMessage, userMessage))
    .build());
```

Prompt caching provides:
- **Cost Reduction**: Up to 90% cheaper for cached content
- **Latency Improvement**: Up to 85% faster response times
- **Automatic Optimization**: No manual cache management required

### Custom authentication

You can provide custom Google Cloud credentials:

```java
import com.google.auth.oauth2.GoogleCredentials;
import java.io.FileInputStream;

GoogleCredentials credentials = GoogleCredentials.fromStream(
    new FileInputStream("path/to/service-account-key.json"));

ChatModel model = VertexAiAthropicChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName("claude-3-5-sonnet-v2@20241022")
    .credentials(credentials)
    .build();
```
 
## References

[Available locations](https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations#available-regions)

[Claude model documentation](https://docs.anthropic.com/en/docs/about-claude/models)

[Vertex AI Model Garden](https://console.cloud.google.com/vertex-ai/model-garden)
