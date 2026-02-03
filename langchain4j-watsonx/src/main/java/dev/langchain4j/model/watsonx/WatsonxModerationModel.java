package dev.langchain4j.model.watsonx;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.detection.DetectionService;
import com.ibm.watsonx.ai.detection.DetectionTextRequest;
import com.ibm.watsonx.ai.detection.DetectionTextResponse;
import com.ibm.watsonx.ai.detection.detector.BaseDetector;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * A {@link ModerationModel} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * ModerationModel chatModel = WatsonxModerationModel.builder()
 *     .baseUrl("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .projectId("...")
 *     .detectors(Pii.ofDefaults(), GraniteGuardian.ofDefaults())
 *     .build();
 * }</pre>
 *
 */
public class WatsonxModerationModel implements ModerationModel {
    private final List<BaseDetector> detectors;
    private final DetectionService detectionService;

    public WatsonxModerationModel(Builder builder) {

        if (isNull(builder.detectors) || builder.detectors.isEmpty())
            throw new IllegalArgumentException("At least one detector must be provided");

        detectors = builder.detectors;

        var detectionServiceBuilder = nonNull(builder.authenticator)
                ? DetectionService.builder().authenticator(builder.authenticator)
                : DetectionService.builder().apiKey(builder.apiKey);

        detectionService = detectionServiceBuilder
                .baseUrl(builder.baseUrl)
                .version(builder.version)
                .projectId(builder.projectId)
                .spaceId(builder.spaceId)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();
    }

    @Override
    public Response<Moderation> moderate(String text) {

        var request =
                DetectionTextRequest.builder().input(text).detectors(detectors).build();

        return WatsonxExceptionMapper.INSTANCE.withExceptionMapper(
                () -> detectionService.detect(request).detections().stream()
                        .findFirst()
                        .map(this::createModerationResponse)
                        .orElse(Response.from(Moderation.notFlagged())));
    }

    @Override
    public Response<Moderation> moderate(List<ChatMessage> messages) {

        var futures = messages.stream()
                .map(this::toText)
                .map(message -> CompletableFuture.supplyAsync(
                        () -> moderate(message), DefaultExecutorProvider.getDefaultExecutorService()))
                .toList();

        try {

            return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(response -> response.content().flagged())
                    .findFirst()
                    .orElse(Response.from(Moderation.notFlagged()));

        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof LangChain4jException langchainException
                    ? langchainException
                    : new RuntimeException(cause);
        }
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * ModerationModel chatModel = WatsonxModerationModel.builder()
     *     .baseUrl("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .projectId("...")
     *     .detectors(Pii.ofDefaults(), GraniteGuardian.ofDefaults())
     *     .build();
     * }</pre>
     *
     * @return {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    private Response<Moderation> createModerationResponse(DetectionTextResponse detectionTextResponse) {
        Moderation moderation = Moderation.flagged(detectionTextResponse.text());
        Map<String, Object> metadata = Map.of(
                "detection", detectionTextResponse.detection(),
                "detection_type", detectionTextResponse.detectionType(),
                "start", detectionTextResponse.start(),
                "end", detectionTextResponse.end(),
                "score", detectionTextResponse.score());
        return Response.from(moderation, null, null, metadata);
    }

    private String toText(ChatMessage chatMessage) {
        if (chatMessage instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        } else if (chatMessage instanceof UserMessage userMessage) {
            return userMessage.singleText();
        } else if (chatMessage instanceof AiMessage aiMessage) {
            return aiMessage.text();
        } else if (chatMessage instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return toolExecutionResultMessage.text();
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + chatMessage.type());
        }
    }

    /**
     * Builder class for constructing {@link WatsonxModerationModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxBuilder<Builder> {
        private List<BaseDetector> detectors;

        private Builder() {}

        /**
         * Sets the list of detectors to use.
         *
         * @param detectors the list of detectors
         */
        public Builder detectors(List<BaseDetector> detectors) {
            this.detectors = detectors;
            return this;
        }

        /**
         * Sets the list of detectors to use.
         *
         * @param detectors the list of detectors
         */
        public Builder detectors(BaseDetector... detectors) {
            return detectors(List.of(detectors));
        }

        public WatsonxModerationModel build() {
            return new WatsonxModerationModel(this);
        }
    }
}
