package dev.langchain4j.model.typesafe.internal;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.DecisionModelInputUtils;
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
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.typesafe.TypeSafeBoxAnswer;
import dev.langchain4j.model.typesafe.TypeSafeBoxQuestion;
import dev.langchain4j.model.typesafe.TypeSafeDecisionRequestParameters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps {@link DecisionRequest}s to the System One request format and System One responses to
 * {@link DecisionResponse}s.
 */
@Internal
public final class TypeSafeMapper {

    private static final double PROBABILITY_TOLERANCE = 1e-6;

    // unlikely to appear in text, since the server replaces every occurrence with the image
    private static final String IMAGE_REFERENCE_PREFIX = "{{langchain4j-image-";
    private static final Map<String, String> IMAGE_FORMATS =
            Map.of("image/png", "png", "image/jpeg", "jpeg", "image/jpg", "jpg");

    private TypeSafeMapper() {}

    public static TypeSafeRequest toTypeSafeRequest(DecisionRequest request) {
        if (isNullOrBlank(request.modelName())) {
            throw new IllegalArgumentException("The model name must be set, either with "
                    + "TypeSafeDecisionModel.builder().modelName(...) or on the request with "
                    + "DecisionRequestParameters.builder().modelName(...)");
        }
        TypeSafeRequest typeSafeRequest = new TypeSafeRequest();
        typeSafeRequest.model = request.modelName();
        Map<String, Map<String, String>> assets = new LinkedHashMap<>();
        typeSafeRequest.state = state(request.input(), assets);
        typeSafeRequest.assets = assets.isEmpty() ? null : assets;
        typeSafeRequest.questions = new LinkedHashMap<>();
        request.questions().forEach((name, question) -> typeSafeRequest.questions.put(name, toQuestion(question)));
        if (request.parameters() instanceof TypeSafeDecisionRequestParameters parameters
                && !isNullOrEmpty(parameters.customParameters())) {
            Map<String, Object> customParameters = parameters.customParameters();
            typeSafeRequest.customParameters = customParameters;
            // a custom parameter replaces the field of the same name, which is then omitted (not sent twice)
            if (customParameters.containsKey("model")) typeSafeRequest.model = null;
            if (customParameters.containsKey("state")) typeSafeRequest.state = null;
            if (customParameters.containsKey("questions")) typeSafeRequest.questions = null;
            if (customParameters.containsKey("assets")) typeSafeRequest.assets = null;
        }
        return typeSafeRequest;
    }

    private static Object state(DecisionInput input, Map<String, Map<String, String>> assets) {
        if (input instanceof TextInput text) {
            return text.text();
        } else if (input instanceof FieldsInput fields) {
            Map<String, Object> state = new LinkedHashMap<>();
            fields.fields()
                    .forEach((name, value) -> state.put(
                            name,
                            value instanceof Content
                                            || value instanceof List<?> list
                                                    && !list.isEmpty()
                                                    && list.get(0) instanceof Content
                                    ? text(value instanceof Content ? List.of(value) : (List<?>) value, assets)
                                    : value));
            return state;
        } else if (input instanceof ContentsInput contents) {
            return text(contents.contents(), assets);
        } else if (input instanceof MessagesInput messages) {
            for (ChatMessage message : messages.messages()) {
                List<Content> contents = message instanceof UserMessage userMessage
                        ? userMessage.contents()
                        : message instanceof ToolExecutionResultMessage result ? result.contents() : List.of();
                for (Content content : contents) {
                    if (!(content instanceof TextContent)) {
                        throw new UnsupportedFeatureException(
                                "TypeSafe supports only text in a conversation, but it contains " + content.type());
                    }
                }
            }
            // a conversation is sent as a transcript
            return Map.of(
                    "messages",
                    DecisionModelInputUtils.transcript(messages.messages(), "TypeSafe")
                            .messages());
        }
        throw new UnsupportedFeatureException(
                "TypeSafe does not support " + input.getClass().getName() + " as input");
    }

