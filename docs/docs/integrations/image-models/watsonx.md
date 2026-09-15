---
sidebar_position: 10
---

# watsonx.ai

- [watsonx.ai API Reference](https://cloud.ibm.com/apidocs/watsonx-ai)
- [watsonx.ai Java SDK](https://github.com/IBM/watsonx-ai-java-sdk)
- [watsonx.ai Java SDK documentation](https://ibm.github.io/watsonx-ai-java-sdk/services/model-gateway/image-generation)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-watsonx</artifactId>
    <version>1.20.0-beta30</version>
</dependency>
```

## Model Gateway

Image generation is available only through the IBM watsonx.ai **Model Gateway**, which exposes an images endpoint compatible with the OpenAI one and routes the requests to the models hosted by the providers registered in it. There is no foundation model counterpart, so `WatsonxGatewayImageModel` is the only image model of this integration.

> **Note:** the gateway must be configured by an administrator before use. Every `modelName` you pass must be an id already registered in the gateway.

## WatsonxGatewayImageModel

The `WatsonxGatewayImageModel` implements the LangChain4j `ImageModel` interface. To create an instance, specify:

- `baseUrl(...)` – IBM Cloud endpoint URL (as `String`, `URI`, or `CloudRegion`)
- `apiKey(...)` – IBM Cloud IAM API key (or a full `Authenticator` via `.authenticator(...)`)
- `modelName(...)` – image model id registered in the gateway, written in the OpenAI style

No `projectId` or `spaceId` is required, the gateway resolves the model on its own.

```java
import com.ibm.watsonx.ai.CloudRegion;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayImageModel;

ImageModel imageModel = WatsonxGatewayImageModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .modelName("gpt-image-1")
    .build();

Image image = imageModel.generate("A futuristic city at sunset").content();
```

### Generating more than one image

```java
Response<List<Image>> response = imageModel.generate("A futuristic city at sunset", 3);

for (Image image : response.content()) {
    System.out.println(image.base64Data());
}
```

The number of images accepted per request depends on the model, so a value that one model allows can be rejected by another one.

### Saving the generated image

An image is returned either as a link or as Base64 data, depending on the model and on the response format that was requested.

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

Image image = imageModel.generate("A futuristic city at sunset").content();

if (image.base64Data() != null) {
    Files.write(Path.of("image.png"), Base64.getDecoder().decode(image.base64Data()));
} else {
    System.out.println(image.url());
}
```

When the model reports the format of the images it generated, `mimeType()` carries it, for example `image/png`.

## Parameters

Every parameter can be set once on the builder and applies to all the requests.

| Builder method | Description |
|---|---|
| `background(...)` | Background of the generated images, `TRANSPARENT`, `OPAQUE` or `AUTO`. Transparency requires an output format that supports it, so `PNG` or `WEBP` |
| `moderation(...)` | How strictly the generated images are filtered, `LOW` or `AUTO` |
| `outputCompression(...)` | Compression level from 0 to 100, accepted only by the `JPEG` and `WEBP` formats |
| `outputFormat(...)` | File format of the generated images, `PNG`, `JPEG`, `WEBP` or `AUTO` |
| `quality(...)` | Quality of the generated images, `AUTO`, `HIGH`, `MEDIUM`, `LOW`, `HD` or `STANDARD` |
| `responseFormat(...)` | How the images are returned, `URL` for a link or `B64_JSON` for Base64 data |
| `size(...)` | Dimensions of the generated images, for example `SIZE_1024X1024` |
| `style(...)` | Visual style of the generated images, `VIVID` or `NATURAL` |
| `user(...)` | Identifier of the end user, used for abuse monitoring |

Each of them also has an overload that takes a `String`, useful for the values that the enums do not cover yet.

```java
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.OutputFormat;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Quality;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Size;

ImageModel imageModel = WatsonxGatewayImageModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .modelName("gpt-image-1")
    .size(Size.SIZE_1024X1024)
    .quality(Quality.LOW)
    .outputFormat(OutputFormat.PNG)
    .build();
```

### Per request parameters

The same values can be passed per request through `ModelGatewayImageParameters`. The given parameters replace the ones set on the builder, they are not merged with them.

```java
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters;

WatsonxGatewayImageModel imageModel = WatsonxGatewayImageModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .modelName("gpt-image-1")
    .build();

ModelGatewayImageParameters parameters = ModelGatewayImageParameters.builder()
    .size(Size.SIZE_1024X1024)
    .quality(Quality.HIGH)
    .n(2)
    .build();

Response<List<Image>> response = imageModel.generate("A futuristic city at sunset", parameters);
```

`n` and `partialImages` are available only here, they are not on the builder. The number of images is already part of the LangChain4j image request API through `generate(prompt, n)`, and `partialImages` has no effect because the endpoint does not stream the result.

## Model specific behavior

The gateway forwards the request to the provider of the model, so not every parameter is honored by every model.

| Model | Behavior |
|---|---|
| `gpt-image-1` | Always answers with Base64 data, it ignores `responseFormat`. It is the only model that reports the token usage |
| `dall-e-3` | Honors `responseFormat` and it is the only model that returns a revised prompt, readable with `image.revisedPrompt()` |
| `dall-e-2` | Honors `responseFormat`, it does not support `quality` or `style` |

The defaults applied when a parameter is left unset are `responseFormat` as `url`, `outputFormat` as `jpeg`, `size` as `1024x1024`, `quality` as `auto` and one single image.

## Token usage

The token usage is reported only by the models that count the tokens of the prompt, so `response.tokenUsage()` can be `null`.

```java
Response<Image> response = imageModel.generate("A futuristic city at sunset");

if (response.tokenUsage() != null) {
    System.out.println(response.tokenUsage().totalTokenCount());
}
```

## Unsupported operations

The endpoint generates images from a prompt only, so it has no counterpart for the editing methods of the `ImageModel` interface. Calling `edit(Image, String)` or `edit(Image, Image, String)` throws an `IllegalArgumentException`.

## Authentication

Watsonx.ai supports authentication via the `Authenticator` interface.

This allows you to use different authentication mechanisms depending on your deployment:

- **IBMCloudAuthenticator** – authenticates with **IBM Cloud** using an API key. This is the simplest approach and is used when you provide the `apiKey(...)` builder method.
- **CP4DAuthenticator** – authenticates with **Cloud Pak for Data** deployments.
- **Custom authenticators** – any implementation of the `Authenticator` interface can be used.

The `WatsonxGatewayImageModel` and other service builders accept either a shortcut via `.apiKey(...)` or a full `Authenticator` instance via `.authenticator(...)`.

### Example

```java
WatsonxGatewayImageModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key") // Simple IBM Cloud authentication
    .modelName("gpt-image-1")
    .build();

WatsonxGatewayImageModel.builder()
    .baseUrl("https://my-instance-url")
    .authenticator( // For Cloud Pak for Data deployments
        CP4DAuthenticator.builder()
            .baseUrl("https://my-instance-url")
            .username("username")
            .apiKey("api-key")
            .build()
    )
    .modelName("gpt-image-1")
    .build();
```
