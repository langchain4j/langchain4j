package dev.langchain4j.model.azure;

import static com.knuddels.jtokkit.api.EncodingType.O200K_BASE;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class can be used to estimate the cost (in tokens) before calling OpenAI or when using streaming.
 * Magic numbers present in this class were found empirically while testing.
 * There are integration tests in place that are making sure that the calculations here are very close to that of OpenAI.
 */
public class AzureOpenAiTokenCountEstimator implements TokenCountEstimator {

    private static final Logger log = LoggerFactory.getLogger(AzureOpenAiTokenCountEstimator.class);
    private static final EncodingRegistry ENCODING_REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String modelName;
    private final Encoding encoding;

    /**
     * Creates an instance of the {@code AzureOpenAiTokenCountEstimator} for a given {@link AzureOpenAiChatModelName}.
     */
    public AzureOpenAiTokenCountEstimator(AzureOpenAiChatModelName modelName) {
        this(modelName.modelType());
    }

    /**
     * Creates an instance of the {@code AzureOpenAiTokenCountEstimator} for a given {@link AzureOpenAiEmbeddingModelName}.
     */
    public AzureOpenAiTokenCountEstimator(AzureOpenAiEmbeddingModelName modelName) {
        this(modelName.modelType());
    }

    /**
     * Creates an instance of the {@code AzureOpenAiTokenCountEstimator} for a given {@link AzureOpenAiLanguageModelName}.
     */
    public AzureOpenAiTokenCountEstimator(AzureOpenAiLanguageModelName modelName) {
        this(modelName.modelType());
    }

    /**
     * Creates an instance of the {@code AzureOpenAiTokenCountEstimator} for a given model name.
     */
    public AzureOpenAiTokenCountEstimator(String modelName) {
        this.modelName = ensureNotBlank(modelName, "modelName");
        if (modelName.startsWith("o") || modelName.startsWith("gpt-4.")) {
            // temporary fix until https://github.com/knuddelsgmbh/jtokkit/pull/118 is released
            this.encoding = ENCODING_REGISTRY.getEncoding(O200K_BASE);
        } else {
            this.encoding = ENCODING_REGISTRY.getEncodingForModel(modelName).orElseThrow(unknownModelException());
        }
    }

    public int estimateTokenCountInText(String text) {
        return encoding.countTokensOrdinary(text);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        int tokenCount = 1; // 1 token for role
        tokenCount += 3; // extra tokens per each message

        if (message instanceof SystemMessage) {
            tokenCount += estimateTokenCountIn((SystemMessage) message);
        } else if (message instanceof UserMessage) {
            tokenCount += estimateTokenCountIn((UserMessage) message);
        } else if (message instanceof AiMessage) {
            tokenCount += estimateTokenCountIn((AiMessage) message);
        } else if (message instanceof ToolExecutionResultMessage) {
            tokenCount += estimateTokenCountIn((ToolExecutionResultMessage) message);
        } else {
            throw new IllegalArgumentException("Unknown message type: " + message);
        }

        return tokenCount;
    }

    private int estimateTokenCountIn(SystemMessage systemMessage) {
        return estimateTokenCountInText(systemMessage.text());
    }

    private int estimateTokenCountIn(UserMessage userMessage) {
        int tokenCount = 0;

        for (Content content : userMessage.contents()) {
            if (content instanceof TextContent) {
                tokenCount += estimateTokenCountInText(((TextContent) content).text());
            } else if (content instanceof ImageContent) {
                tokenCount += estimateImageTokenCount((ImageContent) content);
            } else {
                throw illegalArgument("Unknown content type: " + content);
            }
        }

        if (userMessage.name() != null) {
            tokenCount += 1; // extra tokens per name
            tokenCount += estimateTokenCountInText(userMessage.name());
        }

        return tokenCount;
    }

    /**
     * Estimates the token count for an image based on OpenAI's image token calculation.
     *
     * @param imageContent the image content to estimate tokens for
     * @return the estimated token count
     * @see <a href="https://platform.openai.com/docs/guides/vision">OpenAI Vision API</a>
     */
    private int estimateImageTokenCount(ImageContent imageContent) {
        ImageContent.DetailLevel detailLevel = imageContent.detailLevel();

        switch (detailLevel) {
            case LOW:
                // Low detail images always cost 85 tokens
                return 85;
            case HIGH:
                // High detail images use OpenAI's tile-based calculation
                return calculateHighDetailTokens(imageContent.image());
            case AUTO:
                // AUTO mode chooses between LOW and HIGH based on image size
                return calculateAutoDetailTokens(imageContent.image());
            default:
                // Fallback to LOW detail if unknown detail level
                return 85;
        }
    }

