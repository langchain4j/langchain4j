---
sidebar_position: 2
---

# Azure OpenAI Audio Models

:::note

This is the documentation for the `Azure OpenAI` integration, that uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.

For the chat models of this integration, see [Azure OpenAI](/integrations/language-models/azure-open-ai).

:::

## Creating `AzureOpenAiAudioTranscriptionModel`

Azure OpenAI now supports audio transcription, enabling you to convert spoken language from audio files into text using state-of-the-art models hosted on Azure.

### Maven Dependency

The audio transcription feature is included in the main `langchain4j-azure-open-ai` package:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai</artifactId>
    <version>1.22.0</version>
</dependency>
```

### Plain Java Usage

You can transcribe audio files with the `AzureOpenAiAudioTranscriptionModel`:

```java
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import java.io.File;
import java.nio.file.Files;

AzureOpenAiAudioTranscriptionModel model = AzureOpenAiAudioTranscriptionModel.builder()
    .endpoint(System.getenv("AZURE_OPENAI_URL"))
    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
    .deploymentName("your-audio-model-deployment-name") // e.g., "whisper"
    .build();

// Read audio file as binary data
File audioFile = new File("path/to/audio-file.wav");
byte[] audioData = Files.readAllBytes(audioFile.toPath());

// Create Audio object with binary data
Audio audio = Audio.builder()
    .binaryData(audioData)
    .build();

// Create transcription request
AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
    .audio(audio)
    .prompt("This is an audio file containing ...") // optional
    .language("en") // optional
    .temperature(0.0) // optional
    .build();

// Transcribe audio
AudioTranscriptionResponse response = model.transcribe(request);
String transcript = response.text();
System.out.println(transcript);
```

### Notes

- **Deployment**: You must deploy an audio transcription model (such as Whisper) in your Azure OpenAI resource. See the [Azure OpenAI documentation](https://learn.microsoft.com/en-us/azure/ai-services/openai/) for details.
- **Supported Formats**: Common audio formats such as WAV, MP3, and FLAC are supported.
- **Quotas and Pricing**: Audio transcription consumes resources from your Azure subscription. Review the applicable quotas and pricing in your Azure portal.


## Examples

- [Azure OpenAI Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/azure-open-ai-examples/src/main/java)
