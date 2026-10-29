package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import com.openai.core.JsonField;
import com.openai.core.JsonValue;
import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionChoiceOption;
import com.openai.models.decisions.DecisionChoiceValue;
import com.openai.models.decisions.DecisionCreateParams;
import com.openai.models.decisions.DecisionInputImage;
import com.openai.models.decisions.DecisionInputMessage;
import com.openai.models.decisions.DecisionInputPart;
import com.openai.models.decisions.DecisionInputText;
import com.openai.errors.OpenAIInvalidDataException;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.DecisionModelInputUtils;
import dev.langchain4j.internal.DecisionModelInputUtils.Transcript;
import dev.langchain4j.internal.Json;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts between the {@link dev.langchain4j.model.decision.DecisionModel} API and the OpenAI Decisions API of the
 * OpenAI Java SDK.
 */
class InternalOpenAiOfficialDecisionHelper {

    private static final double PROBABILITY_TOLERANCE = 1e-6;

    private InternalOpenAiOfficialDecisionHelper() {}

    static DecisionCreateParams toDecisionCreateParams(DecisionRequest request) {
        if (isNullOrBlank(request.modelName())) {
            throw new IllegalArgumentException("The model name must be set, either on the decision model builder "
                    + "with modelName(...) or on the request with DecisionRequestParameters.builder().modelName(...)");
        }
        DecisionCreateParams.Builder params = DecisionCreateParams.builder().model(request.modelName());
        addInput(params, request.input());
        request.questions().forEach((name, question) -> addQuestion(params, name, question));
        if (request.parameters() instanceof OpenAiOfficialDecisionRequestParameters parameters) {
            if (parameters.promptCacheKey() != null) {
                // the SDK has no typed setter for it yet
                params.putAdditionalBodyProperty("prompt_cache_key", JsonValue.from(parameters.promptCacheKey()));
            }
            parameters.customParameters().forEach((name, value) -> addCustomParameter(params, name, value));
        }
        return params.build();
    }

    private static void addInput(DecisionCreateParams.Builder params, DecisionInput input) {
        if (input instanceof TextInput text) {
            params.input(text.text());
        } else if (input instanceof FieldsInput fields) {
            params.inputOfDecisionInputMessages(List.of(userMessage(toLabeledParts(fields.fields()))));
        } else if (input instanceof ContentsInput contents) {
            params.inputOfDecisionInputMessages(List.of(userMessage(toInputParts(contents.contents()))));
        } else if (input instanceof MessagesInput messages) {
            params.inputOfDecisionInputMessages(List.of(userMessage(toTranscriptParts(messages.messages()))));
        } else {
            throw new UnsupportedFeatureException(
                    "The OpenAI Decisions API does not support " + input.getClass().getName() + " as input");
        }
    }

    private static DecisionInputMessage userMessage(List<DecisionInputPart> parts) {
        return DecisionInputMessage.builder().contentOfParts(parts).build();
    }

    /**
     * Each field is labeled with its name, contents such as images follow their label.
     */
    private static List<DecisionInputPart> toLabeledParts(Map<String, Object> fields) {
        List<DecisionInputPart> parts = new ArrayList<>();
        fields.forEach((name, value) -> {
            if (value instanceof Content content) {
                parts.add(textPart(name + ":"));
                parts.add(toInputPart(content));
            } else if (isContents(value)) {
                parts.add(textPart(name + ":"));
                ((List<?>) value).forEach(content -> parts.add(toInputPart((Content) content)));
            } else {
                parts.add(textPart(name + ": " + Json.toJson(value)));
            }
        });
        return parts;
    }

    private static List<DecisionInputPart> toInputParts(List<Content> contents) {
        List<DecisionInputPart> parts = new ArrayList<>();
        contents.forEach(content -> parts.add(toInputPart(content)));
        return parts;
    }

