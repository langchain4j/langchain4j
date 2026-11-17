package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.CustomMimeTypesFileTypeDetector;
import dev.langchain4j.internal.DecisionModelInputUtils;
import dev.langchain4j.internal.DecisionModelInputUtils.Transcript;
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
import dev.langchain4j.model.googleai.GeminiDecisionRequest.Block;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

class GeminiDecisionMapper {

    private static final String PROVIDER = "The Gemini Decisions API";
    private static final double PROBABILITY_TOLERANCE = 1e-6;
    private static final Pattern DATA_URL_PATTERN = Pattern.compile("^data:([^;,]+)(?:;[^,]*)?,(.*)$");
    private static final CustomMimeTypesFileTypeDetector MIME_TYPE_DETECTOR = new CustomMimeTypesFileTypeDetector();
    private static final Set<ContentType> SUPPORTED_MEDIA =
            Set.of(ContentType.IMAGE, ContentType.AUDIO, ContentType.VIDEO, ContentType.PDF);
    private static final Set<String> BLOCK_CODES =
            Set.of("safety", "prohibited_content", "blocklist", "image_safety", "content_blocked");

    private GeminiDecisionMapper() {}

    static GeminiDecisionRequest toGeminiRequest(DecisionRequest request) {
        if (isNullOrBlank(request.modelName())) {
            throw new IllegalArgumentException("The model name must be set, either with "
                    + "GoogleAiGeminiDecisionModel.builder().modelName(...) or on the request with "
                    + "DecisionRequestParameters.builder().modelName(...)");
        }
        GeminiDecisionRequest geminiRequest = new GeminiDecisionRequest();
        geminiRequest.setModel(request.modelName());
        geminiRequest.setInput(toInput(request.input()));
        Map<String, GeminiDecisionRequest.Question> questions = new LinkedHashMap<>();
        request.questions().forEach((name, question) -> questions.put(name, toQuestion(question)));
        geminiRequest.setQuestions(questions);
        if (request.parameters() instanceof GoogleAiGeminiDecisionRequestParameters parameters
                && !isNullOrEmpty(parameters.customParameters())) {
            Map<String, Object> customParameters = parameters.customParameters();
            geminiRequest.setCustomParameters(customParameters);
            // a custom parameter replaces the field of the same name, which is then omitted (not sent twice)
            if (customParameters.containsKey("model")) geminiRequest.setModel(null);
            if (customParameters.containsKey("input")) geminiRequest.setInput(null);
            if (customParameters.containsKey("questions")) geminiRequest.setQuestions(null);
        }
        return geminiRequest;
    }

    private static Object toInput(DecisionInput input) {
        if (input instanceof TextInput text) {
            return text.text();
        } else if (input instanceof ContentsInput contents) {
            return contents.contents().stream().map(GeminiDecisionMapper::toBlock).toList();
        } else if (input instanceof FieldsInput fields) {
            return toLabeledBlocks(fields.fields());
        } else if (input instanceof MessagesInput messages) {
            return toTranscriptBlocks(messages.messages());
        }
        throw new UnsupportedFeatureException(
                PROVIDER + " does not support " + input.getClass().getName() + " as input");
    }

    /**
     * Each field is labeled with its name, contents such as images follow their label. Gemini joins adjacent text
     * blocks without a separator, so each text block ends with a line break.
     */
    private static List<Block> toLabeledBlocks(Map<String, Object> fields) {
        List<Block> blocks = new ArrayList<>();
        fields.forEach((name, value) -> {
            if (value instanceof Content content) {
                blocks.add(Block.text(name + ":\n"));
                blocks.add(toBlock(content));
            } else if (isContents(value)) {
                blocks.add(Block.text(name + ":\n"));
                ((List<?>) value).forEach(content -> blocks.add(toBlock((Content) content)));
            } else {
                blocks.add(Block.text(name + ": " + toJson(value) + "\n"));
            }
        });
        return blocks;
    }

    private static boolean isContents(Object value) {
        return value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Content;
    }