    /**
     * Calculates tokens for HIGH detail level using OpenAI's algorithm:
     * 1. Resize image to fit within 2048x2048 while maintaining aspect ratio
     * 2. Scale the shortest side to 768px
     * 3. Count how many 512x512 tiles are needed
     * 4. Each tile costs 170 tokens + base cost of 85 tokens
     */
    private int calculateHighDetailTokens(Image image) {
        Dimension dimensions = getImageDimensions(image);
        if (dimensions == null) {
            // If we can't get dimensions, use a reasonable estimate for HIGH detail
            // Assume medium-large image (1024x1024) = 85 + 170 * 2 * 2 = 765 tokens
            return 765;
        }

        // Step 1: Resize to fit within 2048x2048 while maintaining aspect ratio
        double width = dimensions.getWidth();
        double height = dimensions.getHeight();

        if (width > 2048 || height > 2048) {
            double scale = Math.min(2048.0 / width, 2048.0 / height);
            width *= scale;
            height *= scale;
        }

        // Step 2: Scale the shortest side to 768px
        double shortestSide = Math.min(width, height);
        if (shortestSide > 0) {
            double scale = 768.0 / shortestSide;
            width *= scale;
            height *= scale;
        }

        // Step 3: Calculate number of 512x512 tiles needed
        int tilesWide = (int) Math.ceil(width / 512.0);
        int tilesHigh = (int) Math.ceil(height / 512.0);
        int totalTiles = tilesWide * tilesHigh;

        // Step 4: Calculate final token count
        return 85 + (170 * totalTiles);
    }

    /**
     * Calculates tokens for AUTO detail level.
     * Uses LOW detail (85 tokens) for small images, HIGH detail for larger images.
     * The threshold is roughly 512x512 pixels.
     */
    private int calculateAutoDetailTokens(Image image) {
        Dimension dimensions = getImageDimensions(image);
        if (dimensions == null) {
            // Conservative estimate for AUTO mode when dimensions unknown
            return 340; // Middle ground between LOW (85) and typical HIGH (765)
        }

        // If image is small (both dimensions <= 512), use LOW detail
        if (dimensions.getWidth() <= 512 && dimensions.getHeight() <= 512) {
            return 85;
        } else {
            // For larger images, use HIGH detail calculation
            return calculateHighDetailTokens(image);
        }
    }

    /**
     * Attempts to get the dimensions of an image.
     * Works with base64 encoded images and URL-based images using Java's built-in ImageIO.
     *
     * @param image the image to analyze
     * @return the dimensions if available, null otherwise
     */
    private Dimension getImageDimensions(Image image) {
        // Try to get dimensions from base64 data
        if (image.base64Data() != null) {
            try {
                byte[] imageBytes = Base64.getDecoder().decode(image.base64Data());
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (bufferedImage != null) {
                    return new Dimension(bufferedImage.getWidth(), bufferedImage.getHeight());
                }
            } catch (IOException | IllegalArgumentException e) {
                log.debug("Failed to decode or read Base64 image data, falling back to estimates: {}", e.getMessage());
            }
        }

        // Try to get dimensions from URL
        if (image.url() != null) {
            try {
                URI uri = URI.create(image.url().toString());
                URL url = uri.toURL();
                BufferedImage bufferedImage = ImageIO.read(url);
                if (bufferedImage != null) {
                    return new Dimension(bufferedImage.getWidth(), bufferedImage.getHeight());
                }
            } catch (Exception e) {
                log.debug(
                        "Failed to fetch or read URL image '{}', falling back to estimates: {}",
                        image.url(),
                        e.getMessage());
            }
        }

        return null;
    }

    private int estimateTokenCountIn(AiMessage aiMessage) {
        int tokenCount = 0;

        if (aiMessage.text() != null) {
            tokenCount += estimateTokenCountInText(aiMessage.text());
        }

        if (aiMessage.hasToolExecutionRequests()) {
            tokenCount += 6;
            if (aiMessage.toolExecutionRequests().size() == 1) {
                tokenCount -= 1;
                ToolExecutionRequest toolExecutionRequest =
                        aiMessage.toolExecutionRequests().get(0);
                tokenCount += estimateTokenCountInText(toolExecutionRequest.name()) * 2;
                tokenCount += estimateTokenCountInText(toolExecutionRequest.arguments());
            } else {
                tokenCount += 15;
                for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                    tokenCount += 7;
                    tokenCount += estimateTokenCountInText(toolExecutionRequest.name());

                    if (isNullOrBlank(toolExecutionRequest.arguments())) {
                        continue;
                    }

                    try {
                        Map<?, ?> arguments = OBJECT_MAPPER.readValue(toolExecutionRequest.arguments(), Map.class);
                        for (Map.Entry<?, ?> argument : arguments.entrySet()) {
                            tokenCount += 2;
                            tokenCount += estimateTokenCountInText(String.valueOf(argument.getKey()));
                            tokenCount += estimateTokenCountInText(String.valueOf(argument.getValue()));
                        }
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        if (modelName.startsWith("o4")) {
            tokenCount += 2;
        }

        return tokenCount;
    }

    private int estimateTokenCountIn(ToolExecutionResultMessage toolExecutionResultMessage) {
        return estimateTokenCountInText(toolExecutionResultMessage.text());
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        // see https://github.com/openai/openai-cookbook/blob/main/examples/How_to_count_tokens_with_tiktoken.ipynb

        int tokenCount = 3; // every reply is primed with <|start|>assistant<|message|>
        for (ChatMessage message : messages) {
            tokenCount += estimateTokenCountInMessage(message);
        }
        if (modelName.startsWith("o")) {
            tokenCount -= 1;
        }
        return tokenCount;
    }

    private Supplier<IllegalArgumentException> unknownModelException() {
        return () -> illegalArgument("Model '%s' is unknown to jtokkit", modelName);
    }
}