    /**
     * The SDK writes additional body properties next to the fields it knows, so the fields set by this class are
     * replaced through their own setters to avoid sending them twice.
     */
    private static void addCustomParameter(DecisionCreateParams.Builder params, String name, Object value) {
        switch (name) {
            case "model" -> params.model(jsonField(value));
            case "input" -> params.input(jsonField(value));
            case "questions" -> params.questions(jsonField(value));
            default -> params.putAdditionalBodyProperty(name, JsonValue.from(value));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> JsonField<T> jsonField(Object value) {
        return (JsonField<T>) (JsonField<?>) JsonValue.from(value);
    }

    /**
     * The conversation is sent as a JSON transcript, followed by the images it refers to.
     */
    private static List<DecisionInputPart> toTranscriptParts(List<ChatMessage> messages) {
        Transcript transcript = DecisionModelInputUtils.transcript(messages, "The OpenAI Decisions API");
        List<DecisionInputPart> parts = new ArrayList<>();
        parts.add(textPart("messages: " + Json.toJson(transcript.messages())));
        for (int i = 0; i < transcript.images().size(); i++) {
            parts.add(textPart("[image " + (i + 1) + "]:"));
            parts.add(toInputPart(transcript.images().get(i)));
        }
        return parts;
    }


    private static boolean isContents(Object value) {
        return value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Content;
    }

    private static DecisionInputPart textPart(String text) {
        return DecisionInputPart.ofInputText(DecisionInputText.builder().text(text).build());
    }

    private static DecisionInputPart toInputPart(Content content) {
        if (content instanceof TextContent text) {
            return DecisionInputPart.ofInputText(
                    DecisionInputText.builder().text(text.text()).build());
        }
        if (content instanceof ImageContent image) {
            return DecisionInputPart.ofInputImage(DecisionInputImage.builder()
                    .imageUrl(toImageUrl(image.image()))
                    .detail(toDetail(image.detailLevel()))
                    .build());
        }
        throw new UnsupportedFeatureException(
                "The OpenAI Decisions API supports only text and image input, but the input contains "
                        + content.type());
    }

    private static String toImageUrl(Image image) {
        if (image.base64Data() != null) {
            if (isNullOrBlank(image.mimeType())) {
                throw new IllegalArgumentException("The MIME type of an image in base64 must be set");
            }
            return "data:" + image.mimeType() + ";base64," + image.base64Data();
        }
        return image.url().toString();
    }

    private static DecisionInputImage.Detail toDetail(ImageContent.DetailLevel detailLevel) {
        return switch (detailLevel) {
            case LOW -> DecisionInputImage.Detail.LOW;
            case HIGH -> DecisionInputImage.Detail.HIGH;
            case AUTO -> DecisionInputImage.Detail.AUTO;
            case MEDIUM, ULTRA_HIGH -> throw new UnsupportedFeatureException("DetailLevel " + detailLevel
                    + " is not supported by the OpenAI Decisions API. Supported values: LOW, HIGH, AUTO");
        };
    }

    private static void addQuestion(DecisionCreateParams.Builder params, String name, Question question) {
        if (question instanceof YesNoQuestion yesNo) {
            params.addQuestion(DecisionCreateParams.Question.Predicate.builder()
                    .name(name)
                    .instructions(instructions(yesNo))
                    .build());
        } else if (question instanceof ChoiceQuestion choice) {
            List<DecisionChoiceOption> options = new ArrayList<>();
            choice.options()
                    .forEach((value, description) -> options.add(DecisionChoiceOption.builder()
                            .value(value)
                            .description(description)
                            .build()));
            params.addQuestion(DecisionCreateParams.Question.Choice.builder()
                    .name(name)
                    .instructions(choice.text())
                    .choices(options)
                    .build());
        } else if (question instanceof ScaleQuestion scale) {
            List<DecisionCreateParams.Question.Score.Level> levels = new ArrayList<>();
            for (int i = 0; i < scale.levels().size(); i++) {
                DecisionCreateParams.Question.Score.Level.Builder level =
                        DecisionCreateParams.Question.Score.Level.builder().label(scale.levels().get(i));
                if (scale.levelDescriptions().get(i) != null) {
                    level.description(scale.levelDescriptions().get(i));
                }
                levels.add(level.build());
            }
            params.addQuestion(DecisionCreateParams.Question.Score.builder()
                    .name(name)
                    .instructions(scale.text())
                    .levels(levels)
                    .build());
        } else {
            throw new UnsupportedFeatureException(
                    "The OpenAI Decisions API does not support " + question.getClass().getName() + " questions");
        }
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

    static DecisionResponse toDecisionResponse(Decision decision, DecisionRequest request) {
        try {
            return mapDecisionResponse(decision, request);
        } catch (OpenAIInvalidDataException e) {
            // the SDK checks the fields of the response only when they are read
            throw new InvalidDecisionResponseException("The response of the OpenAI Decisions API is invalid: "
                    + e.getMessage());
        }
    }

    private static DecisionResponse mapDecisionResponse(Decision decision, DecisionRequest request) {
        List<String> questionNames = new ArrayList<>(request.questions().keySet());
        DecisionResponse.Builder builder = DecisionResponse.builder().modelName(decision.model());
        List<Decision.Answer> answers = decision.answers();
        for (int i = 0; i < answers.size(); i++) {
            Decision.Answer answer = answers.get(i);
            // answers are returned in question order, and named after their question
            String name = name(answer).orElse(i < questionNames.size() ? questionNames.get(i) : null);
            if (name == null) {
                throw new InvalidDecisionResponseException("The response contains more answers than questions");
            }
            builder.answer(name, toAnswer(name, answer, request.questions().get(name)));
        }
        decision._usage().asKnown().ifPresent(usage -> builder.tokenUsage(toTokenUsage(usage)));
        return builder.build();
    }

    private static Optional<String> name(Decision.Answer answer) {
        if (answer.isPredicate()) {
            return answer.asPredicate().name();
        }
        if (answer.isChoice()) {
            return answer.asChoice().name();
        }
        if (answer.isScore()) {
            return answer.asScore().name();
        }
        if (answer.isRefusal()) {
            return answer.asRefusal().name();
        }
        return Optional.empty();
    }

    private static DecisionAnswer toAnswer(String name, Decision.Answer answer, Question question) {
        if (answer.isPredicate()) {
            return YesNoAnswer.from(probability(name, answer.asPredicate().probability(), "probability"));
        }
        if (answer.isChoice()) {
            Decision.Answer.Choice choice = answer.asChoice();
            ChoiceAnswer.Builder builder = ChoiceAnswer.builder()
                    .value(toString(choice.choice()))
                    .confidence(probability(name, choice.confidence(), "confidence"));
            choice.probabilities()
                    .forEach(probability -> builder.probability(
                            toString(probability.value()), probability(name, probability.probability(), "probability")));
            return builder.build();
        }
        if (answer.isScore()) {
            Decision.Answer.Score score = answer.asScore();
            ScaleAnswer.Builder builder = ScaleAnswer.builder()
                    .mean(question instanceof ScaleQuestion scale
                            ? level(name, score.score(), scale.levels().size())
                            : score.score())
                    .confidence(probability(name, score.confidence(), "confidence"));
            if (!score.probabilities().isEmpty() && question instanceof ScaleQuestion scale) {
                // the value of a level probability is the index of the level, starting at 0
                Double[] probabilities = new Double[scale.levels().size()];
                Arrays.fill(probabilities, 0.0);
                for (Decision.Answer.Score.Probability probability : score.probabilities()) {
                    long index = probability.value();
                    if (index < 0 || index >= probabilities.length) {
                        throw new InvalidDecisionResponseException(("The answer to question '%s' has a probability "
                                        + "for the level %s, but the levels are 0 to %s")
                                .formatted(name, index, probabilities.length - 1));
                    }
                    probabilities[(int) index] = probability(name, probability.probability(), "probability");
                }
                builder.probabilities(Arrays.asList(probabilities));
            }
            return builder.build();
        }
        if (answer.isRefusal()) {
            return RefusalAnswer.of();
        }
        throw new InvalidDecisionResponseException(
                "The answer to question '%s' has an unknown type".formatted(name));
    }

    private static double probability(String name, double value, String field) {
        if (Double.isNaN(value) || value < -PROBABILITY_TOLERANCE || value > 1 + PROBABILITY_TOLERANCE) {
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

    private static String toString(DecisionChoiceValue value) {
        return value.isString() ? value.asString() : String.valueOf(value.asBool());
    }

    private static OpenAiOfficialTokenUsage toTokenUsage(Decision.Usage usage) {
        return OpenAiOfficialTokenUsage.builder()
                .inputTokenCount(usage.inputTokens())
                .outputTokenCount(usage.outputTokens())
                .totalTokenCount(usage.totalTokens())
                .inputTokensDetails(OpenAiOfficialTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(usage.inputTokensDetails().cachedTokens())
                        .cacheWriteTokens(usage.inputTokensDetails().cacheWriteTokens())
                        .build())
                .outputTokensDetails(OpenAiOfficialTokenUsage.OutputTokensDetails.builder()
                        .reasoningTokens(usage.outputTokensDetails().reasoningTokens())
                        .build())
                .build();
    }
}
