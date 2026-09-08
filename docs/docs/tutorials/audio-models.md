---
sidebar_position: 12
---

# Audio Models

LangChain4j provides model abstractions for three kinds of audio operation:

- `AudioTranscriptionModel` converts audio to text.
- `TextToSpeechModel` converts text to audio.
- `SpeechToSpeechModel` sends audio to a model and receives generated audio directly, without requiring an explicit transcription and synthesis pipeline.

All three APIs use `Audio` to represent input or output audio. `Audio` can contain a URL, binary data, or Base64-encoded data. The formats accepted by a model depend on the provider implementation.

## Speech to speech

Use the convenience method when only input audio is required:

```java
Audio input = Audio.builder()
        .binaryData(Files.readAllBytes(Path.of("question.wav")))
        .mimeType("audio/wav")
        .build();

SpeechToSpeechResponse response = model.generate(input);
Audio answer = response.audio();
```

Use `SpeechToSpeechRequest` when the provider supports additional instructions or voice selection:

```java
SpeechToSpeechRequest request = SpeechToSpeechRequest.builder(input)
        .instructions("Answer briefly and in English")
        .voice("alloy")
        .build();

SpeechToSpeechResponse response = model.generate(request);
Audio answer = response.audio();
String transcript = response.transcript(); // Optional
```

Support for instructions, voices, transcripts, and audio formats is provider-specific. Consult the provider integration documentation for its supported capabilities.

`SpeechToSpeechModel` represents a single request and response. Realtime, bidirectional audio streaming requires a session-oriented API and is not modeled by this interface.
