package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * WebSocket-based implementation of LiveSession for Gemini Live API.
 * Supports bidirectional streaming of audio, video, and text.
 */
class LiveSessionImpl extends WebSocketClient implements LiveSession {

    private static final String LIVE_API_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent";

    private final GoogleAiGeminiLiveModel model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Consumer<byte[]> audioHandler;
    private Consumer<String> textHandler;
    private Consumer<Throwable> errorHandler;
    private Runnable turnCompleteHandler;

    private volatile Exception connectionError;
    private volatile String closeReason;
    private volatile int closeCode;
    private final CountDownLatch openLatch = new CountDownLatch(1);
    private final CountDownLatch setupCompleteLatch = new CountDownLatch(1);

    LiveSessionImpl(GoogleAiGeminiLiveModel model) {
        super(createUri(model.getApiKey()));
        this.model = model;

        configureSSL();
        connectAndSetup();
    }

    private void configureSSL() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            SSLSocketFactory factory = sslContext.getSocketFactory();
            this.setSocketFactory(factory);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure SSL", e);
        }
    }

    private void connectAndSetup() {
        try {
            boolean connected = this.connectBlocking(10, TimeUnit.SECONDS);

            if (!this.isOpen()) {
                String errorMsg = "Failed to connect to Gemini Live API";
                if (closeReason != null && !closeReason.isEmpty()) {
                    errorMsg += " (code=" + closeCode + "): " + closeReason;
                } else if (connectionError != null) {
                    errorMsg += ": " + connectionError.getMessage();
                } else {
                    errorMsg += " (code=" + closeCode + "): connection closed immediately";
                }
                throw new RuntimeException(errorMsg, connectionError);
            }

            sendSetup();

            boolean setupReceived = setupCompleteLatch.await(10, TimeUnit.SECONDS);
            if (!setupReceived) {
                throw new RuntimeException("Timeout waiting for setup confirmation from Gemini Live API");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to connect to Gemini Live API", e);
        }
    }

    private static URI createUri(String apiKey) {
        try {
            return new URI(LIVE_API_ENDPOINT + "?key=" + apiKey);
        } catch (Exception e) {
            throw new RuntimeException("Invalid URI", e);
        }
    }

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

                if (model.getResponseModalities().stream().anyMatch(m -> m.equalsIgnoreCase("AUDIO"))) {
                    ObjectNode speechConfig = objectMapper.createObjectNode();
                    ObjectNode voiceConfig = objectMapper.createObjectNode();
                    ObjectNode prebuiltVoiceConfig = objectMapper.createObjectNode();
                    prebuiltVoiceConfig.put("voiceName", "Aoede");
                    voiceConfig.set("prebuiltVoiceConfig", prebuiltVoiceConfig);
                    speechConfig.set("voiceConfig", voiceConfig);
                    generationConfig.set("speechConfig", speechConfig);
                }
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

            ObjectNode realtimeInputConfig = objectMapper.createObjectNode();
            ObjectNode automaticActivityDetection = objectMapper.createObjectNode();
            automaticActivityDetection.put("disabled", false);
            realtimeInputConfig.set("automaticActivityDetection", automaticActivityDetection);
            setup.set("realtimeInputConfig", realtimeInputConfig);

            message.set("setup", setup);
            send(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            throw new RuntimeException("Failed to send setup", e);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        openLatch.countDown();
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);

            if (json.has("setupComplete")) {
                setupCompleteLatch.countDown();
                return;
            }

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
                                    String base64Audio = inlineData.get("data").asText();
                                    byte[] audioBytes = Base64.getDecoder().decode(base64Audio);
                                    audioHandler.accept(audioBytes);
                                }
                            }
                        }
                    }
                }

                if (serverContent.has("turnComplete")
                        && serverContent.get("turnComplete").asBoolean()) {
                    if (turnCompleteHandler != null) {
                        turnCompleteHandler.run();
                    }
                }
            }
        } catch (Exception e) {
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        byte[] data = new byte[bytes.remaining()];
        bytes.get(data);
        String message = new String(data, StandardCharsets.UTF_8);
        onMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        this.closeCode = code;
        this.closeReason = reason;
        openLatch.countDown();
        setupCompleteLatch.countDown();
    }

    @Override
    public void onError(Exception ex) {
        this.connectionError = ex;
        openLatch.countDown();
        if (errorHandler != null) {
            errorHandler.accept(ex);
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

            send(objectMapper.writeValueAsString(message));
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

            send(objectMapper.writeValueAsString(message));
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

            send(objectMapper.writeValueAsString(message));
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

            send(objectMapper.writeValueAsString(message));
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

            send(objectMapper.writeValueAsString(message));
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

            send(objectMapper.writeValueAsString(message));
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
        return this.isOpen();
    }
}
