package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.genai.gaos.models.decisions.Answer;
import com.google.genai.gaos.models.decisions.BlockReason;
import com.google.genai.gaos.models.decisions.ChoiceQuestionInstructions;
import com.google.genai.gaos.models.decisions.CreateDecisionRequest;
import com.google.genai.gaos.models.decisions.CreateDecisionResponse;
import com.google.genai.gaos.models.decisions.DecisionInputContent;
import com.google.genai.gaos.models.decisions.Input;
import com.google.genai.gaos.models.decisions.NoulAnswer;
import com.google.genai.gaos.models.decisions.NoulCriteria;
import com.google.genai.gaos.models.decisions.NoulQuestion;
import com.google.genai.gaos.models.decisions.NoulQuestionInstructions;
import com.google.genai.gaos.models.decisions.PromptFeedback;
import com.google.genai.gaos.models.decisions.ScoreAnswer;
import com.google.genai.gaos.models.decisions.ScoreQuestion;
import com.google.genai.gaos.models.decisions.ScoreQuestionInstructions;
import com.google.genai.gaos.models.decisions.UnknownAnswer;
import com.google.genai.gaos.models.errors.GaosApiException;
import com.google.genai.gaos.models.interactions.AudioContentMimeType;
import com.google.genai.gaos.models.interactions.DocumentContent;
import com.google.genai.gaos.models.interactions.DocumentContentMimeType;
import com.google.genai.gaos.models.interactions.ImageContentMimeType;
import com.google.genai.gaos.models.interactions.MediaResolution;
import com.google.genai.gaos.models.interactions.Usage;
import com.google.genai.gaos.models.interactions.VideoContentMimeType;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.ContentsInput;
import dev.langchain4j.model.decision.request.DecisionInput;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.request.MessagesInput;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.TextInput;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Converts between the {@link DecisionModel} API and the Gemini Decisions API of the Google Gen AI Java SDK.
 */
class GoogleGenAiDecisionMapper {

    private static final double PROBABILITY_TOLERANCE = 1e-6;
    private static final String PROVIDER_NAME = "The Gemini Decisions API";
    private static final Pattern DATA_URL_PATTERN = Pattern.compile("^data:([^;,]+)(?:;[^,]*)?,(.*)$");
    private static final Set<String> FIELDS_SET_BY_THE_MODEL = Set.of("model", "input", "questions");
    private static final Set<ContentType> SUPPORTED_CONVERSATION_MEDIA =
            Set.of(ContentType.IMAGE, ContentType.AUDIO, ContentType.VIDEO, ContentType.PDF);
    // Safety/policy error.code values returned with HTTP 400 by the Gemini Decisions API (POST /v1beta/decisions)
    // when input is blocked, matching the PromptFeedback.block_reason values returned on HTTP 200 responses.
    private static final Set<String> BLOCK_CODES =
            Set.of("safety", "prohibited_content", "blocklist", "image_safety", "content_blocked");

    private GoogleGenAiDecisionMapper() {}

    static CreateDecisionRequest toCreateDecisionRequest(DecisionRequest request) {
        if (isNullOrBlank(request.modelName())) {
            throw new IllegalArgumentException("The model name must be set, either with "
                    + "GoogleGenAiDecisionModel.builder().modelName(...) or on the request with "
                    + "DecisionRequestParameters.builder().modelName(...)");
        }
        Map<String, Object> customParameters =
                request.parameters() instanceof GoogleGenAiDecisionRequestParameters parameters
                        ? parameters.customParameters()
                        : Map.of();
        for (String name : customParameters.keySet()) {
            if (FIELDS_SET_BY_THE_MODEL.contains(name)) {
                throw new IllegalArgumentException("'" + name + "' is set by GoogleGenAiDecisionModel and cannot be "
                        + "a custom parameter");
            }
        }
        Map<String, com.google.genai.gaos.models.decisions.Question> questions = new LinkedHashMap<>();
        questionNamesByKey(request)
                .forEach((key, name) ->
                        questions.put(key, toQuestion(request.questions().get(name))));

        Input input = toInput(request.input());
        if (customParameters.isEmpty()) {
            return CreateDecisionRequest.builder()
                    .model(request.modelName())
                    .input(input)
                    .questions(questions)
                    .build();
        }
        return new CreateDecisionRequestWithCustomParameters(input, request.modelName(), questions, customParameters);
    }

