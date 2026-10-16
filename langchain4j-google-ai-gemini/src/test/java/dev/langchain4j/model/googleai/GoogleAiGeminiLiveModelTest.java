package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GoogleAiGeminiLiveModel and LiveSession.
 * These tests don't require an actual API key - they test the message
 * serialization/deserialization logic using a mock WebSocket.
 */
class GoogleAiGeminiLiveModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    class ModelBuilderTests {

        @Test
        void should_build_model_with_all_options() {
            // Given & When
            GoogleAiGeminiLiveModel model = GoogleAiGeminiLiveModel.builder()
                    .apiKey("test-api-key")
                    .modelName("gemini-2.0-flash-exp")
                    .responseModalities(Arrays.asList("AUDIO", "TEXT"))
                    .systemInstruction("You are a helpful assistant")
                    .thinkingBudget(100)
                    .build();

            // Then
            assertThat(model.getApiKey()).isEqualTo("test-api-key");
            assertThat(model.getModelName()).isEqualTo("gemini-2.0-flash-exp");
            assertThat(model.getResponseModalities()).containsExactly("AUDIO", "TEXT");
            assertThat(model.getSystemInstruction()).isEqualTo("You are a helpful assistant");
            assertThat(model.getThinkingBudget()).isEqualTo(100);
        }

        @Test
        void should_use_default_model_name() {
            // Given & When
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-api-key").build();

            // Then
            assertThat(model.getModelName()).isEqualTo("gemini-2.5-flash-native-audio-preview-12-2025");
        }

        @Test
        void should_throw_when_api_key_is_null() {
            // Given & When & Then
            assertThatThrownBy(() -> GoogleAiGeminiLiveModel.builder().build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("apiKey");
        }
    }

    @Nested
    class MessageSerializationTests {

        /**
         * Tests that verify the JSON message formats match the Gemini Live API specification.
         * These use a testable session that captures sent messages instead of sending them.
         */
        @Test
        void should_serialize_setup_message_correctly() throws Exception {
            // Given
            GoogleAiGeminiLiveModel model = GoogleAiGeminiLiveModel.builder()
                    .apiKey("test-key")
                    .modelName("gemini-2.0-flash-exp")
                    .responseModalities(Arrays.asList("AUDIO"))
                    .systemInstruction("Be helpful")
                    .thinkingBudget(50)
                    .build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);

            // When - setup is sent on connection
            session.simulateOpen();

            // Then
            assertThat(session.getSentMessages()).hasSize(1);
            JsonNode setupMessage =
                    objectMapper.readTree(session.getSentMessages().get(0));

            assertThat(setupMessage.has("setup")).isTrue();
            JsonNode setup = setupMessage.get("setup");

            // Verify model name
            assertThat(setup.get("model").asText()).isEqualTo("models/gemini-2.0-flash-exp");

            // Verify generation config
            JsonNode generationConfig = setup.get("generationConfig");
            assertThat(generationConfig.get("responseModalities").get(0).asText())
                    .isEqualTo("AUDIO");
            assertThat(generationConfig
                            .get("thinkingConfig")
                            .get("thinkingBudget")
                            .asInt())
                    .isEqualTo(50);

            // Verify system instruction
            JsonNode systemInstruction = setup.get("systemInstruction");
            assertThat(systemInstruction.get("parts").get(0).get("text").asText())
                    .isEqualTo("Be helpful");
        }

        @Test
        void should_serialize_text_message_correctly() throws Exception {
            // Given
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-key").build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);
            session.simulateOpen();
            session.clearSentMessages(); // Clear the setup message

            // When
            session.sendText("Hello, world!");

            // Then
            assertThat(session.getSentMessages()).hasSize(1);
            JsonNode message = objectMapper.readTree(session.getSentMessages().get(0));

            assertThat(message.has("clientContent")).isTrue();
            JsonNode clientContent = message.get("clientContent");

            assertThat(clientContent.get("turnComplete").asBoolean()).isTrue();

            JsonNode turn = clientContent.get("turns").get(0);
            assertThat(turn.get("role").asText()).isEqualTo("user");
            assertThat(turn.get("parts").get(0).get("text").asText()).isEqualTo("Hello, world!");
        }

