---
sidebar_position: 8
---

# Google Vertex AI Gemini

Vertex AI is Google Cloud's fully-managed AI development platform that provides access to Google's large generative models, including the older generation (PaLM2) and the newer generation (Gemini).

To utilize Vertex AI, one must first create a Google Cloud Platform account.

## Get started

### Create Google Cloud Account

If you're new to Google Cloud, you can create a new account by clicking on the `[create an account]` button located under `Get set up on Google Cloud` dropdown menu on the following page:

[Create an account](https://cloud.google.com/vertex-ai/generative-ai/docs/start/quickstarts/quickstart-multimodal#new-to-google-cloud)

### Create a project within your Google Cloud Platform account.

Within your Google Cloud Account create a new project and enable the Vertex AI APIs by following the steps outlined below:

[Create a new project](https://cloud.google.com/vertex-ai/docs/start/cloud-environment#set_up_a_project)

Note your `PROJECT_ID` as it will be required for future API calls.

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

This authentication method is compatible with both the `vertex-ai` (Embedding models, PaLM2) and `vertex-ai-gemini` (Gemini) packages.

## Add dependencies

To get started, add the following dependencies to your project's `pom.xml`:

```xml
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-vertex-ai-gemini</artifactId>
  <version>1.0.1-beta6</version>
</dependency>
```

or project's `build.gradle`:

```groovy
implementation 'dev.langchain4j:langchain4j-vertex-ai-gemini:1.0.1-beta6'
```

### Try out an example code:

[Example of using chat model for text prediction](https://github.com/langchain4j/langchain4j-examples/blob/main/vertex-ai-gemini-examples/src/main/java/VertexAiGeminiChatModelExamples.java)

[Gemini Pro Vision with Image input](https://github.com/langchain4j/langchain4j/blob/657aac9519b57afc04ea434ddcfa70d701923b91/langchain4j-vertex-ai-gemini/src/test/java/dev/langchain4j/model/vertexai/VertexAiGeminiChatModelIT.java#L123)

The `PROJECT_ID` field represents the variable you set when creating a new Google Cloud project.

```java
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;

public class GeminiProVisionWithImageInput {

    private static final String PROJECT_ID = "YOUR-PROJECT-ID";
    private static final String LOCATION = "us-central1";
    private static final String MODEL_NAME = "gemini-1.5-flash";
    private static final String CAT_IMAGE_URL = "https://upload.wikimedia.org/" +
        "wikipedia/commons/e/e9/" +
        "Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    public static void main(String[] args) {
        ChatModel visionModel = VertexAiGeminiChatModel.builder()
            .project(PROJECT_ID)
            .location(LOCATION)
            .modelName(MODEL_NAME)
            .build();

        ChatResponse response = visionModel.chat(
            UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                TextContent.from("What do you see?")
            )
        );
        
        System.out.println(response.aiMessage().text());
    }
}
```

Streaming is also supported thanks to the `VertexAiGeminiStreamingChatModel` class:

```java
var model = VertexAiGeminiStreamingChatModel.builder()
        .project(PROJECT_ID)
        .location(LOCATION)
        .modelName(GEMINI_1_5_PRO)
        .build();

model.chat("Why is the sky blue?", new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        System.print(partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse){
        System.print(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

You can use the shortcut `onPartialResponse()` and `onPartialResponseAndError()` utility functions from `LambdaStreamingResponseHandler`:

```java
model.chat("Why is the sky blue?", onPartialResponse(System.out::print));
model.chat("Why is the sky blue?", onPartialResponseAndError(System.out::print, Throwable::printStackTrace));
```

### Available models

| Model name                | Description                                                                                                                         | Inputs                                                  | Properties                                            |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|-------------------------------------------------------|
| `gemini-1.5-flash`        | Provides speed and efficiency for high-volume, quality, cost-effective apps.                                                        | Text, code, images, audio, video, video with audio, PDF | Max input tokens: 1,048,576, Max output tokens: 8,192 |
| `gemini-1.5-pro`          | Supports text or chat prompts for a text or code response. Supports long-context understanding up to the maximum input token limit. | Text, code, images, audio, video, video with audio, PDF | Max input tokens: 2,097,152, Max output tokens: 8,192 |
| `gemini-1.0-pro`          | The best performing model for a wide range of text-only tasks.                                                                      | Text                                                    | Max input tokens: 32,760, Max output tokens: 8,192    |
| `gemini-1.0-pro-vision`   | The best performing image and video understanding model to handle a broad range of applications.                                    | Text, images, audio, video, video with audio, PDF       | Max input tokens: 16,384, Max output tokens: 2,048    |
| `gemini-1.0-ultra`        | The most capable text model, optimized for complex tasks, including instruction, code, and reasoning.                               | Text                                                    | Max tokens input: 8,192, Max tokens output: 2,048     |
| `gemini-1.0-ultra-vision` | The most capable multimodal vision model. Optimized to support joint text, images, and video inputs.                                | Text, code, images, audio, video, video with audio, PDF | Max tokens input: 8,192, Max tokens output: 2,048     |

You can learn more about the models in the [Gemini model documentation page](https://cloud.google.com/vertex-ai/generative-ai/docs/learn/models)

Note that in March 2024, the Ultra version has private access with an allow list. Therefore, you may receive an exception similar to this:

```text
Caused by: io.grpc.StatusRuntimeException:
 FAILED_PRECONDITION: Project `1234567890` is not allowed to use Publisher Model
  `projects/{YOUR_PROJECT_ID}/locations/us-central1/publishers/google/models/gemini-ultra`
```

## Configuration

```java
ChatModel model = VertexAiGeminiChatModel.builder()
    .project(PROJECT_ID)        // your Google Cloud project ID
    .location(LOCATION)         // the region where AI inference should take place
    .modelName(MODEL_NAME)      // the model used
    .logRequests(true)          // log input requests
    .logResponses(true)         // log output responses
    .maxOutputTokens(8192)      // the maximum number of tokens to generate (up to 8192)
    .temperature(0.7)           // temperature (between 0 and 2)
    .topP(0.95)                 // topP (between 0 and 1) — cumulative probability of the most probable tokens
    .topK(3)                    // topK (positive integer) — pick a token among the most probable ones
    .seed(1234)                 // seed for the random number generator
    .maxRetries(2)              // maximum number of retries
    .responseMimeType("application/json") // to get JSON structured outputs
    .responseSchema(/*...*/)    // structured output following the provided schema
    .safetySettings(/*...*/)    // specify safety settings to filter inappropriate content
    .useGoogleSearch(true)      // to ground responses with Google Search results
    .vertexSearchDatastore(name)// to ground responses with data backed documents 
                                // from a custom Vertex AI Search datastore
    .toolCallingMode(/*...*/)   // AUTO (automatic), ANY (from a list of functions), NONE
    .allowedFunctionNames(/*...*/) // when using ANY tool calling mode, 
                                // specify the allowed function names to be called
    .listeners(/*...*/)         // list of listeners to receive model events
    .build();
```

The same parameters are also available on the streaming chat model.

## More examples

Gemini is a `multimodal` model which accepts text, but also images, audio and video files, as well as PDFs in input.

### Describing the content of an image

```java
ChatModel model = VertexAiGeminiChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName(GEMINI_1_5_PRO)
    .build();

UserMessage userMessage = UserMessage.from(
    ImageContent.from(CAT_IMAGE_URL),
    TextContent.from("What do you see? Reply in one word.")
);

ChatResponse response = model.chat(userMessage);
```

The URL can be a web URL, or can point at a file stored in Google Cloud Storage buckets,
like `gs://my-bucket/my-image.png`.

You can also pass the content of an image as Base64 encoded string:

```java
String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
UserMessage userMessage = UserMessage.from(
        ImageContent.from(base64Data, "image/png"),
        TextContent.from("What do you see? Reply in one word.")
);
```

### Asking questions about a PDF document

```java
var model = VertexAiGeminiChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName(GEMINI_1_5_PRO)
    .logRequests(true)
    .logResponses(true)
    .build();

UserMessage message = UserMessage.from(
    PdfFileContent.from(Paths.get("src/test/resources/gemini-doc-snapshot.pdf").toUri()),
    TextContent.from("Provide a summary of the document")
);

ChatResponse response = model.chat(message);
```

### Tool calling

```java
ChatModel model = VertexAiGeminiChatModel.builder()
        .project(PROJECT_ID)
        .location(LOCATION)
        .modelName(GEMINI_1_5_PRO)
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
        .messages(UserMessage.from("What is the weather in Paris?"))
        .toolSpecifications(weatherToolSpec)
        .build();

ChatResponse response = model.chat(request);
```

The model will reply back with a tool execution request instead of a text message.
Your responsibility will be to provide the model with the response of that execution request,
by sending a `ToolExecutionResultMessage` back to the model.
The model will then be able to reply with a text response.

Parallel function calling is also supported, when the model asks to make multiple tool execution requests in a single response.

### Tool support with AiServices

You can use `AiServices` to create your own assistants powered by tools.
The following example shows a `Calculator` tool to do some math calculations,
an `Assistant` interface to specify the contract of our assistant,
then we configure `AiServices` to use Gemini, with a chat memory, and the calculator tool.

```java
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

### Grounding responses with Google Search results

LLMs don't necessarily know tha answer to all possible questions!
It's even more the case for recent events or information that have happened past the end of their last training.
It's possible to _ground_ Gemini's answers with fresh results from Google Search results:

```java
var modelWithSearch = VertexAiGeminiChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName("gemini-1.5-flash-001")
    .useGoogleSearch(true)
    .build();

String resp = modelWithSearch.chat("What is the score of yesterday's football match from Paris Saint Germain?");
```

### Grounding responses with Vertex AI Search results

When working with private internal information, documents, data, you can use 
[Vertex AI Search datastores](https://cloud.google.com/generative-ai-app-builder/docs/create-data-store-es) to hold those documents.
You can then ground Gemini's answers with those documents:

```java
var modelWithSearch = VertexAiGeminiChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName("gemini-1.5-flash-001")
    .vertexSearchDatastore("name_of_the_datastore")
    .build();
```

### JSON structured output

You can ask Gemini to return only valid JSON outputs:

```java
var modelWithResponseMimeType = VertexAiGeminiChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName("gemini-1.5-flash-001")
    .responseMimeType("application/json")
    .build();

String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler.";
String jsonResponse = modelWithResponseMimeType.chat(userMessage).content().text();
// {"name": "Klaus", "surname": "Heisler"}
```

### Strict JSON structured output with JSON schemas

With `responseMimeType("application/json)` the model can still be a bit creative in the way it responds
if ever your prompt didn't precisely describe the desired JSON output.
To ensure a stricter JSON structured output, you can specify a JSON schema for the response:

```java
Schema schema = Schema.newBuilder()
    .setType(Type.OBJECT)
    .putProperties("name", Schema.newBuilder()
        .setType(Type.STRING)
        .build())
    .putProperties("address", Schema.newBuilder()
        .setType(Type.OBJECT)
        .putProperties("street", 
            Schema.newBuilder().setType(Type.STRING).build())
        .putProperties("zipcode",
           Schema.newBuilder().setType(Type.STRING).build())
    .build())
.build();

var model = VertexAiGeminiChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName(GEMINI_1_5_PRO)
    .responseMimeType("application/json")
    .responseSchema(Schema)
    .build();
```

A convenience method allows you to generate a schema for a Java class:

```java
class Artist {
    public String artistName;
    int artistAge;
    protected boolean artistAdult;
    private String artistAddress;
    public Pet[] pets;
}

class Pet {
    public String name;
}

Schema schema = SchemaHelper.fromClass(Artist.class);

var model = VertexAiGeminiChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName(GEMINI_1_5_PRO)
    .responseMimeType("application/json")
    .responseSchema(schema)
    .build();
```

Another method allows you to create a schema from a JSON schema string:
`SchemaHelper.fromJson(...)`.

Gemini supports both JSON objects and arrays as structured output, 
but there's also a special case for a JSON string enum as output,
which is particularly interesting when asking Gemini to do classification tasks 
(like sentiment analysis):

```java
var model = VertexAiGeminiChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName(GEMINI_1_5_PRO)
    .logRequests(true)
    .logResponses(true)
    .responseSchema(Schema.newBuilder()
        .setType(Type.STRING)
        .addAllEnum(Arrays.asList("POSITIVE", "NEUTRAL", "NEGATIVE"))
        .build())
    .build();
```

In this case, the implicit response mime type is set to `text/x.enum` 
(which is not an official registered mime type).

### Specify safety settings

If you want to filter or block harmful content, you can set safety settings with different threshold levels:

```java
HashMap<HarmCategory, SafetyThreshold> safetySettings = new HashMap<>();
safetySettings.put(HARM_CATEGORY_HARASSMENT, BLOCK_LOW_AND_ABOVE);
safetySettings.put(HARM_CATEGORY_DANGEROUS_CONTENT, BLOCK_ONLY_HIGH);
safetySettings.put(HARM_CATEGORY_SEXUALLY_EXPLICIT, BLOCK_MEDIUM_AND_ABOVE);

var model = VertexAiGeminiChatModel.builder()
    .project(PROJECT_ID)
    .location(LOCATION)
    .modelName("gemini-1.5-flash-001")
    .safetySettings(safetySettings)
    .logRequests(true)
    .logResponses(true)
    .build();
```

## References

[Available locations](https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations#available-regions)

[Multimodal capabilities](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/overview#multimodal_models)


## Examples

- [Google Vertex AI Gemini Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/vertex-ai-gemini-examples/src/main/java)