    /**
     * The request of the SDK has no place for other fields, so the custom parameters are added by this subclass, which
     * the SDK serializes as it is.
     */
    static class CreateDecisionRequestWithCustomParameters extends CreateDecisionRequest {

        private final Map<String, Object> customParameters;

        CreateDecisionRequestWithCustomParameters(
                Input input,
                String model,
                Map<String, com.google.genai.gaos.models.decisions.Question> questions,
                Map<String, Object> customParameters) {
            super(input, model, questions);
            this.customParameters = customParameters;
        }

        @JsonAnyGetter
        public Map<String, Object> customParameters() {
            return customParameters;
        }

        @Override
        public String toString() {
            return super.toString() + " customParameters=" + customParameters;
        }
    }

    /**
     * Gemini accepts only question keys made of {@code A-Z}, {@code a-z}, {@code 0-9}, {@code _} and {@code -}
     * (up to 64 characters), and its model does not read them, so each question is sent under a positional key such
     * as {@code q0}.
     */
    private static Map<String, String> questionNamesByKey(DecisionRequest request) {
        Map<String, String> names = new LinkedHashMap<>();
        request.questions().keySet().forEach(name -> names.put("q" + names.size(), name));
        return names;
    }

    private static Input toInput(DecisionInput input) {
        if (input instanceof TextInput text) {
            return Input.of(text.text());
        }
        if (input instanceof FieldsInput fields) {
            return Input.of(separated(toLabeledContents(fields.fields())));
        }
        if (input instanceof ContentsInput contents) {
            return Input.of(separated(toDecisionInputContents(contents.contents())));
        }
        if (input instanceof MessagesInput messages) {
            return Input.of(separated(toTranscriptContents(messages.messages())));
        }
        throw new UnsupportedFeatureException(
                PROVIDER_NAME + " does not support " + input.getClass().getName() + " as input");
    }

