---
sidebar_position: 3
---

# Google AI Gemini Image Generation

Gemini can generate and edit images conversationally using specialized image models known as **Nano Banana** (Gemini 2.5 Flash Image) and **Nano Banana Pro** (Gemini 3 Pro Image Preview).

## Table of Contents

- [Overview](#overview)
- [Models Available](#models-available)
- [GoogleAiGeminiImageModel](#googleaigeminiimagemodel)
    - [Basic Usage](#basic-usage)
    - [Configuration](#configuration)
- [Image Generation](#image-generation)
    - [Text-to-Image](#text-to-image)
    - [Aspect Ratios](#aspect-ratios)
    - [Image Sizes](#image-sizes)
- [Image Editing](#image-editing)
    - [Adding and Removing Elements](#adding-and-removing-elements)
    - [Style Transfer](#style-transfer)
    - [Inpainting](#inpainting)
- [Batch Image Generation](#batch-image-generation)
- [Limitations](#limitations)
- [Resources](#resources)

## Overview

Gemini's native image generation capabilities allow you to:

- **Text-to-Image**: Generate high-quality images from text descriptions
- **Image Editing**: Add, remove, or modify elements in existing images
- **Style Transfer**: Apply artistic styles to images
- **Iterative Refinement**: Conversationally refine images over multiple turns
- **High-Fidelity Text Rendering**: Generate images with legible, well-placed text

All generated images include a [SynthID watermark](https://ai.google.dev/responsible/docs/safeguards/synthid).

## Models Available

| Model | Description | Max Resolution | Max Input Images |
|-------|-------------|----------------|------------------|
| `gemini-2.5-flash-image` | Fast, efficient image generation (Nano Banana) | 1024px | 3 |
| `gemini-3-pro-image-preview` | Advanced features, thinking mode, Google Search grounding (Nano Banana Pro) | 4K | 14 |

## GoogleAiGeminiImageModel

### Basic Usage

```java
ImageModel imageModel = GoogleAiGeminiImageModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash-image")
    .build();

Response<Image> response = imageModel.generate(
    "A nano banana dish in a fancy restaurant with a Gemini theme"
);

// Save the generated image
Image image = response.content();
byte[] imageBytes = Base64.getDecoder().decode(image.base64Data());
Files.write(Paths.get("nano-banana.png"), imageBytes);
```

### Configuration

```java
ImageModel imageModel = GoogleAiGeminiImageModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-3-pro-image-preview")
    .aspectRatio("16:9")              // Output aspect ratio
    .imageSize("2K")                   // Resolution (Gemini 3 Pro only)
    .timeout(Duration.ofSeconds(120))
    .maxRetries(3)
    .logRequestsAndResponses(true)
    .safetySettings(...)               // Content safety settings
    .build();
```

## Image Generation

### Text-to-Image

Generate images from descriptive text prompts:

```java
ImageModel imageModel = GoogleAiGeminiImageModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash-image")
    .build();

// Photorealistic style
Response<Image> photo = imageModel.generate("""
    A photorealistic close-up portrait of an elderly Japanese ceramicist
    with deep wrinkles and a warm smile, inspecting a tea bowl.
    Soft golden hour light, 85mm portrait lens, shallow depth of field.
    """);

// Stylized illustration
Response<Image> sticker = imageModel.generate("""
    A kawaii-style sticker of a happy red panda wearing a bamboo hat,
    munching on a leaf. Bold outlines, cel-shading, vibrant colors,
    white background.
    """);

// Logo design
Response<Image> logo = imageModel.generate("""
    A modern, minimalist logo for 'The Daily Grind' coffee shop.
    Clean, bold sans-serif font. Black and white. Circular design
    with a clever coffee bean element.
    """);
```

### Aspect Ratios

Supported aspect ratios for both models:

| Aspect Ratio | Use Case |
|--------------|----------|
| `1:1` | Square, social media posts |
| `2:3`, `3:2` | Portrait/landscape photos |
| `3:4`, `4:3` | Standard photos |
| `4:5`, `5:4` | Instagram posts |
| `9:16`, `16:9` | Stories, YouTube thumbnails |
| `21:9` | Cinematic, ultrawide |

```java
ImageModel imageModel = GoogleAiGeminiImageModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash-image")
    .aspectRatio("16:9")  // Widescreen format
    .build();
```

### Image Sizes

**Gemini 3 Pro Image Preview** supports higher resolutions:

| Size | Description |
|------|-------------|
| `1K` | Default resolution |
| `2K` | Higher resolution |
| `4K` | Maximum resolution |

```java
ImageModel imageModel = GoogleAiGeminiImageModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-3-pro-image-preview")
    .aspectRatio("1:1")
    .imageSize("4K")  // High resolution output
    .build();
```

## Image Editing

### Adding and Removing Elements

Edit existing images by providing them alongside text prompts:

```java
ImageModel imageModel = GoogleAiGeminiImageModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash-image")
    .build();

// Load the source image
Image sourceImage = Image.builder()
    .base64Data(Base64.getEncoder().encodeToString(
        Files.readAllBytes(Paths.get("cat.png"))))
    .mimeType("image/png")
    .build();

Response<Image> edited = imageModel.edit(
    sourceImage,
    "Add a small wizard hat on the cat's head. " +
    "Make it look natural with matching lighting."
);
```

### Style Transfer

Transform images into different artistic styles:

```java
Image cityPhoto = // ... load your image

Response<Image> stylized = imageModel.edit(
    cityPhoto,
    "Transform this city street into Vincent van Gogh's 'Starry Night' style. " +
    "Preserve the composition but render with swirling brushstrokes " +
    "and a dramatic palette of deep blues and bright yellows."
);
```

### Inpainting

Modify specific elements while preserving the rest:

```java
Image livingRoom = // ... load your image

Response<Image> edited = imageModel.edit(
    livingRoom,
    "Change only the blue sofa to a vintage brown leather chesterfield. " +
    "Keep everything else exactly the same."
);
```

## Batch Image Generation

For generating multiple images at scale with 50% cost reduction:

```java
GoogleAiGeminiBatchImageModel batchModel = GoogleAiGeminiBatchImageModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash-image")
    .build();

List<String> prompts = List.of(
    "A nano banana dish in a Gemini-themed restaurant",
    "A kawaii sticker of a banana wearing a chef hat",
    "A photorealistic banana split dessert",
    "A minimalist logo for 'Nano Banana Co.'"
);

// Submit batch
BatchResponse<?> response = batchModel.createBatchInline(prompts, "image-batch");
BatchName batchName = getBatchName(response);

// Poll for completion
do {
    Thread.sleep(10000);
    response = batchModel.retrieveBatchResults(batchName);
} while (response instanceof BatchIncomplete);

// Process results
if (response instanceof BatchSuccess<?> success) {
    for (Image image : success.images()) {
        byte[] imageBytes = Base64.getDecoder().decode(image.base64Data());
        // Save or process each image
    }
}

// Clean up
batchModel.deleteBatchJob(batchName);
```

## Limitations

- **Languages**: Best performance with EN, and supported languages including ar-EG, de-DE, es-MX, fr-FR, hi-IN, id-ID, it-IT, ja-JP, ko-KR, pt-BR, ru-RU, vi-VN, zh-CN
- **Input**: Audio and video inputs are not supported for image generation
- **Output Count**: The model may not always generate the exact number of images requested
- **Input Images**:
    - `gemini-2.5-flash-image`: Up to 3 input images
    - `gemini-3-pro-image-preview`: Up to 14 input images (including 5 human images for consistency)
- **URL Images**: URL-based images are not supported for editing; use base64-encoded images
- **Watermark**: All generated images include a SynthID watermark

## Resources

- [Gemini Image Generation Documentation](https://ai.google.dev/gemini-api/docs/image-generation)
- [Gemini API Models](https://ai.google.dev/gemini-api/docs/models/gemini)
- [Batch API Documentation](https://ai.google.dev/gemini-api/docs/batch-api)
- [Image Generation Cookbook](https://colab.research.google.com/github/google-gemini/cookbook/blob/main/quickstarts/Get_Started_Nano_Banana.ipynb)

