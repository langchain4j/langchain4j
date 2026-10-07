package dev.langchain4j.model.openai.internal.decision;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.Internal;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Converts between the {@link dev.langchain4j.model.decision.DecisionModel} API and the OpenAI Decisions API.
 */
@Internal
public final class DecisionMapper {

    private static final double PROBABILITY_TOLERANCE = 1e-6;

    private DecisionMapper() {}

    public static DecisionCreateRequest toOpenAiRequest(DecisionRequest request) {
        if (isNullOrBlank(request.modelName())) {
            throw new IllegalArgumentException("The model name must be set, either on the decision model builder "
                    + "with modelName(...) or on the request with DecisionRequestParameters.builder().modelName(...)");
        }
        DecisionCreateRequest openAiRequest = new DecisionCreateRequest();
        openAiRequest.model = request.modelName();
        openAiRequest.input = toInput(request.input());
        openAiRequest.questions = new ArrayList<>();
        request.questions().forEach((name, question) -> openAiRequest.questions.add(toQuestion(name, question)));
        return openAiRequest;
    }

    private static Object toInput(Object input) {
        if (input instanceof String text) {
            return text;
        }
        DecisionCreateRequest.InputMessage message = new DecisionCreateRequest.InputMessage();
        message.content = new ArrayList<>();
        if (input instanceof Map<?, ?> map) {
            if (!containsContents(map)) {
                return Json.toJson(map);
            }
            // each value is labeled with its name, contents such as images follow their label
            map.forEach((name, value) -> {
                if (value instanceof Content content) {
                    message.content.add(textPart(name + ":"));
                    message.content.add(toInputPart(content));
                } else if (isContents(value)) {
                    message.content.add(textPart(name + ":"));
                    ((List<?>) value).forEach(content -> message.content.add(toInputPart((Content) content)));
                } else {
                    message.content.add(textPart(name + ": " + Json.toJson(value)));
                }
            });
            return List.of(message);
        }
        for (Object content : (List<?>) input) {
            message.content.add(toInputPart((Content) content));
        }
        return List.of(message);
    }

    private static boolean containsContents(Map<?, ?> map) {
        return map.values().stream().anyMatch(value -> value instanceof Content || isContents(value));
    }

    private static boolean isContents(Object value) {
        return value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Content;
    }

    private static DecisionCreateRequest.InputPart textPart(String text) {
        DecisionCreateRequest.InputPart part = new DecisionCreateRequest.InputPart();
        part.type = "input_text";
        part.text = text;
        return part;
    }

    private static DecisionCreateRequest.InputPart toInputPart(Content content) {
        DecisionCreateRequest.InputPart part = new DecisionCreateRequest.InputPart();
        if (content instanceof TextContent text) {
            part.type = "input_text";
            part.text = text.text();
        } else if (content instanceof ImageContent image) {
            part.type = "input_image";
            part.imageUrl = toDataUrl(image.image());
            part.detail = toDetail(image.detailLevel());
        } else {
            throw new UnsupportedFeatureException(
                    "The OpenAI Decisions API supports only text and image input, but the input contains "
                            + content.type());
        }
        return part;
    }

    private static String toDataUrl(Image image) {
        if (image.base64Data() != null) {
            if (isNullOrBlank(image.mimeType())) {
                throw new IllegalArgumentException("The MIME type of an image in base64 must be set");
            }
            return "data:" + image.mimeType() + ";base64," + image.base64Data();
        }
        if (image.url() != null && "data".equals(image.url().getScheme())) {
            return image.url().toString();
        }
        throw new UnsupportedFeatureException("The OpenAI Decisions API supports only inline images (base64 data or "
                + "data URLs), not images referenced by URL");
    }

    private static String toDetail(ImageContent.DetailLevel detailLevel) {
        return switch (detailLevel) {
            case LOW -> "low";
            case HIGH -> "high";
            case AUTO -> "auto";
            case MEDIUM, ULTRA_HIGH -> throw new UnsupportedFeatureException("DetailLevel " + detailLevel
                    + " is not supported by the OpenAI Decisions API. Supported values: LOW, HIGH, AUTO");
        };
    }

    private static DecisionCreateRequest.Question toQuestion(String name, Question question) {
        DecisionCreateRequest.Question result = new DecisionCreateRequest.Question();
        result.name = name;
        if (question instanceof YesNoQuestion yesNo) {
            result.type = "predicate";
            result.instructions = instructions(yesNo);
        } else if (question instanceof ChoiceQuestion choice) {
            result.type = "choice";
            result.instructions = choice.text();
            result.choices = new ArrayList<>();
            choice.options().forEach((value, description) -> {
                DecisionCreateRequest.Choice option = new DecisionCreateRequest.Choice();
                option.value = value;
                option.description = description;
                result.choices.add(option);
            });
        } else if (question instanceof ScaleQuestion scale) {
            result.type = "score";
            result.instructions = scale.text();
            result.levels = new ArrayList<>();
            for (int i = 0; i < scale.levels().size(); i++) {
                DecisionCreateRequest.Level level = new DecisionCreateRequest.Level();
                level.label = scale.levels().get(i);
                level.description = scale.levelDescriptions().get(i);
                result.levels.add(level);
            }
        } else {
            throw new UnsupportedFeatureException(
                    "The OpenAI Decisions API does not support " + question.getClass().getName() + " questions");
        }
        return result;
    }

    private static String instructions(YesNoQuestion question) {
        StringBuilder instructions = new StringBuilder(question.text());
        if (question.yesWhen() != null) {
            instructions.append("\nAnswer yes when: ").append(question.yesWhen());
        }
        if (question.noWhen() != null) {
            instructions.append("\nAnswer no when: ").append(question.noWhen());
        }
        return instructions.toString();
    }

