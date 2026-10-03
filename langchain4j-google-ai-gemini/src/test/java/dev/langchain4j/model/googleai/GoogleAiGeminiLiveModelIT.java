package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for GoogleAiGeminiLiveModel.
 * Tests bidirectional streaming with audio, video, and text.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_AI_KEY", matches = ".+")
class GoogleAiGeminiLiveModelIT {

    @Test
    void should_send_text_and_receive_response() throws InterruptedException {
        GoogleAiGeminiLiveModel model = GoogleAiGeminiLiveModel.builder()
                .apiKey(System.getenv("GEMINI_AI_KEY"))
                .modelName("gemini-2.0-flash-exp")
                .responseModalities(Arrays.asList("TEXT"))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder response = new StringBuilder();

        try (LiveSession session = model.connect()) {
            session.onTextResponse(text -> {
                response.append(text);
                latch.countDown();
            });

            session.onError(error -> {
                error.printStackTrace();
                latch.countDown();
            });

            session.sendText("Hello, how are you?");

            boolean received = latch.await(30, TimeUnit.SECONDS);

            assertThat(received).as("Should receive response within 30 seconds").isTrue();
            assertThat(response.toString()).isNotEmpty();
        }
    }

    @Test
    void should_send_text_and_receive_audio_response() throws InterruptedException {
        GoogleAiGeminiLiveModel model = GoogleAiGeminiLiveModel.builder()
                .apiKey(System.getenv("GEMINI_AI_KEY"))
                .modelName("gemini-2.0-flash-exp")
                .responseModalities(Arrays.asList("AUDIO"))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        ByteArrayOutputStream allAudioChunks = new ByteArrayOutputStream();

        try (LiveSession session = model.connect()) {
            session.onAudioResponse(audioBytes -> {
                try {
                    allAudioChunks.write(audioBytes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            session.onTurnComplete(() -> {
                try {
                    Path outputPath = Path.of("target/gemini-response-24khz.wav");
                    saveAsWav(allAudioChunks.toByteArray(), 24000, outputPath);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                latch.countDown();
            });

            session.onError(error -> {
                error.printStackTrace();
                latch.countDown();
            });

            session.sendText("Say hello in one word.");

            boolean received = latch.await(30, TimeUnit.SECONDS);

            assertThat(received)
                    .as("Should receive complete audio response within 30 seconds")
                    .isTrue();
            assertThat(allAudioChunks.size())
                    .as("Should have received audio data")
                    .isGreaterThan(0);
        }
    }

    @Test
    void should_send_audio_and_receive_audio_response() throws Exception {
        GoogleAiGeminiLiveModel model = GoogleAiGeminiLiveModel.builder()
                .apiKey(System.getenv("GEMINI_AI_KEY"))
                .modelName("gemini-2.0-flash-exp")
                .responseModalities(Arrays.asList("AUDIO"))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        ByteArrayOutputStream allAudioChunks = new ByteArrayOutputStream();

        byte[] audioData;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sample-16khz.wav")) {
            if (is == null) {
                throw new RuntimeException("sample-16khz.wav not found in test resources");
            }
            audioData = is.readAllBytes();
        }

        try (LiveSession session = model.connect()) {
            session.onAudioResponse(audioBytes -> {
                try {
                    allAudioChunks.write(audioBytes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            session.onTurnComplete(() -> {
                try {
                    Path outputPath = Path.of("target/gemini-audio-to-audio-response.wav");
                    saveAsWav(allAudioChunks.toByteArray(), 24000, outputPath);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                latch.countDown();
            });

            session.onError(error -> {
                error.printStackTrace();
                latch.countDown();
            });

            session.sendAudioAsTurn(audioData);

            boolean received = latch.await(30, TimeUnit.SECONDS);

            assertThat(received)
                    .as("Should receive complete audio response within 30 seconds")
                    .isTrue();
            assertThat(allAudioChunks.size())
                    .as("Should have received audio data")
                    .isGreaterThan(0);
        }
    }

    @Test
    void should_send_video_stream_and_receive_audio_response() throws InterruptedException {
        GoogleAiGeminiLiveModel model = GoogleAiGeminiLiveModel.builder()
                .apiKey(System.getenv("GEMINI_AI_KEY"))
                .modelName("gemini-2.0-flash-exp")
                .responseModalities(Arrays.asList("AUDIO"))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        ByteArrayOutputStream allAudioChunks = new ByteArrayOutputStream();

        try (LiveSession session = model.connect()) {
            session.onAudioResponse(audioBytes -> {
                try {
                    allAudioChunks.write(audioBytes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            session.onTurnComplete(() -> {
                try {
                    Path outputPath = Path.of("target/gemini-video-to-audio-response.wav");
                    saveAsWav(allAudioChunks.toByteArray(), 24000, outputPath);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                latch.countDown();
            });

            session.onError(error -> {
                error.printStackTrace();
                latch.countDown();
            });

            int frameCount = 3;
            for (int i = 0; i < frameCount; i++) {
                byte[] frame = generateVideoFrame(640, 480, i, frameCount);
                session.sendVideo(frame);
                Thread.sleep(1000);
            }

            session.sendText("What did you see in the video? Describe the movement.");

            boolean received = latch.await(30, TimeUnit.SECONDS);

            assertThat(received)
                    .as("Should receive audio response within 30 seconds")
                    .isTrue();
            assertThat(allAudioChunks.size())
                    .as("Should have received audio data")
                    .isGreaterThan(0);
        }
    }

    private byte[] generateVideoFrame(int width, int height, int frameIndex, int totalFrames) {
        try {
            java.awt.image.BufferedImage image =
                    new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(new java.awt.Color(240, 240, 245));
            g2d.fillRect(0, 0, width, height);

            g2d.setColor(new java.awt.Color(180, 160, 140));
            g2d.fillRect(0, height * 2 / 3, width, height / 3);

            double progress = (double) frameIndex / (totalFrames - 1);
            int ballX = (int) (50 + progress * (width - 150));
            int ballY = height / 2 - 40;
            int ballSize = 80;

            g2d.setColor(new java.awt.Color(100, 100, 100, 80));
            g2d.fillOval(ballX + 10, height * 2 / 3 - 10, ballSize, 20);

            g2d.setColor(java.awt.Color.RED);
            g2d.fillOval(ballX, ballY, ballSize, ballSize);

            g2d.setColor(new java.awt.Color(255, 150, 150));
            g2d.fillOval(ballX + 15, ballY + 15, 25, 25);

            g2d.setColor(java.awt.Color.BLACK);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 20));
            g2d.drawString("Frame " + (frameIndex + 1) + "/" + totalFrames, 10, 30);

            g2d.dispose();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "JPEG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate video frame", e);
        }
    }

    private void saveAsWav(byte[] pcmData, int sampleRate, Path outputPath) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            int byteRate = sampleRate * 2;
            int dataSize = pcmData.length;
            int fileSize = 36 + dataSize;

            fos.write("RIFF".getBytes());
            fos.write(intToLittleEndian(fileSize));
            fos.write("WAVE".getBytes());
            fos.write("fmt ".getBytes());
            fos.write(intToLittleEndian(16));
            fos.write(shortToLittleEndian((short) 1));
            fos.write(shortToLittleEndian((short) 1));
            fos.write(intToLittleEndian(sampleRate));
            fos.write(intToLittleEndian(byteRate));
            fos.write(shortToLittleEndian((short) 2));
            fos.write(shortToLittleEndian((short) 16));
            fos.write("data".getBytes());
            fos.write(intToLittleEndian(dataSize));
            fos.write(pcmData);
        }
    }

    private byte[] intToLittleEndian(int value) {
        return new byte[] {
            (byte) (value & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 24) & 0xFF)
        };
    }

    private byte[] shortToLittleEndian(short value) {
        return new byte[] {(byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF)};
    }
}