    /**
     * The conversation is sent as a JSON transcript, followed by the images, audio, video and PDF files it refers to,
     * each labeled with the marker that refers to it, such as {@code [audio 1]}.
     */
    private static List<Block> toTranscriptBlocks(List<ChatMessage> messages) {
        Transcript transcript = DecisionModelInputUtils.transcript(messages, PROVIDER, SUPPORTED_MEDIA);
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.text("messages: " + toJson(transcript.messages()) + "\n"));
        transcript.media().forEach((marker, content) -> {
            blocks.add(Block.text(marker + ":\n"));
            blocks.add(toBlock(content));
        });
        return blocks;
    }

    private static Block toBlock(Content content) {
        if (content instanceof TextContent text) {
            return Block.text(text.text());
        } else if (content instanceof ImageContent image) {
            return media(
                    "image",
                    image.image().url(),
                    image.image().base64Data(),
                    image.image().mimeType(),
                    resolution(image));
        } else if (content instanceof AudioContent audio) {
            return media("audio", audio.audio().url(), base64Data(audio.audio()), audio.audio().mimeType(), null);
        } else if (content instanceof VideoContent video) {
            return media(
                    "video", video.video().url(), video.video().base64Data(), video.video().mimeType(), null);
        } else if (content instanceof PdfFileContent pdf) {
            return media(
                    "document",
                    pdf.pdfFile().url(),
                    pdf.pdfFile().base64Data(),
                    getOrDefault(pdf.pdfFile().mimeType(), "application/pdf"),
                    null);
        }
        throw new UnsupportedFeatureException(
                PROVIDER + " does not support " + content.getClass().getName() + " as content");
    }

    private static Block media(
            String type,
            @Nullable URI url,
            @Nullable String base64Data,
            @Nullable String mimeType,
            @Nullable String resolution) {
        if (!isNullOrBlank(base64Data)) {
            return new Block(type, null, base64Data, null, mimeType, resolution);
        }
        if (url == null) {
            throw new IllegalArgumentException("The " + type + " has neither base64 data nor a URL");
        }
        if ("data".equals(url.getScheme())) {
            Matcher matcher = DATA_URL_PATTERN.matcher(url.toString());
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "The data URL of the " + type + " is not valid, expected data:<MIME type>;base64,<data>");
            }
            return new Block(type, null, matcher.group(2), null, matcher.group(1), resolution);
        }
        return new Block(
                type,
                null,
                null,
                url.toString(),
                getOrDefault(mimeType, () -> MIME_TYPE_DETECTOR.probeContentType(url)),
                resolution);
    }

    private static @Nullable String base64Data(Audio audio) {
        if (audio.base64Data() != null || audio.binaryData() == null) {
            return audio.base64Data();
        }
        return Base64.getEncoder().encodeToString(audio.binaryData());
    }

    /**
     * An image whose detail level was not chosen is sent without a resolution, so that Gemini's default applies.
     */
    private static @Nullable String resolution(ImageContent image) {
        if (!image.detailLevelSpecified()) {
            return null;
        }
        return switch (image.detailLevel()) {
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case ULTRA_HIGH -> "ultra_high";
            case AUTO -> null;
        };
    }

    private static GeminiDecisionRequest.Question toQuestion(Question question) {
        if (question instanceof YesNoQuestion yesNo) {
            return new GeminiDecisionRequest.Question("noul", yesNo.text(), yesNoCriteria(yesNo));
        } else if (question instanceof ChoiceQuestion choice) {
            return new GeminiDecisionRequest.Question("choice", choice.text(), choice.options());
        } else if (question instanceof ScaleQuestion scale) {
            return new GeminiDecisionRequest.Question("score", scale.text(), levels(scale));
        }
        throw new UnsupportedFeatureException(
                PROVIDER + " does not support " + question.getClass().getName() + " questions");
    }

    private static @Nullable Map<String, String> yesNoCriteria(YesNoQuestion question) {
        if (question.yesWhen() == null && question.noWhen() == null) {
            return null;
        }
        Map<String, String> criteria = new LinkedHashMap<>();
        if (question.yesWhen() != null) {
            criteria.put("true_description", question.yesWhen());
        }
        if (question.noWhen() != null) {
            criteria.put("false_description", question.noWhen());
        }
        return criteria;
    }

    /**
     * Gemini levels have a description only, so a level is sent as its label, followed by its description if any.
     */
    private static List<String> levels(ScaleQuestion scale) {
        List<String> levels = new ArrayList<>();
        for (int i = 0; i < scale.levels().size(); i++) {
            String description = scale.levelDescriptions().get(i);
            levels.add(description == null ? scale.levels().get(i) : scale.levels().get(i) + ": " + description);
        }
        return levels;
    }

    static DecisionResponse toDecisionResponse(GeminiDecisionResponse response, DecisionRequest request) {
        DecisionResponse.Builder builder = DecisionResponse.builder()
                .modelName(request.modelName())
                .tokenUsage(toTokenUsage(response.usage()));
        if (isBlocked(response)) {
            // Gemini blocks the whole input, so no question is answered
            request.questions().keySet().forEach(name -> builder.answer(name, RefusalAnswer.of()));
            return builder.build();
        }
        Map<String, GeminiDecisionResponse.Answer> answers = getOrDefault(response.answers(), Map.of());
        request.questions().forEach((name, question) -> {
            GeminiDecisionResponse.Answer answer = answers.get(name);
            if (answer == null) {
                throw new InvalidDecisionResponseException(
                        "The response contains no answer to question '%s'".formatted(name));
            }
            builder.answer(name, toAnswer(name, answer, question));
        });
        for (String name : answers.keySet()) {
            if (!request.questions().containsKey(name)) {
                throw new InvalidDecisionResponseException(
                        "The response contains an answer to the unknown question '%s'".formatted(name));
            }
        }
        return builder.build();
    }

    private static @Nullable GoogleAiGeminiTokenUsage toTokenUsage(GeminiDecisionResponse.@Nullable Usage usage) {
        if (usage == null) {
            return null;
        }
        return GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(usage.totalInputTokens())
                .outputTokenCount(usage.totalOutputTokens())
                .totalTokenCount(usage.totalTokens())
                .build();
    }

    private static boolean isBlocked(GeminiDecisionResponse response) {
        return response.promptFeedback() != null && !isNullOrBlank(response.promptFeedback().blockReason());
    }

    /**
     * Gemini answers blocked input either with {@code prompt_feedback.block_reason} (HTTP 200) or with an HTTP 400
     * error whose {@code code} is the block reason. The latter is turned into the former, so that both are handled
     * the same way. Returns {@code null} for any other error.
     */
    static @Nullable GeminiDecisionResponse toBlockedResponse(HttpException error) {
        if (error.statusCode() != 400) {
            return null;
        }
        GeminiDecisionResponse.ErrorResponse body;
        try {
            body = Json.fromJson(error.getMessage(), GeminiDecisionResponse.ErrorResponse.class);
        } catch (RuntimeException e) {
            return null;
        }
        if (body == null
                || body.error() == null
                || !(body.error().code() instanceof String code)
                || !BLOCK_CODES.contains(code)) {
            return null;
        }
        return new GeminiDecisionResponse(
                null, null, new GeminiDecisionResponse.PromptFeedback(code, body.error().message()));
    }

    private static DecisionAnswer toAnswer(String name, GeminiDecisionResponse.Answer answer, Question question) {
        if (question instanceof YesNoQuestion) {
            ensureType(name, answer, "noul");
            return YesNoAnswer.builder()
                    .probability(probability(name, "noul", required(name, "noul", answer.noul())))
                    .build();
        }
        if (question instanceof ChoiceQuestion) {
            ensureType(name, answer, "choice");
            Map<String, Double> probabilities = new LinkedHashMap<>();
            if (answer.probabilities() != null) {
                answer.probabilities()
                        .forEach((option, probability) -> probabilities.put(
                                option, probability(name, "probability of '" + option + "'", probability)));
            }
            return ChoiceAnswer.builder()
                    .value(required(name, "choice", answer.choice()))
                    .probabilities(probabilities)
                    .confidence(confidence(name, answer.confidence()))
                    .build();
        }
        ScaleQuestion scale = (ScaleQuestion) question;
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
                Double probability = answer.probabilities().getOrDefault(String.valueOf(level), 0.0);
                probabilities.add(probability(name, "probability of level " + level, probability));
            }
        }
        return ScaleAnswer.builder()
                .mean(level(name, required(name, "score", answer.score()), levels))
                .probabilities(probabilities)
                .confidence(confidence(name, answer.confidence()))
                .build();
    }

    private static void ensureType(String name, GeminiDecisionResponse.Answer answer, String expectedType) {
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
        return Math.min(1.0, Math.max(0.0, value)); // absorbs rounding errors such as 1.0000000002
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
}