    public static DecisionResponse toDecisionResponse(DecisionCreateResponse response, DecisionRequest request) {
        List<String> questionNames = new ArrayList<>(request.questions().keySet());
        List<DecisionCreateResponse.Answer> answers = response.answers == null ? List.of() : response.answers;
        DecisionResponse.Builder builder = DecisionResponse.builder().modelName(response.model);
        for (int i = 0; i < answers.size(); i++) {
            DecisionCreateResponse.Answer answer = answers.get(i);
            // answers are returned in question order, and named after their question
            String name = answer.name != null ? answer.name : i < questionNames.size() ? questionNames.get(i) : null;
            if (name == null) {
                throw new InvalidDecisionResponseException("The response contains more answers than questions");
            }
            builder.answer(name, toAnswer(name, answer, request.questions().get(name)));
        }
        if (response.usage != null) {
            builder.tokenUsage(toTokenUsage(response.usage));
        }
        return builder.build();
    }

    private static DecisionAnswer toAnswer(String name, DecisionCreateResponse.Answer answer, Question question) {
        if (answer.type == null) {
            throw new InvalidDecisionResponseException("The answer to question '%s' has no type".formatted(name));
        }
        return switch (answer.type) {
            case "predicate" -> YesNoAnswer.of(probability(name, answer.probability, "probability"));
            case "choice" -> toChoiceAnswer(name, answer);
            case "score" -> toScaleAnswer(name, answer, question);
            case "refusal" -> RefusalAnswer.of();
            default ->
                throw new InvalidDecisionResponseException(
                        "The answer to question '%s' has the unknown type '%s'".formatted(name, answer.type));
        };
    }

    private static ChoiceAnswer toChoiceAnswer(String name, DecisionCreateResponse.Answer answer) {
        ChoiceAnswer.Builder builder = ChoiceAnswer.builder()
                .value(String.valueOf(required(name, answer.choice, "choice")))
                .confidence(confidence(name, answer.confidence));
        if (answer.probabilities != null) {
            answer.probabilities.forEach(probability -> builder.probability(
                    String.valueOf(probability.value), probability(name, probability.probability, "probability")));
        }
        return builder.build();
    }

    private static ScaleAnswer toScaleAnswer(String name, DecisionCreateResponse.Answer answer, Question question) {
        double score = required(name, answer.score, "score");
        ScaleAnswer.Builder builder = ScaleAnswer.builder()
                .mean(question instanceof ScaleQuestion scale ? level(name, score, scale.levels().size()) : score)
                .confidence(confidence(name, answer.confidence));
        if (answer.probabilities != null
                && !answer.probabilities.isEmpty()
                && question instanceof ScaleQuestion scale) {
            // the value of a level probability is the index of the level, starting at 0
            Double[] probabilities = new Double[scale.levels().size()];
            Arrays.fill(probabilities, 0.0);
            for (DecisionCreateResponse.Probability probability : answer.probabilities) {
                int index = probability.value instanceof Integer || probability.value instanceof Long
                        ? ((Number) probability.value).intValue()
                        : -1;
                if (index < 0 || index >= probabilities.length) {
                    throw new InvalidDecisionResponseException(
                            "The answer to question '%s' has a probability for the level %s, but the levels are 0 to %s"
                                    .formatted(name, probability.value, probabilities.length - 1));
                }
                probabilities[index] = probability(name, probability.probability, "probability");
            }
            builder.probabilities(Arrays.asList(probabilities));
        }
        return builder.build();
    }

    private static Double confidence(String name, Double confidence) {
        return confidence == null ? null : probability(name, confidence, "confidence");
    }

    private static double probability(String name, Double value, String field) {
        if (value == null || value.isNaN() || value < -PROBABILITY_TOLERANCE || value > 1 + PROBABILITY_TOLERANCE) {
            throw new InvalidDecisionResponseException(
                    "The answer to question '%s' has an invalid %s: %s".formatted(name, field, value));
        }
        return Math.min(1.0, Math.max(0.0, value)); // absorbs rounding errors such as 1.0000000002
    }

    private static double level(String name, double value, int levels) {
        if (Double.isNaN(value) || value < -PROBABILITY_TOLERANCE || value > levels - 1 + PROBABILITY_TOLERANCE) {
            throw new InvalidDecisionResponseException("The answer to question '%s' has an invalid score: %s, but the levels are 0 to %s"
                    .formatted(name, value, levels - 1));
        }
        return Math.min(levels - 1, Math.max(0.0, value));
    }

    private static <T> T required(String name, T value, String field) {
        if (value == null) {
            throw new InvalidDecisionResponseException(
                    "The answer to question '%s' has no %s".formatted(name, field));
        }
        return value;
    }

    private static OpenAiTokenUsage toTokenUsage(DecisionCreateResponse.Usage usage) {
        OpenAiTokenUsage.Builder builder = OpenAiTokenUsage.builder()
                .inputTokenCount(usage.inputTokens)
                .outputTokenCount(usage.outputTokens)
                .totalTokenCount(usage.totalTokens);
        if (usage.inputTokensDetails != null) {
            builder.inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                    .cachedTokens(usage.inputTokensDetails.cachedTokens)
                    .cacheWriteTokens(usage.inputTokensDetails.cacheWriteTokens)
                    .build());
        }
        if (usage.outputTokensDetails != null) {
            builder.outputTokensDetails(OpenAiTokenUsage.OutputTokensDetails.builder()
                    .reasoningTokens(usage.outputTokensDetails.reasoningTokens)
                    .build());
        }
        return builder.build();
    }
}
