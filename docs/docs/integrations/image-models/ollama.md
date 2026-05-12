---
sidebar_position: 9
---

# Ollama Image Generation

:::warning

Ollama image generation is experimental. The Ollama API and LangChain4j integration may change in future versions.

:::

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
    <version>${latest version here}</version>
</dependency>
```

## API

- `OllamaImageModel`

`OllamaImageModel` implements `ImageModel` and supports text-to-image generation through Ollama's
standard `/api/generate` endpoint. Image editing and generating multiple images from the same prompt
are not supported.

This requires an Ollama version and model that support the experimental image generation capability.
Regular text models return text instead of an image and will fail with `OllamaImageGenerationException`.

## Usage

```java
ImageModel imageModel = OllamaImageModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("x/z-image-turbo")
        .width(1024)
        .height(768)
        .steps(20)
        .seed(42)
        .build();

Response<Image> response = imageModel.generate("a sunset over mountains");
Image image = response.content();
byte[] imageBytes = Base64.getDecoder().decode(image.base64Data());
Files.write(Path.of("ollama-image.png"), imageBytes);
```

## Parameters

| Parameter           | Description                                                                                 | Type                  |
|---------------------|---------------------------------------------------------------------------------------------|-----------------------|
| `httpClientBuilder` | See [Customizable HTTP Client](https://docs.langchain4j.dev/tutorials/customizable-http-client) | `HttpClientBuilder`   |
| `baseUrl`           | The base URL of the Ollama server.                                                          | `String`              |
| `modelName`         | The image generation model to use from the Ollama server.                                    | `String`              |
| `width`             | Width of the generated image in pixels. Use `0` or leave unset to use the Ollama/model default. Must be between 0 and 4096 when set. | `Integer`             |
| `height`            | Height of the generated image in pixels. Use `0` or leave unset to use the Ollama/model default. Must be between 0 and 4096 when set. | `Integer`             |
| `steps`             | Number of diffusion steps. Use `0` or leave unset to use the Ollama/model default. Must not be negative. | `Integer`             |
| `seed`              | Random seed sent through Ollama `options.seed`.                                              | `Integer`             |
| `timeout`           | The maximum time allowed for the API call to complete.                                       | `Duration`            |
| `customHeaders`     | Custom HTTP headers.                                                                        | `Map<String, String>` |
| `logRequests`       | Whether to log requests.                                                                    | `Boolean`             |
| `logResponses`      | Whether to log responses.                                                                   | `Boolean`             |
| `maxRetries`        | The maximum number of retries in case of API call failure.                                   | `Integer`             |

There is currently no Spring Boot starter configuration for `OllamaImageModel`.

The generated image is returned as Base64-encoded PNG data. URL output, token usage, and Ollama
streaming progress fields such as `completed` and `total` are not exposed.
