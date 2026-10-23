---
sidebar_position: 1
---

# OpenAI Audio Models

:::note

This is the documentation for the `OpenAI` integration, that uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).

For the chat models of this integration, see [OpenAI](/integrations/language-models/open-ai).

:::

## OpenAI Documentation

- [OpenAI Text to Speech Guide](https://platform.openai.com/docs/guides/text-to-speech)
- [OpenAI Speech to Text Guide](https://platform.openai.com/docs/guides/speech-to-text)
- [OpenAI Audio API Reference](https://platform.openai.com/docs/api-reference/audio)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.22.0</version>
</dependency>
```

## API Key

To use OpenAI models, you will need an API key.
You can create one [here](https://platform.openai.com/api-keys).

## Creating `OpenAiAudioTranscriptionModel`

`OpenAiAudioTranscriptionModel` performs speech-to-text using the
[OpenAI Transcription API](https://platform.openai.com/docs/api-reference/audio/createTranscription).

The supported models are `whisper-1`, `gpt-4o-transcribe`, `gpt-4o-mini-transcribe` and `gpt-4o-transcribe-diarize`
(see `OpenAiAudioTranscriptionModelName`).

```java
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModel;
import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModelName;

AudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(OpenAiAudioTranscriptionModelName.GPT_4_O_MINI_TRANSCRIBE)
        .build();

Audio audio = Audio.builder()
        .binaryData(Files.readAllBytes(Path.of("speech.mp3")))
        .mimeType("audio/mpeg")
        .build();

AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
        .audio(audio)
        .prompt("A conversation about LangChain4j") // optional
        .language("en")                             // optional, ISO-639-1 code
        .temperature(0.0)                           // optional
        .build();

AudioTranscriptionResponse response = model.transcribe(request);
String transcript = response.text();
```

`OpenAiAudioTranscriptionModel` supports the following connection options: `baseUrl`, `organizationId`, `projectId`,
`timeout`, `maxRetries` (default 2), `logRequests`, `logResponses`, `logger` and `httpClientProvider`.

## Creating `OpenAiTextToSpeechModel`

`OpenAiTextToSpeechModel` performs text-to-speech (TTS) using the
[OpenAI Speech API](https://platform.openai.com/docs/api-reference/audio/createSpeech).
It returns the generated audio as raw bytes wrapped in an `Audio` object.

The supported models are `tts-1`, `tts-1-hd`, `gpt-4o-mini-tts`, and `gpt-4o-mini-tts-2025-12-15`
(see `OpenAiTextToSpeechModelName`). The default voice is `alloy`.

### Plain Java
```java
import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import dev.langchain4j.model.audio.TextToSpeechResponse;
import dev.langchain4j.model.openai.OpenAiTextToSpeechModel;
import dev.langchain4j.model.openai.OpenAiTextToSpeechModelName;
import dev.langchain4j.model.openai.OpenAiTextToSpeechVoiceName;

TextToSpeechModel model = OpenAiTextToSpeechModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(OpenAiTextToSpeechModelName.TTS_1)
        .voice(OpenAiTextToSpeechVoiceName.ALLOY) // optional, defaults to ALLOY
        .build();

// Convenience method (uses the model's default voice):
TextToSpeechResponse response = model.synthesize("Hello world!");

// Or with an explicit request (the voice here overrides the model default):
TextToSpeechRequest request = TextToSpeechRequest.builder()
        .text("Hello world!")
        .voice("nova")
        .build();
TextToSpeechResponse response2 = model.synthesize(request);

byte[] audioBytes = response.audio().binaryData(); // e.g. write to an .mp3 file
String mimeType = response.audio().mimeType();      // e.g. "audio/mpeg"
```

The input text must not exceed 4096 characters (the OpenAI Speech API limit);
longer input causes an `IllegalArgumentException`.

### Optional parameters
```java
TextToSpeechModel model = OpenAiTextToSpeechModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(OpenAiTextToSpeechModelName.GPT_4_O_MINI_TTS)
        .instructions("Speak in a cheerful tone.")              // at most 4096 characters, not supported by tts-1 and tts-1-hd
        .responseFormat(OpenAiTextToSpeechResponseFormat.WAV)  // mp3 (default), opus, aac, flac, wav or pcm
        .speed(1.25)                                           // from 0.25 to 4.0, defaults to 1.0
        .build();
```

Values outside these limits cause an `IllegalArgumentException` when the model is built.

### Voices

The default voice can be set on the builder in three ways:

- `voice(OpenAiTextToSpeechVoiceName)`: one of the built-in voices
  (`alloy`, `ash`, `ballad`, `coral`, `echo`, `fable`, `onyx`, `nova`, `sage`, `shimmer`, `verse`, `marin`, `cedar`).
- `voice(String)`: a built-in voice by name (e.g. `"alloy"`) or a custom voice by ID (e.g. `"voice_1234"`).
  A value that matches a built-in voice name is used as a built-in voice; any other value is used as a custom voice ID.
- `voice(OpenAiCustomVoice)`: a custom voice, referenced by its ID (see [Creating `OpenAiVoiceModel`](#creating-openaivoicemodel)).

A blank voice passed to the builder's `voice(String)` causes an `IllegalArgumentException`.

A custom voice can be set as the model's default voice:

```java
import dev.langchain4j.model.openai.OpenAiCustomVoice;

TextToSpeechModel model = OpenAiTextToSpeechModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(OpenAiTextToSpeechModelName.GPT_4_O_MINI_TTS)
        .voice(OpenAiCustomVoice.from("voice_1234"))
        .build();
```

A voice set on a `TextToSpeechRequest` overrides the default voice. If it is a built-in voice name
(e.g. `"coral"`), it is sent as a built-in voice; any other value is sent as a custom voice ID:

```java
TextToSpeechResponse response = model.synthesize(TextToSpeechRequest.builder()
        .text("Hello from my custom voice!")
        .voice("voice_1234")
        .build());
```

## Creating `OpenAiVoiceModel`

:::note
This API is experimental and may change in future releases.
:::

`OpenAiVoiceModel` creates a custom voice from a text description using the
[OpenAI Voices API](https://developers.openai.com/api/reference/resources/audio/subresources/voices).
The ID of the created voice can then be used with `OpenAiTextToSpeechModel` via `OpenAiCustomVoice`.

```java
import dev.langchain4j.model.openai.OpenAiCustomVoice;
import dev.langchain4j.model.openai.OpenAiVoiceModel;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceResponse;

OpenAiVoiceModel voiceModel = OpenAiVoiceModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .build();

OpenAiCreateVoiceResponse voice = voiceModel.createVoice(OpenAiCreateVoiceRequest.builder()
        .name("narrator")                                     // required, 1-256 characters
        .prompt("A warm, calm, middle-aged narrator voice")   // required, 1-2000 characters
        .scriptHint("Welcome to today's story.")              // optional, 1-2000 characters
        .model("auto")                                        // optional, defaults to "auto"
        .build());

OpenAiCustomVoice customVoice = OpenAiCustomVoice.from(voice.id());
```

Requests that violate the length constraints above are rejected with an `IllegalArgumentException`
before being sent.

## Creating `OpenAiVoiceConsentModel`

:::note
This API is experimental and may change in future releases.
:::

`OpenAiVoiceConsentModel` manages voice consent recordings using the
[OpenAI Voice Consents API](https://developers.openai.com/api/reference/resources/audio/subresources/voice_consents):
a recording of the voice actor reading the consent phrase for a given language.
It supports creating, listing, retrieving, updating (renaming) and deleting consents.

```java
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.openai.OpenAiVoiceConsentModel;
import dev.langchain4j.model.openai.internal.audio.transcription.AudioFile;
import dev.langchain4j.model.openai.internal.audio.voice.*;

OpenAiVoiceConsentModel consentModel = OpenAiVoiceConsentModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .build();

// Create (the recording must not exceed 10 MB)
OpenAiVoiceConsentResponse consent = consentModel.createVoiceConsent(OpenAiCreateVoiceConsentRequest.builder()
        .name("Jane Doe consent")
        .language("en-US") // BCP 47 language tag of the consent phrase
        .recording(AudioFile.from(Audio.builder()
                .binaryData(Files.readAllBytes(Path.of("consent.mp3")))
                .mimeType("audio/mpeg")
                .build()))
        .build());

// List (paginated; limit must be between 1 and 100)
OpenAiVoiceConsentListResponse page = consentModel.listVoiceConsents(OpenAiListVoiceConsentsRequest.builder()
        .limit(20)
        .after(null) // or the lastId() of the previous page
        .build());
page.data().forEach(c -> System.out.println(c.id() + " " + c.name()));

// Retrieve
OpenAiVoiceConsentId id = OpenAiVoiceConsentId.builder().consentId(consent.id()).build();
OpenAiVoiceConsentResponse retrieved = consentModel.retrieveVoiceConsent(id);

// Update (rename)
consentModel.updateVoiceConsent(OpenAiUpdateVoiceConsentRequest.builder()
        .consentId(consent.id())
        .name("Jane Doe consent (2026)")
        .build());

// Delete
OpenAiDeleteVoiceConsentResponse deleted = consentModel.deleteVoiceConsent(id);
```

Supported recording MIME types are `audio/mpeg`, `audio/wav`, `audio/x-wav`, `audio/ogg`, `audio/aac`,
`audio/flac`, `audio/webm` and `audio/mp4`.

Both `OpenAiVoiceModel` and `OpenAiVoiceConsentModel` support the same connection options as the other
OpenAI models: `baseUrl`, `organizationId`, `projectId`, `timeout`, `maxRetries` (default 2),
`logRequests`, `logResponses`, `logger` and `httpClientBuilder`.


## Examples
- [OpenAI Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/open-ai-examples/src/main/java)