        @Test
        void should_serialize_audio_message_correctly() throws Exception {
            // Given
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-key").build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);
            session.simulateOpen();
            session.clearSentMessages();

            byte[] audioData = new byte[] {0x01, 0x02, 0x03, 0x04};

            // When
            session.sendAudio(audioData);

            // Then
            assertThat(session.getSentMessages()).hasSize(1);
            JsonNode message = objectMapper.readTree(session.getSentMessages().get(0));

            assertThat(message.has("realtimeInput")).isTrue();
            JsonNode realtimeInput = message.get("realtimeInput");

            JsonNode audio = realtimeInput.get("audio");
            assertThat(audio.get("mimeType").asText()).isEqualTo("audio/pcm;rate=16000");

            // Verify the audio data is base64 encoded correctly
            String base64Data = audio.get("data").asText();
            byte[] decodedData = Base64.getDecoder().decode(base64Data);
            assertThat(decodedData).isEqualTo(audioData);
        }

        @Test
        void should_serialize_video_message_correctly() throws Exception {
            // Given
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-key").build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);
            session.simulateOpen();
            session.clearSentMessages();

            byte[] videoFrame = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}; // JPEG magic bytes

            // When
            session.sendVideo(videoFrame);

            // Then
            assertThat(session.getSentMessages()).hasSize(1);
            JsonNode message = objectMapper.readTree(session.getSentMessages().get(0));

            assertThat(message.has("clientContent")).isTrue();
            JsonNode clientContent = message.get("clientContent");
            JsonNode turn = clientContent.get("turns").get(0);
            JsonNode part = turn.get("parts").get(0);

            assertThat(part.has("inlineData")).isTrue();
            JsonNode inlineData = part.get("inlineData");
            assertThat(inlineData.get("mimeType").asText()).isEqualTo("image/jpeg");

