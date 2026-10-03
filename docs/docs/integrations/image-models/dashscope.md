---
sidebar_position: 10
---

# DashScope (Qwen / Wanx) Image Generation

[DashScope](https://dashscope.aliyuncs.com/) is a platform developed by [Alibaba Cloud](https://www.alibabacloud.com/).
LangChain4j integrates with DashScope using the
[DashScope Java SDK](https://help.aliyun.com/zh/dashscope/java-sdk-best-practices)
and supports two ways of generating images:

1. **`WanxImageModel`** — implements `ImageModel` and wraps the DashScope
   [Image Synthesis API](https://www.alibabacloud.com/help/en/model-studio/text-to-image) for the Wanx
   text-to-image models (`wanx-v1`, `wanx2.*-t2i-*`, `wan2.6-image`). Supports text-to-image and
   image editing (image-to-image) via a reference image.
2. **`QwenChatModel`** with the `qwen-image-2.0` / `qwen-image-2.0-pro` models — image generation and
   editing that **re-uses the text-to-text chat protocol**. Set `isMultimodalModel(true)` on the
   builder and read the generated images from `AiMessage.images()`.

## Maven Dependency

:::note
Since `1.0.0-alpha1`, `langchain4j-dashscope` has migrated to `langchain4j-community` and is renamed to
`langchain4j-community-dashscope`.
:::

Before `1.0.0-alpha1`:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-dashscope</artifactId>
    <version>${previous version here}</version>
</dependency>
```

`1.0.0-alpha1` and later:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Or, you can use BOM to manage dependencies consistently:

```xml

<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-bom</artifactId>
        <version>${latest version here}</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```

### Spring Boot

To use it in a Spring Boot application, add the appropriate starter
(`langchain4j-community-dashscope-spring-boot-starter` for Spring Boot 3, or
`langchain4j-community-dashscope-spring-boot4-starter` for Spring Boot 4). Note that the auto-configured
bean is currently `QwenChatModel` (for the chat-based image models), not `WanxImageModel`.

## WanxImageModel

`WanxImageModel` implements `ImageModel` and supports text-to-image generation and image editing through
the DashScope Image Synthesis API. The generated image is returned as a URL.

Available Wanx model IDs (defined in `WanxModelName`):

- `wanx-v1` – Wanx text-to-image v1 (Chinese and English)
- `wanx2.0-t2i-turbo` – Wanx text-to-image v2.0 turbo
- `wanx2.1-t2i-turbo` – Wanx text-to-image v2.1 turbo
- `wanx2.1-t2i-plus` – Wanx text-to-image v2.1 plus
- `wan2.6-image` – Wan 2.6 image

### Parameters

| Parameter        | Description                                                                                                  | Default       |
|------------------|--------------------------------------------------------------------------------------------------------------|---------------|
| `baseUrl`        | The base URL to connect to DashScope                                                                         | DashScope default |
| `apiKey`         | The API Key                                                                                                  |               |
| `modelName`      | The image model to use                                                                                       | `wanx-v1`     |
| `size`           | Resolution: `1024*1024`, `720*1280`, or `1280*720` (`WanxImageSize`)                                         | `1024*1024`   |
| `style`          | Style (`WanxImageStyle`, e.g. `PHOTOGRAPHY`, `ANIME`, `AUTO`)                                                |               |
| `seed`           | Random seed                                                                                                  |               |
| `refMode`        | Reference image mode for editing: `repaint` (reference content) or `refonly` (reference style) (`WanxImageRefMode`) | `repaint` |
| `refStrength`    | Similarity to the reference image, range `[0.0, 1.0]`                                                        | `0.5`         |
| `negativePrompt` | Content you do not want to see in the image                                                                  |               |
| `promptExtend`   | Enable intelligent prompt rewriting (adds 3–4s)                                                              |               |
| `watermark`      | Add an "AI生成" watermark in the lower-right corner                                                          |               |

### Text-to-image

```java
import dev.langchain4j.community.model.dashscope.WanxImageModel;
import dev.langchain4j.community.model.dashscope.WanxModelName;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;

ImageModel imageModel = WanxImageModel.builder()
    .apiKey("your-api-key")
    .modelName(WanxModelName.WANX2_1_T2I_PLUS)
    .size(WanxImageSize.SIZE_1280_720)
    .style(WanxImageStyle.PHOTOGRAPHY)
    .seed(42)
    .promptExtend(true)
    .build();

Response<Image> response = imageModel.generate("a sunset over mountains");
String url = response.content().url().toString();
```

`generate(String prompt, int n)` returns multiple images in a single call.

### Image editing (image-to-image)

Pass a reference image to `edit(...)`; `refMode`/`refStrength` control how strongly the result follows it.

```java
Image reference = Image.builder().url("https://example.com/subject.png").build();
Response<Image> response = imageModel.edit(reference, "change the background to a beach");
String url = response.content().url().toString();
```

## QwenChatModel (qwen-image-2.0 / qwen-image-2.0-pro)

The `qwen-image-2.0` and `qwen-image-2.0-pro` models perform image generation **and** editing but are
invoked through the standard chat (text-to-text) protocol. Build a `QwenChatModel` with
`isMultimodalModel(true)` and read the generated images from `AiMessage.images()`.

> 🔗 [Qwen Image API reference](https://www.alibabacloud.com/help/en/model-studio/text-to-image-generation)

### Text-to-image

```java
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenModelName;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

ChatModel model = QwenChatModel.builder()
    .apiKey("your-api-key")
    .modelName(QwenModelName.QWEN_IMAGE_2_0)
    .isMultimodalModel(true)
    .build();

ChatResponse response = model.chat(UserMessage.from("Draw a parrot."));
// generated images are attached to the AiMessage
response.aiMessage().images();
```

### Image-to-image

Provide the source image and an instruction as a multimodal `UserMessage`:

```java
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

Image source = Image.builder().url("https://example.com/dog_and_girl.jpeg").build();
ChatResponse response = model.chat(UserMessage.from(
    ImageContent.from(source),
    TextContent.from("Change the background.")
));
response.aiMessage().images();
```

## Examples

- [WanxImageModelIT](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-dashscope/src/test/java/dev/langchain4j/community/model/dashscope/WanxImageModelIT.java)
- [QwenChatModelIT](https://github.com/langchain4j/langchain4j-community/blob/main/models/langchain4j-community-dashscope/src/test/java/dev/langchain4j/community/model/dashscope/QwenChatModelIT.java) (image generation and editing via `QwenChatModel`)