    /**
     * Gemini joins adjacent text blocks without a separator, so a line break is added to a text block that is
     * followed by another text block, unless it already ends with whitespace.
     */
    private static List<DecisionInputContent> separated(List<DecisionInputContent> blocks) {
        List<DecisionInputContent> separated = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            DecisionInputContent block = blocks.get(i);
            if (i + 1 < blocks.size()
                    && block instanceof com.google.genai.gaos.models.interactions.TextContent textBlock
                    && blocks.get(i + 1) instanceof com.google.genai.gaos.models.interactions.TextContent
                    && !endsWithWhitespace(textBlock)) {
                block = textContent(textBlock.text().orElse("") + "\n");
            }
            separated.add(block);
        }
        return separated;
    }

    private static boolean endsWithWhitespace(com.google.genai.gaos.models.interactions.TextContent block) {
        String text = block.text().orElse(null);
        return text != null && !text.isEmpty() && Character.isWhitespace(text.charAt(text.length() - 1));
    }

    /**
     * Each field is labeled with its name; multimodal contents follow their label.
     */
    private static List<DecisionInputContent> toLabeledContents(Map<String, Object> fields) {
        List<DecisionInputContent> parts = new ArrayList<>();
        fields.forEach((name, value) -> {
            if (value instanceof Content content) {
                parts.add(textContent(name + ":\n"));
                parts.add(toDecisionInputContent(content));
            } else if (isContents(value)) {
                parts.add(textContent(name + ":\n"));
                ((List<?>) value).forEach(content -> parts.add(toDecisionInputContent((Content) content)));
            } else {
                parts.add(textContent(name + ": " + Json.toJson(value) + "\n"));
            }
        });
        return parts;
    }

    private static boolean isContents(Object value) {
        return value instanceof List<?> list && !list.isEmpty() && list.stream().allMatch(Content.class::isInstance);
    }

    private static List<DecisionInputContent> toDecisionInputContents(List<Content> contents) {
        List<DecisionInputContent> parts = new ArrayList<>(contents.size());
        contents.forEach(content -> parts.add(toDecisionInputContent(content)));
        return parts;
    }

    /**
     * The conversation is sent as a JSON transcript, followed by the images, audio, video and PDF files it refers to,
     * each labeled with the marker that refers to it, such as {@code [audio 1]}.
     */
    private static List<DecisionInputContent> toTranscriptContents(List<ChatMessage> messages) {
        ConversationTranscript transcript = buildTranscript(messages);
        List<DecisionInputContent> parts = new ArrayList<>();
        parts.add(textContent("messages: " + Json.toJson(transcript.messages()) + "\n"));
        transcript.media().forEach((marker, content) -> {
            parts.add(textContent(marker + ":\n"));
            parts.add(toDecisionInputContent(content));
        });
        return parts;
    }

    private static ConversationTranscript buildTranscript(List<ChatMessage> messages) {
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Content> media = new LinkedHashMap<>();
        Map<ContentType, Integer> mediaCounts = new EnumMap<>(ContentType.class);
        for (ChatMessage message : messages) {
            Map<String, Object> entry = new LinkedHashMap<>();
            if (message instanceof SystemMessage systemMessage) {
                entry.put("role", "system");
                entry.put("text", systemMessage.text());
            } else if (message instanceof UserMessage userMessage) {
                entry.put("role", "user");
                if (userMessage.name() != null) {
                    entry.put("name", userMessage.name());
                }
                entry.put("text", transcriptText(userMessage.contents(), media, mediaCounts));
            } else if (message instanceof AiMessage aiMessage) {
                if ((aiMessage.text() == null || aiMessage.text().isEmpty()) && !aiMessage.hasToolExecutionRequests()) {
                    continue;
                }
                entry.put("role", "assistant");
                if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
                    entry.put("text", aiMessage.text());
                }
                if (aiMessage.hasToolExecutionRequests()) {
                    List<Map<String, Object>> toolCalls = new ArrayList<>();
                    for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                        Map<String, Object> toolCall = new LinkedHashMap<>();
                        putIfNotNull(toolCall, "id", toolRequest.id());
                        toolCall.put("name", toolRequest.name());
                        putIfNotNull(toolCall, "arguments", toolRequest.arguments());
                        toolCalls.add(toolCall);
                    }
                    entry.put("tool_calls", toolCalls);
                }
            } else if (message instanceof ToolExecutionResultMessage result) {
                entry.put("role", "tool");
                putIfNotNull(entry, "tool_call_id", result.id());
                entry.put("name", result.toolName());
                entry.put("text", transcriptText(result.contents(), media, mediaCounts));
                if (Boolean.TRUE.equals(result.isError())) {
                    entry.put("is_error", true);
                }
            } else {
                throw new UnsupportedFeatureException(PROVIDER_NAME + " does not support "
                        + message.getClass().getSimpleName() + " in a conversation. Supported messages: "
                        + "SystemMessage, UserMessage, AiMessage and ToolExecutionResultMessage");
            }
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("The conversation has nothing to send: AiMessages without text and tool "
                    + "execution requests are skipped");
        }
        return new ConversationTranscript(entries, media);
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String transcriptText(
            List<Content> contents, Map<String, Content> media, Map<ContentType, Integer> mediaCounts) {
        List<String> parts = new ArrayList<>();
        for (Content content : contents) {
            if (content instanceof TextContent textContent) {
                parts.add(textContent.text());
                continue;
            }
            ContentType kind = content.type();
            if (kind == null
                    || !SUPPORTED_CONVERSATION_MEDIA.contains(kind)
                    || !kind.getContentClass().isInstance(content)) {
                throw new UnsupportedFeatureException(
                        PROVIDER_NAME + " supports only text, images, audio, video and PDF files in a conversation, "
                                + "but it contains " + content.type());
            }
            int position = mediaCounts.merge(kind, 1, Integer::sum);
            String marker = "[" + kind.name().toLowerCase(Locale.ROOT) + " " + position + "]";
            media.put(marker, content);
            parts.add(marker);
        }
        return String.join("\n", parts);
    }

    private record ConversationTranscript(List<Map<String, Object>> messages, Map<String, Content> media) {}

    private static DecisionInputContent textContent(String text) {
        return com.google.genai.gaos.models.interactions.TextContent.builder()
                .text(text)
                .build();
    }

    private static DecisionInputContent toDecisionInputContent(Content content) {
        if (content instanceof TextContent text) {
            return textContent(text.text());
        }
        if (content instanceof ImageContent imageContent) {
            Image image = imageContent.image();
            MediaData media = resolveMedia("image", image.url(), image.mimeType(), image.base64Data(), null, null);
            return com.google.genai.gaos.models.interactions.ImageContent.builder()
                    .data(media.data())
                    .uri(media.uri())
                    .mimeType(media.mimeType() == null ? null : ImageContentMimeType.of(media.mimeType()))
                    .resolution(
                            imageContent.detailLevelSpecified() ? toMediaResolution(imageContent.detailLevel()) : null)
                    .build();
        }
        if (content instanceof AudioContent audioContent) {
            Audio audio = audioContent.audio();
            MediaData media =
                    resolveMedia("audio", audio.url(), audio.mimeType(), audio.base64Data(), audio.binaryData(), null);
            return com.google.genai.gaos.models.interactions.AudioContent.builder()
                    .data(media.data())
                    .uri(media.uri())
                    .mimeType(media.mimeType() == null ? null : AudioContentMimeType.of(media.mimeType()))
                    .build();
        }
        if (content instanceof VideoContent videoContent) {
            Video video = videoContent.video();
            MediaData media = resolveMedia("video", video.url(), video.mimeType(), video.base64Data(), null, null);
            return com.google.genai.gaos.models.interactions.VideoContent.builder()
                    .data(media.data())
                    .uri(media.uri())
                    .mimeType(media.mimeType() == null ? null : VideoContentMimeType.of(media.mimeType()))
                    .build();
        }
        if (content instanceof PdfFileContent pdfContent) {
            PdfFile pdf = pdfContent.pdfFile();
            MediaData media =
                    resolveMedia("document", pdf.url(), pdf.mimeType(), pdf.base64Data(), null, "application/pdf");
            return DocumentContent.builder()
                    .data(media.data())
                    .uri(media.uri())
                    .mimeType(media.mimeType() == null ? null : DocumentContentMimeType.of(media.mimeType()))
                    .build();
        }
        throw new UnsupportedFeatureException(
                PROVIDER_NAME + " does not support " + content.getClass().getName() + " as content");
    }

    private static MediaResolution toMediaResolution(ImageContent.DetailLevel detailLevel) {
        if (detailLevel == null) {
            return null;
        }
        return switch (detailLevel) {
            case LOW -> MediaResolution.LOW;
            case MEDIUM -> MediaResolution.MEDIUM;
            case HIGH -> MediaResolution.HIGH;
            case ULTRA_HIGH -> MediaResolution.ULTRA_HIGH;
            case AUTO -> null;
        };
    }

    private static MediaData resolveMedia(
            String type,
            @Nullable URI url,
            @Nullable String mimeType,
            @Nullable String base64Data,
            byte @Nullable [] binaryData,
            @Nullable String defaultMimeType) {
        if (!isNullOrBlank(base64Data)) {
            return new MediaData(base64Data, null, isNullOrBlank(mimeType) ? defaultMimeType : mimeType);
        }
        if (binaryData != null) {
            return new MediaData(
                    Base64.getEncoder().encodeToString(binaryData),
                    null,
                    isNullOrBlank(mimeType) ? defaultMimeType : mimeType);
        }
        if (url == null) {
            throw new IllegalArgumentException("The " + type + " has neither base64 data nor a URL");
        }
        String scheme = url.getScheme();
        if ("data".equalsIgnoreCase(scheme)) {
            Matcher matcher = DATA_URL_PATTERN.matcher(url.toString());
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "The data URL of the " + type + " is not valid, expected data:<MIME type>;base64,<data>");
            }
            String resolvedMime = !isNullOrBlank(mimeType) ? mimeType : matcher.group(1);
            return new MediaData(matcher.group(2), null, resolvedMime);
        }
        String resolvedMime = !isNullOrBlank(mimeType)
                ? mimeType
                : (defaultMimeType != null ? defaultMimeType : guessRemoteMimeType(url));
        return new MediaData(null, url.toString(), resolvedMime);
    }

    /**
     * Guesses the MIME type of a remote URI from its file name / extension only, without accessing the local file
     * system. Returns {@code null} when the URI has no recognizable extension.
     */
    private static @Nullable String guessRemoteMimeType(URI url) {
        String uriPath = url.getPath();
        if (isNullOrBlank(uriPath)) {
            return null;
        }
        int slash = uriPath.lastIndexOf('/');
        String fileName = slash >= 0 ? uriPath.substring(slash + 1) : uriPath;
        if (fileName.isEmpty()) {
            return null;
        }
        try {
            return GoogleGenAiContentMapper.detectMimeType(url);
        } catch (IllegalArgumentException ignored) {
            return URLConnection.guessContentTypeFromName(fileName);
        }
    }

    private record MediaData(
            @Nullable String data,
            @Nullable String uri,
            @Nullable String mimeType) {}

    private static com.google.genai.gaos.models.decisions.Question toQuestion(Question question) {
        if (question instanceof YesNoQuestion yesNo) {
            NoulQuestion.Builder builder =
                    NoulQuestion.builder().instructions(NoulQuestionInstructions.of(yesNo.text()));
            if (yesNo.yesWhen() != null || yesNo.noWhen() != null) {
                builder.criteria(NoulCriteria.builder()
                        .trueDescription(yesNo.yesWhen())
                        .falseDescription(yesNo.noWhen())
                        .build());
            }
            return builder.build();
        }
        if (question instanceof ChoiceQuestion choice) {
            return com.google.genai.gaos.models.decisions.ChoiceQuestion.builder()
                    .instructions(ChoiceQuestionInstructions.of(choice.text()))
                    .criteria(choice.options())
                    .build();
        }
        if (question instanceof ScaleQuestion scale) {
            List<String> criteria = new ArrayList<>(scale.levels().size());
            for (int i = 0; i < scale.levels().size(); i++) {
                String level = scale.levels().get(i);
                String description = scale.levelDescriptions().get(i);
                criteria.add(description == null ? level : level + ": " + description);
            }
            return ScoreQuestion.builder()
                    .instructions(ScoreQuestionInstructions.of(scale.text()))
                    .criteria(criteria)
                    .build();
        }
        throw new UnsupportedFeatureException(
                PROVIDER_NAME + " does not support " + question.getClass().getName() + " questions");
    }

    /**
     * Gemini answers blocked input either with {@code prompt_feedback.block_reason} (HTTP 200) or with an HTTP 400
     * error whose {@code code} is the block reason. The latter is turned into the former, so that both are handled
     * the same way. Returns {@code null} for any other error.
     */
    static @Nullable CreateDecisionResponse toBlockedResponse(GaosApiException error) {
        if (error.code() != 400) {
            return null;
        }
        String body = error.bodyAsString().orElse(null);
        if (isNullOrBlank(body)
                || !(GoogleGenAiExceptionMapper.errorField(body, "code") instanceof String code)
                || !BLOCK_CODES.contains(code)) {
            return null;
        }
        String message = GoogleGenAiExceptionMapper.errorField(body, "message") instanceof String text ? text : null;
        return CreateDecisionResponse.builder()
                .promptFeedback(PromptFeedback.builder()
                        .blockReason(BlockReason.of(code))
                        .blockReasonMessage(message)
                        .build())
                .build();
    }

    static DecisionResponse toDecisionResponse(CreateDecisionResponse response, DecisionRequest request) {
        PromptFeedback promptFeedback =
                isBlocked(response) ? response.promptFeedback().orElse(null) : null;
        DecisionResponse.Builder builder = DecisionResponse.builder()
                .metadata(GoogleGenAiDecisionResponseMetadata.builder()
                        .modelName(request.modelName())
                        .tokenUsage(response.usage()
                                .map(GoogleGenAiDecisionMapper::toTokenUsage)
                                .orElse(null))
                        .blockReason(
                                promptFeedback == null
                                        ? null
                                        : promptFeedback
                                                .blockReason()
                                                .map(BlockReason::value)
                                                .orElse(null))
                        .blockMessage(
                                promptFeedback == null
                                        ? null
                                        : promptFeedback.blockReasonMessage().orElse(null))
                        .build());
        if (promptFeedback != null) {
            // Gemini blocks the whole input, so no question is answered
            request.questions().keySet().forEach(name -> builder.answer(name, RefusalAnswer.of()));
            return builder.build();
        }

        Map<String, Answer> answers = response.answers().orElse(Map.of());
        Map<String, String> namesByKey = questionNamesByKey(request);
        for (String key : answers.keySet()) {
            if (!namesByKey.containsKey(key)) {
                throw new InvalidDecisionResponseException(
                        "The response contains an answer under the unknown key '%s'".formatted(key));
            }
        }
        namesByKey.forEach((key, name) -> {
            Answer answer = answers.get(key);
            if (answer == null) {
                throw new InvalidDecisionResponseException(
                        "The response contains no answer to question '%s' (key '%s')".formatted(name, key));
            }
            builder.answer(name, toAnswer(name, answer, request.questions().get(name)));
        });
        return builder.build();
    }

    private static boolean isBlocked(CreateDecisionResponse response) {
        return response.promptFeedback().isPresent()
                && response.promptFeedback().get().blockReason().isPresent()
                && !isNullOrBlank(
                        response.promptFeedback().get().blockReason().get().value());
    }

    private static DecisionAnswer toAnswer(String name, Answer rawAnswer, Question question) {
        ParsedAnswer answer = parseAnswer(name, rawAnswer);
        if (question instanceof YesNoQuestion) {
            ensureType(name, answer, "noul");
            return YesNoAnswer.from(probability(name, "noul", required(name, "noul", answer.noul())));
        }
        if (question instanceof ChoiceQuestion) {
            ensureType(name, answer, "choice");
            Map<String, Double> probabilities = new LinkedHashMap<>();
            if (answer.probabilities() != null) {
                answer.probabilities()
                        .forEach((option, prob) ->
                                probabilities.put(option, probability(name, "probability of '" + option + "'", prob)));
            }
            return ChoiceAnswer.builder()
                    .value(required(name, "choice", answer.choice()))
                    .probabilities(probabilities)
                    .confidence(confidence(name, answer.confidence()))
                    .build();
        }
        if (!(question instanceof ScaleQuestion scale)) {
            throw new UnsupportedFeatureException(
                    PROVIDER_NAME + " does not support " + question.getClass().getName() + " questions");
        }
        ensureType(name, answer, "score");
        int levels = scale.levels().size();
        List<Double> probabilities = new ArrayList<>();
        if (!isNullOrEmpty(answer.probabilities())) {
            answer.probabilities().keySet().forEach(level -> {
                if (!isLevelIndex(level, levels)) {
                    throw invalid(
                            name, "has a probability for level '%s', but the levels are 0 to %s", level, levels - 1);
                }
            });
            for (int level = 0; level < levels; level++) {
                Double prob = answer.probabilities().getOrDefault(String.valueOf(level), 0.0);
                probabilities.add(probability(name, "probability of level " + level, prob));
            }
        }
        return ScaleAnswer.builder()
                .mean(level(name, required(name, "score", answer.score()), levels))
                .probabilities(probabilities)
                .confidence(confidence(name, answer.confidence()))
                .build();
    }

    private static ParsedAnswer parseAnswer(String name, Answer answer) {
        if (answer instanceof NoulAnswer noul) {
            return new ParsedAnswer(
                    "noul",
                    noul.noul().orElse(null),
                    null,
                    null,
                    null,
                    noul.confidence().orElse(null));
        }
        if (answer instanceof com.google.genai.gaos.models.decisions.ChoiceAnswer choice) {
            return new ParsedAnswer(
                    "choice",
                    null,
                    choice.choice().orElse(null),
                    null,
                    choice.probabilities().orElse(null),
                    choice.confidence().orElse(null));
        }
        if (answer instanceof ScoreAnswer score) {
            return new ParsedAnswer(
                    "score",
                    null,
                    null,
                    score.score().orElse(null),
                    score.probabilities().orElse(null),
                    score.confidence().orElse(null));
        }
        if (answer instanceof UnknownAnswer unknown) {
            JsonNode node = unknown.asJson();
            if (node == null || !node.isObject()) {
                throw invalid(name, "is not a JSON object");
            }
            String type = node.hasNonNull("type") ? node.get("type").asText() : null;
            Double noul = readDouble(name, "noul", node.get("noul"));
            String choice = node.hasNonNull("choice") ? node.get("choice").asText() : null;
            Double score = readDouble(name, "score", node.get("score"));
            Double confidence = readDouble(name, "confidence", node.get("confidence"));
            Map<String, Double> probabilities = null;
            JsonNode probsNode = node.get("probabilities");
            if (probsNode != null && !probsNode.isNull()) {
                if (!probsNode.isObject()) {
                    throw invalid(name, "has non-object 'probabilities'");
                }
                probabilities = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> entry : probsNode.properties()) {
                    probabilities.put(
                            entry.getKey(),
                            readDouble(name, "probability of '" + entry.getKey() + "'", entry.getValue()));
                }
            }
            return new ParsedAnswer(type, noul, choice, score, probabilities, confidence);
        }
        throw invalid(name, "has an unknown type: %s", answer.type());
    }

    private static @Nullable Double readDouble(String name, String field, @Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException e) {
                throw invalid(name, "has an invalid %s: %s", field, node.asText());
            }
        }
        throw invalid(name, "has an invalid %s: %s", field, node);
    }

    private record ParsedAnswer(
            @Nullable String type,
            @Nullable Double noul,
            @Nullable String choice,
            @Nullable Double score,
            @Nullable Map<String, Double> probabilities,
            @Nullable Double confidence) {}

    private static void ensureType(String name, ParsedAnswer answer, String expectedType) {
        if (answer.type() != null && !answer.type().equals(expectedType)) {
            throw invalid(name, "has type '%s' instead of '%s'", answer.type(), expectedType);
        }
    }

    private static <T> T required(String name, String field, @Nullable T value) {
        if (value == null) {
            throw invalid(name, "has no '%s'", field);
        }
        return value;
    }

    private static @Nullable Double confidence(String name, @Nullable Double confidence) {
        return confidence == null ? null : probability(name, "confidence", confidence);
    }

    private static double probability(String name, String field, @Nullable Double value) {
        if (value == null || value.isNaN() || value < -PROBABILITY_TOLERANCE || value > 1 + PROBABILITY_TOLERANCE) {
            throw invalid(name, "has an invalid %s: %s", field, value);
        }
        return Math.min(1.0, Math.max(0.0, value));
    }

    private static double level(String name, double value, int levels) {
        if (Double.isNaN(value) || value < -PROBABILITY_TOLERANCE || value > levels - 1 + PROBABILITY_TOLERANCE) {
            throw invalid(name, "has an invalid score: %s, but the levels are 0 to %s", value, levels - 1);
        }
        return Math.min(levels - 1, Math.max(0.0, value));
    }

    private static boolean isLevelIndex(String level, int levels) {
        try {
            int index = Integer.parseInt(level);
            return index >= 0 && index < levels && String.valueOf(index).equals(level);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static InvalidDecisionResponseException invalid(String name, String format, Object... args) {
        return new InvalidDecisionResponseException(
                "The answer to question '%s' %s".formatted(name, format.formatted(args)));
    }

    private static GoogleGenAiTokenUsage toTokenUsage(Usage usage) {
        return GoogleGenAiTokenUsage.builder()
                .inputTokenCount(usage.totalInputTokens().orElse(null))
                .outputTokenCount(usage.totalOutputTokens().orElse(null))
                .totalTokenCount(usage.totalTokens().orElse(null))
                .build();
    }
}