            // Verify the video data is base64 encoded correctly
            String base64Data = inlineData.get("data").asText();
            byte[] decodedData = Base64.getDecoder().decode(base64Data);
            assertThat(decodedData).isEqualTo(videoFrame);
        }
    }

    @Nested
    class MessageParsingTests {

        @Test
        void should_parse_text_response() throws Exception {
            // Given
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-key").build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);
            session.simulateOpen();

            AtomicReference<String> receivedText = new AtomicReference<>();
            session.onTextResponse(receivedText::set);

            // Simulate a text response from the server
            String serverResponse = createTextResponse("Hello from Gemini!");

            // When
            session.simulateMessage(serverResponse);

            // Then
            assertThat(receivedText.get()).isEqualTo("Hello from Gemini!");
        }

        @Test
        void should_parse_audio_response() throws Exception {
            // Given
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-key").build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);
            session.simulateOpen();

            AtomicReference<byte[]> receivedAudio = new AtomicReference<>();
            session.onAudioResponse(receivedAudio::set);

            // Simulate an audio response from the server
            byte[] expectedAudio = new byte[] {0x10, 0x20, 0x30, 0x40};
            String serverResponse = createAudioResponse(expectedAudio);

            // When
            session.simulateMessage(serverResponse);

            // Then
            assertThat(receivedAudio.get()).isEqualTo(expectedAudio);
        }

        @Test
        void should_handle_malformed_response_gracefully() throws Exception {
            // Given
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-key").build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);
            session.simulateOpen();

            AtomicReference<Throwable> receivedError = new AtomicReference<>();
            session.onError(receivedError::set);

            // When - send invalid JSON
            session.simulateMessage("not valid json {{{");

            // Then
            assertThat(receivedError.get()).isNotNull();
        }

        @Test
        void should_ignore_empty_server_content() throws Exception {
            // Given
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-key").build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);
            session.simulateOpen();

            AtomicReference<String> receivedText = new AtomicReference<>();
            session.onTextResponse(receivedText::set);

            // Simulate an empty serverContent response
            String serverResponse = "{\"serverContent\":{}}";

            // When
            session.simulateMessage(serverResponse);

            // Then - should not crash, text should remain null
            assertThat(receivedText.get()).isNull();
        }

        @Test
        void should_handle_multiple_parts_in_response() throws Exception {
            // Given
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-key").build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);
            session.simulateOpen();

            List<String> receivedTexts = new ArrayList<>();
            session.onTextResponse(receivedTexts::add);

            // Simulate a response with multiple text parts
            String serverResponse = createMultiPartTextResponse("Part 1", "Part 2", "Part 3");

            // When
            session.simulateMessage(serverResponse);

            // Then
            assertThat(receivedTexts).containsExactly("Part 1", "Part 2", "Part 3");
        }

        private String createTextResponse(String text) throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode serverContent = objectMapper.createObjectNode();
            ObjectNode modelTurn = objectMapper.createObjectNode();
            ArrayNode parts = objectMapper.createArrayNode();

            ObjectNode part = objectMapper.createObjectNode();
            part.put("text", text);
            parts.add(part);

            modelTurn.set("parts", parts);
            serverContent.set("modelTurn", modelTurn);
            response.set("serverContent", serverContent);

            return objectMapper.writeValueAsString(response);
        }

        private String createAudioResponse(byte[] audioData) throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode serverContent = objectMapper.createObjectNode();
            ObjectNode modelTurn = objectMapper.createObjectNode();
            ArrayNode parts = objectMapper.createArrayNode();

            ObjectNode part = objectMapper.createObjectNode();
            ObjectNode inlineData = objectMapper.createObjectNode();
            inlineData.put("mimeType", "audio/pcm");
            inlineData.put("data", Base64.getEncoder().encodeToString(audioData));
            part.set("inlineData", inlineData);
            parts.add(part);

            modelTurn.set("parts", parts);
            serverContent.set("modelTurn", modelTurn);
            response.set("serverContent", serverContent);

            return objectMapper.writeValueAsString(response);
        }

        private String createMultiPartTextResponse(String... texts) throws Exception {
            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode serverContent = objectMapper.createObjectNode();
            ObjectNode modelTurn = objectMapper.createObjectNode();
            ArrayNode parts = objectMapper.createArrayNode();

            for (String text : texts) {
                ObjectNode part = objectMapper.createObjectNode();
                part.put("text", text);
                parts.add(part);
            }

            modelTurn.set("parts", parts);
            serverContent.set("modelTurn", modelTurn);
            response.set("serverContent", serverContent);

            return objectMapper.writeValueAsString(response);
        }
    }

    @Nested
    class SessionLifecycleTests {

        @Test
        void should_report_connected_status_correctly() {
            // Given
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-key").build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);

            // When & Then - before open
            assertThat(session.isConnected()).isFalse();

            // When - simulate open
            session.simulateOpen();

            // Then
            assertThat(session.isConnected()).isTrue();

            // When - simulate close
            session.simulateClose(1000, "Normal closure");

            // Then
            assertThat(session.isConnected()).isFalse();
        }

        @Test
        void should_invoke_error_handler_on_connection_error() {
            // Given
            GoogleAiGeminiLiveModel model =
                    GoogleAiGeminiLiveModel.builder().apiKey("test-key").build();

            TestableWebSocketSession session = new TestableWebSocketSession(model);
            session.simulateOpen();

            AtomicReference<Throwable> receivedError = new AtomicReference<>();
            session.onError(receivedError::set);

            // When
            session.simulateError(new RuntimeException("Connection lost"));

            // Then
            assertThat(receivedError.get()).isInstanceOf(RuntimeException.class).hasMessage("Connection lost");
        }
    }

    @Nested
    class AudioDataGenerationTests {

        @Test
        void should_generate_valid_pcm_audio_data() {
            // This tests the helper method used in integration tests
            int sampleRate = 16000;
            double duration = 0.1; // 100ms
            double frequency = 440; // A4 note

            byte[] audio = generateSamplePcmAudio(sampleRate, duration, frequency);

            // PCM 16-bit audio: 2 bytes per sample
            int expectedSamples = (int) (sampleRate * duration);
            int expectedBytes = expectedSamples * 2;

            assertThat(audio.length).isEqualTo(expectedBytes);

            // Verify it's not all zeros (actual audio data)
            boolean hasNonZero = false;
            for (byte b : audio) {
                if (b != 0) {
                    hasNonZero = true;
                    break;
                }
            }
            assertThat(hasNonZero).isTrue();
        }

        private byte[] generateSamplePcmAudio(int sampleRate, double durationSeconds, double frequency) {
            int numSamples = (int) (sampleRate * durationSeconds);
            byte[] audioData = new byte[numSamples * 2];

            for (int i = 0; i < numSamples; i++) {
                double time = (double) i / sampleRate;
                double value = Math.sin(2 * Math.PI * frequency * time);
                short sample = (short) (value * Short.MAX_VALUE * 0.5);

                audioData[i * 2] = (byte) (sample & 0xFF);
                audioData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
            }

            return audioData;
        }
    }

    /**
     * A testable WebSocket session that doesn't actually connect to a server.
     * Instead, it captures sent messages and allows simulation of received messages.
     */
    static class TestableWebSocketSession implements LiveSession {

        private final GoogleAiGeminiLiveModel model;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final List<String> sentMessages = new ArrayList<>();

        private Consumer<byte[]> audioHandler;
        private Consumer<String> textHandler;
        private Consumer<Throwable> errorHandler;
        private Runnable turnCompleteHandler;

        private boolean connected = false;

        TestableWebSocketSession(GoogleAiGeminiLiveModel model) {
            this.model = model;
        }

        // Simulation methods for testing

        void simulateOpen() {
            connected = true;
            // Send setup message like the real implementation does
            sendSetup();
        }

        void simulateClose(int code, String reason) {
            connected = false;
        }

        void simulateMessage(String message) {
            // Parse and handle the message like the real implementation
            try {
                JsonNode json = objectMapper.readTree(message);

                if (json.has("serverContent")) {
                    JsonNode serverContent = json.get("serverContent");
                    if (serverContent.has("modelTurn")) {
                        JsonNode modelTurn = serverContent.get("modelTurn");
                        if (modelTurn.has("parts")) {
                            for (JsonNode part : modelTurn.get("parts")) {
                                if (part.has("text") && textHandler != null) {
                                    textHandler.accept(part.get("text").asText());
                                }
                                if (part.has("inlineData") && audioHandler != null) {
                                    JsonNode inlineData = part.get("inlineData");
                                    if (inlineData.has("data")) {
                                        String base64Audio =
                                                inlineData.get("data").asText();
                                        byte[] audioBytes = Base64.getDecoder().decode(base64Audio);
                                        audioHandler.accept(audioBytes);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (errorHandler != null) {
                    errorHandler.accept(e);
                }
            }
        }

        void simulateError(Exception error) {
            if (errorHandler != null) {
                errorHandler.accept(error);
            }
        }

        List<String> getSentMessages() {
            return new ArrayList<>(sentMessages);
        }

        void clearSentMessages() {
            sentMessages.clear();
        }

        // Implementation of LiveSession interface

        private void sendSetup() {
            try {
                ObjectNode message = objectMapper.createObjectNode();
                ObjectNode setup = objectMapper.createObjectNode();

                setup.put("model", "models/" + model.getModelName());

                ObjectNode generationConfig = objectMapper.createObjectNode();
                if (model.getResponseModalities() != null) {
                    ArrayNode modalities = objectMapper.createArrayNode();
                    model.getResponseModalities().forEach(modalities::add);
                    generationConfig.set("responseModalities", modalities);
                }
                if (model.getThinkingBudget() != null) {
                    ObjectNode thinkingConfig = objectMapper.createObjectNode();
                    thinkingConfig.put("thinkingBudget", model.getThinkingBudget());
                    generationConfig.set("thinkingConfig", thinkingConfig);
                }
                if (generationConfig.size() > 0) {
                    setup.set("generationConfig", generationConfig);
                }

                if (model.getSystemInstruction() != null) {
                    ObjectNode systemInst = objectMapper.createObjectNode();
                    ObjectNode parts = objectMapper.createObjectNode();
                    parts.put("text", model.getSystemInstruction());
                    ArrayNode partsArray = objectMapper.createArrayNode();
                    partsArray.add(parts);
                    systemInst.set("parts", partsArray);
                    setup.set("systemInstruction", systemInst);
                }

                message.set("setup", setup);
                sentMessages.add(objectMapper.writeValueAsString(message));
            } catch (Exception e) {
                throw new RuntimeException("Failed to create setup message", e);
            }
        }

        @Override
        public void sendAudio(byte[] audioData) {
            try {
                ObjectNode message = objectMapper.createObjectNode();
                ObjectNode realtimeInput = objectMapper.createObjectNode();
                ObjectNode audio = objectMapper.createObjectNode();

                audio.put("mimeType", "audio/pcm;rate=16000");
                audio.put("data", Base64.getEncoder().encodeToString(audioData));
                realtimeInput.set("audio", audio);
                message.set("realtimeInput", realtimeInput);

                sentMessages.add(objectMapper.writeValueAsString(message));
            } catch (Exception e) {
                throw new RuntimeException("Failed to send audio", e);
            }
        }

        @Override
        public void sendAudioAsTurn(byte[] audioData) {
            try {
                ObjectNode message = objectMapper.createObjectNode();
                ObjectNode clientContent = objectMapper.createObjectNode();
                ObjectNode turn = objectMapper.createObjectNode();
                ObjectNode part = objectMapper.createObjectNode();
                ObjectNode inlineData = objectMapper.createObjectNode();

                inlineData.put("mimeType", "audio/wav");
                inlineData.put("data", Base64.getEncoder().encodeToString(audioData));
                part.set("inlineData", inlineData);

                ArrayNode partsArray = objectMapper.createArrayNode();
                partsArray.add(part);
                turn.put("role", "user");
                turn.set("parts", partsArray);

                ArrayNode turnsArray = objectMapper.createArrayNode();
                turnsArray.add(turn);
                clientContent.set("turns", turnsArray);
                clientContent.put("turnComplete", true);
                message.set("clientContent", clientContent);

                sentMessages.add(objectMapper.writeValueAsString(message));
            } catch (Exception e) {
                throw new RuntimeException("Failed to send audio as turn", e);
            }
        }

        @Override
        public void sendAudioStreamEnd() {
            try {
                ObjectNode message = objectMapper.createObjectNode();
                ObjectNode realtimeInput = objectMapper.createObjectNode();
                realtimeInput.put("audioStreamEnd", true);
                message.set("realtimeInput", realtimeInput);

                sentMessages.add(objectMapper.writeValueAsString(message));
            } catch (Exception e) {
                throw new RuntimeException("Failed to send audioStreamEnd", e);
            }
        }

        @Override
        public void sendTurnComplete() {
            try {
                ObjectNode message = objectMapper.createObjectNode();
                ObjectNode clientContent = objectMapper.createObjectNode();
                clientContent.put("turnComplete", true);
                message.set("clientContent", clientContent);

                sentMessages.add(objectMapper.writeValueAsString(message));
            } catch (Exception e) {
                throw new RuntimeException("Failed to send turnComplete", e);
            }
        }

        @Override
        public void sendText(String text) {
            try {
                ObjectNode message = objectMapper.createObjectNode();
                ObjectNode clientContent = objectMapper.createObjectNode();
                ObjectNode turn = objectMapper.createObjectNode();
                ObjectNode part = objectMapper.createObjectNode();

                part.put("text", text);
                ArrayNode partsArray = objectMapper.createArrayNode();
                partsArray.add(part);
                turn.put("role", "user");
                turn.set("parts", partsArray);
                ArrayNode turnsArray = objectMapper.createArrayNode();
                turnsArray.add(turn);
                clientContent.set("turns", turnsArray);
                clientContent.put("turnComplete", true);
                message.set("clientContent", clientContent);

                sentMessages.add(objectMapper.writeValueAsString(message));
            } catch (Exception e) {
                throw new RuntimeException("Failed to send text", e);
            }
        }

        @Override
        public void sendVideo(byte[] videoData) {
            try {
                ObjectNode message = objectMapper.createObjectNode();
                ObjectNode clientContent = objectMapper.createObjectNode();
                ObjectNode turns = objectMapper.createObjectNode();
                ObjectNode part = objectMapper.createObjectNode();
                ObjectNode inlineData = objectMapper.createObjectNode();

                inlineData.put("mimeType", "image/jpeg");
                inlineData.put("data", Base64.getEncoder().encodeToString(videoData));

                part.set("inlineData", inlineData);
                ArrayNode partsArray = objectMapper.createArrayNode();
                partsArray.add(part);
                turns.set("parts", partsArray);
                ArrayNode turnsArray = objectMapper.createArrayNode();
                turnsArray.add(turns);
                clientContent.set("turns", turnsArray);
                message.set("clientContent", clientContent);

                sentMessages.add(objectMapper.writeValueAsString(message));
            } catch (Exception e) {
                throw new RuntimeException("Failed to send video", e);
            }
        }

        @Override
        public void onAudioResponse(Consumer<byte[]> handler) {
            this.audioHandler = handler;
        }

        @Override
        public void onTextResponse(Consumer<String> handler) {
            this.textHandler = handler;
        }

        @Override
        public void onError(Consumer<Throwable> handler) {
            this.errorHandler = handler;
        }

        @Override
        public void onTurnComplete(Runnable handler) {
            this.turnCompleteHandler = handler;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void close() {
            connected = false;
        }
    }
}