    /**
     * Images are sent as assets, and each one is referred to in the text by its reference, which the server replaces
     * with the image.
     */
    private static String text(List<?> contents, Map<String, Map<String, String>> assets) {
        StringBuilder text = new StringBuilder();
        for (Object content : contents) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            if (content instanceof TextContent textContent) {
                text.append(textContent.text());
            } else if (content instanceof ImageContent imageContent) {
                String reference = IMAGE_REFERENCE_PREFIX + (assets.size() + 1) + "}}";
                assets.put(reference, asset(imageContent.image()));
                text.append(reference);
            } else {
                throw new UnsupportedFeatureException("TypeSafe supports only text and image input, but the input "
                        + "contains " + ((Content) content).type());
            }
        }
        return text.toString();
    }

    private static Map<String, String> asset(Image image) {
        if (isNullOrBlank(image.base64Data())) {
            throw new UnsupportedFeatureException("TypeSafe supports only images given as base64 data, not as URLs");
        }
        String format = image.mimeType() == null
                ? null
                : IMAGE_FORMATS.get(image.mimeType().toLowerCase(Locale.ROOT));
        if (format == null) {
            throw new UnsupportedFeatureException(
                    "TypeSafe supports only PNG and JPEG images, but the input contains " + image.mimeType());
        }
        Map<String, String> asset = new LinkedHashMap<>();
        asset.put("type", "image");
        asset.put("format", format);
        asset.put("data_base64", image.base64Data());
        return asset;
    }

    private static TypeSafeQuestion toQuestion(Question question) {
        TypeSafeQuestion result = new TypeSafeQuestion();
        result.instructions = question.text();
        if (question instanceof YesNoQuestion yesNo) {
            result.type = "noul";
            if (yesNo.yesWhen() != null || yesNo.noWhen() != null) {
                // some servers (e.g. OpenRouter) require both keys once criteria are sent
                Map<String, String> criteria = new LinkedHashMap<>();
                criteria.put("true", getOrDefault(yesNo.yesWhen(), ""));
                criteria.put("false", getOrDefault(yesNo.noWhen(), ""));
                result.criteria = criteria;
            }
        } else if (question instanceof ChoiceQuestion choice) {
            result.type = "choice";
            result.criteria = choice.options();
        } else if (question instanceof ScaleQuestion scale) {
            result.type = "score";
            result.criteria = levels(scale);
        } else if (question instanceof TypeSafeBoxQuestion) {
            result.type = "box";
        } else {
            throw new UnsupportedFeatureException(
                    "TypeSafe does not support " + question.getClass().getName() + " questions");
        }
        return result;
    }

    private static List<Object> levels(ScaleQuestion scale) {
        List<Object> levels = new ArrayList<>();
        for (int i = 0; i < scale.levels().size(); i++) {
            String description = scale.levelDescriptions().get(i);
            if (description == null) {
                levels.add(scale.levels().get(i));
            } else {
                Map<String, String> level = new LinkedHashMap<>();
                level.put("label", scale.levels().get(i));
                level.put("description", description);
                levels.add(level);
            }
        }
        return levels;
    }

    public static DecisionResponse toDecisionResponse(TypeSafeResponse response, DecisionRequest request) {
        Map<String, TypeSafeAnswer> answers = getOrDefault(response.answers, Map.of());
        DecisionResponse.Builder builder = DecisionResponse.builder().modelName(response.model);
        if (response.usage != null) {
            builder.tokenUsage(new TokenUsage(response.usage.inputTokens, response.usage.outputTokens));
        }
        request.questions().forEach((name, question) -> {
            TypeSafeAnswer answer = answers.get(name);
            if (answer == null) {
                throw new InvalidDecisionResponseException(
                        "The response contains no answer to question '%s'".formatted(name));
            }
            builder.answer(name, toAnswer(name, answer, question));
        });
        return builder.build();
    }

    private static DecisionAnswer toAnswer(String name, TypeSafeAnswer answer, Question question) {
        if (question instanceof YesNoQuestion) {
            ensureType(name, answer, "noul");
            return YesNoAnswer.builder()
                    .probability(probability(name, "noul", required(name, "noul", answer.noul)))
                    .build();
        }
        if (question instanceof ChoiceQuestion) {
            ensureType(name, answer, "choice");
            String value = required(name, "choice", answer.choice);
            Map<String, Double> probabilities = new LinkedHashMap<>();
            if (answer.probabilities != null) {
                answer.probabilities.forEach((option, probability) ->
                        probabilities.put(option, probability(name, "probability of '" + option + "'", probability)));
            }
            return ChoiceAnswer.builder()
                    .value(value)
                    .probabilities(probabilities)
                    .confidence(answer.confidence == null ? null : probability(name, "confidence", answer.confidence))
                    .build();
        }
        if (question instanceof TypeSafeBoxQuestion) {
            return box(name, answer);
        }
        ScaleQuestion scale = (ScaleQuestion) question;
        ensureType(name, answer, "score");
        int levels = scale.levels().size();
        List<Double> probabilities = new ArrayList<>();
        if (answer.probabilities != null && !answer.probabilities.isEmpty()) {
            answer.probabilities.keySet().forEach(level -> {
                if (!isLevelIndex(level, levels)) {
                    throw invalid(
                            name, "has a probability for level '%s', but the levels are 0 to %s", level, levels - 1);
                }
            });
            for (int level = 0; level < levels; level++) {
                Double probability = answer.probabilities.getOrDefault(String.valueOf(level), 0.0);
                probabilities.add(probability(name, "probability of level " + level, probability));
            }
        }
        return ScaleAnswer.builder()
                .mean(level(name, required(name, "score", answer.score), levels))
                .probabilities(probabilities)
                .confidence(answer.confidence == null ? null : probability(name, "confidence", answer.confidence))
                .build();
    }

    private static TypeSafeBoxAnswer box(String name, TypeSafeAnswer answer) {
        ensureType(name, answer, "box");
        Map<String, Double> box = required(name, "box", answer.box);
        double top = probability(name, "top edge", box.get("top"));
        double bottom = probability(name, "bottom edge", box.get("bottom"));
        double left = probability(name, "left edge", box.get("left"));
        double right = probability(name, "right edge", box.get("right"));
        if (top > bottom || left > right) {
            throw invalid(name, "has an invalid box: %s", box);
        }
        return TypeSafeBoxAnswer.builder()
                .top(top)
                .bottom(bottom)
                .left(left)
                .right(right)
                .confidence(answer.confidence == null ? null : probability(name, "confidence", answer.confidence))
                .build();
    }

    private static void ensureType(String name, TypeSafeAnswer answer, String expectedType) {
        if (answer.type != null && !answer.type.equals(expectedType)) {
            throw invalid(name, "has type '%s' instead of '%s'", answer.type, expectedType);
        }
    }

    private static <T> T required(String name, String field, T value) {
        if (value == null) {
            throw invalid(name, "has no '%s'", field);
        }
        return value;
    }

    private static double probability(String name, String field, Double value) {
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
