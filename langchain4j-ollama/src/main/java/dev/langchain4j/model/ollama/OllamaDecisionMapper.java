package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.internal.DecisionModelInputUtils;
import dev.langchain4j.internal.DecisionModelInputUtils.Transcript;
import dev.langchain4j.exception.UnsupportedFeatureException;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class OllamaDecisionMapper {

    private static final double PROBABILITY_TOLERANCE = 1e-6;

    private OllamaDecisionMapper() {}

    static OllamaDecisionRequest toOllamaRequest(DecisionRequest request, Integer keepAlive) {
        if (isNullOrBlank(request.modelName())) {
            throw new IllegalArgumentException("The model name must be set, either with "
                    + "OllamaDecisionModel.builder().modelName(...) or on the request with "
                    + "DecisionRequestParameters.builder().modelName(...)");
        }
        OllamaDecisionRequest ollamaRequest = new OllamaDecisionRequest();
        ollamaRequest.setModel(request.modelName());
        List<String> images = new ArrayList<>();
        ollamaRequest.setState(state(request.input(), images));
        ollamaRequest.setImages(images.isEmpty() ? null : images);
        Map<String, OllamaDecisionRequest.Question> questions = new LinkedHashMap<>();
        request.questions().forEach((name, question) -> questions.put(name, toQuestion(question)));
        ollamaRequest.setQuestions(questions);
        ollamaRequest.setKeepAlive(keepAlive);
        if (request.parameters() instanceof OllamaDecisionRequestParameters parameters
                && !isNullOrEmpty(parameters.customParameters())) {
            Map<String, Object> customParameters = parameters.customParameters();
            ollamaRequest.setCustomParameters(customParameters);
            // a custom parameter replaces the field of the same name, which is then omitted (not sent twice)
            if (customParameters.containsKey("model")) ollamaRequest.setModel(null);
            if (customParameters.containsKey("state")) ollamaRequest.setState(null);
            if (customParameters.containsKey("images")) ollamaRequest.setImages(null);
            if (customParameters.containsKey("questions")) ollamaRequest.setQuestions(null);
            if (customParameters.containsKey("keep_alive")) ollamaRequest.setKeepAlive(null);
        }
        return ollamaRequest;
    }

    private static Object state(DecisionInput input, List<String> images) {
        if (input instanceof TextInput text) {
            return text.text();
        } else if (input instanceof FieldsInput fields) {
            Map<String, Object> state = new LinkedHashMap<>();
            fields.fields().forEach((name, value) -> {
                if (value instanceof Content content) {
                    state.put(name, text(List.of(content), images));
                } else if (isContents(value)) {
                    state.put(name, text((List<?>) value, images));
                } else {
                    state.put(name, value);
                }
            });
            return state;
        } else if (input instanceof ContentsInput contents) {
            return text(contents.contents(), images);
        } else if (input instanceof MessagesInput messages) {
            // a conversation is sent as a transcript, its images are sent like other images
            Transcript transcript = DecisionModelInputUtils.transcript(messages.messages(), "The Ollama decision model");
            transcript.images().forEach(image -> images.add(ImageUtils.base64Image(image.image())));
            return Map.of("messages", transcript.messages());
        }
        throw new UnsupportedFeatureException(
                "Ollama decision models do not support " + input.getClass().getName() + " as input");
    }

    private static boolean isContents(Object value) {
        return value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Content;
    }

    /**
     * Images are sent separately from the state, so each one is referred to in the state by its position in the
     * images, for example {@code [image 1]}.
     */
    private static String text(List<?> contents, List<String> images) {
        List<String> parts = new ArrayList<>();
        for (Object content : contents) {
            if (content instanceof TextContent textContent) {
                parts.add(textContent.text());
            } else if (content instanceof ImageContent imageContent) {
                images.add(ImageUtils.base64Image(imageContent.image()));
                parts.add("[image " + images.size() + "]");
            } else {
                throw new UnsupportedFeatureException(
                        "Ollama decision models support only text and image input, but the input contains "
                                + ((Content) content).type());
            }
        }
        return String.join("\n\n", parts);
    }

    private static OllamaDecisionRequest.Question toQuestion(Question question) {
        OllamaDecisionRequest.Question result = new OllamaDecisionRequest.Question();
        result.setInstructions(question.text());
        if (question instanceof YesNoQuestion yesNo) {
            result.setType("noul");
            if (yesNo.yesWhen() != null && yesNo.noWhen() != null) {
                Map<String, String> criteria = new LinkedHashMap<>();
                criteria.put("true", yesNo.yesWhen());
                criteria.put("false", yesNo.noWhen());
                result.setCriteria(criteria);
            } else if (yesNo.yesWhen() != null) {
                // a single criterion is replaced by a model-specific default for the other side, which can turn
                // the meaning around, so it is given in the instructions instead
                result.setInstructions(question.text() + "\nAnswer yes when: " + yesNo.yesWhen());
            } else if (yesNo.noWhen() != null) {
                result.setInstructions(question.text() + "\nAnswer no when: " + yesNo.noWhen());
            }
        } else if (question instanceof ChoiceQuestion choice) {
            result.setType("choice");
            result.setCriteria(choice.options());
        } else if (question instanceof ScaleQuestion scale) {
            result.setType("score");
            result.setCriteria(levels(scale));
        } else {
            throw new UnsupportedFeatureException(
                    "Ollama decision models do not support " + question.getClass().getName() + " questions");
        }
        return result;
    }

    private static List<String> levels(ScaleQuestion scale) {
        List<String> levels = new ArrayList<>();
        for (int i = 0; i < scale.levels().size(); i++) {
            String description = scale.levelDescriptions().get(i);
            levels.add(description == null ? scale.levels().get(i) : scale.levels().get(i) + ": " + description);
        }
        return levels;
    }

    static DecisionResponse toDecisionResponse(OllamaDecisionResponse response, DecisionRequest request) {
        Map<String, OllamaDecisionResponse.Answer> answers = getOrDefault(response.getAnswers(), Map.of());
        DecisionResponse.Builder builder = DecisionResponse.builder().modelName(response.getModel());
        if (response.getUsage() != null) {
            builder.tokenUsage(new TokenUsage(
                    response.getUsage().getInputTokens(), response.getUsage().getOutputTokens()));
        }
        request.questions().forEach((name, question) -> {
            OllamaDecisionResponse.Answer answer = answers.get(name);
            if (answer == null) {
                throw new InvalidDecisionResponseException(
                        "The response contains no answer to question '%s'".formatted(name));
            }
            builder.answer(name, toAnswer(name, answer, question));
        });
        return builder.build();
    }

    private static DecisionAnswer toAnswer(String name, OllamaDecisionResponse.Answer answer, Question question) {
        if (question instanceof YesNoQuestion) {
            ensureType(name, answer, "noul");
            return YesNoAnswer.builder()
                    .probability(probability(name, "noul", required(name, "noul", answer.getNoul())))
                    .build();
        }
        if (question instanceof ChoiceQuestion) {
            ensureType(name, answer, "choice");
            Map<String, Double> probabilities = new LinkedHashMap<>();
            if (answer.getProbabilities() != null) {
                answer.getProbabilities().forEach((option, probability) ->
                        probabilities.put(option, probability(name, "probability of '" + option + "'", probability)));
            }
            return ChoiceAnswer.builder()
                    .value(required(name, "choice", answer.getChoice()))
                    .probabilities(probabilities)
                    .confidence(confidence(name, answer.getConfidence()))
                    .build();
        }
        ScaleQuestion scale = (ScaleQuestion) question;
        ensureType(name, answer, "score");
        int levels = scale.levels().size();
        List<Double> probabilities = new ArrayList<>();
        if (answer.getProbabilities() != null && !answer.getProbabilities().isEmpty()) {
            answer.getProbabilities().keySet().forEach(level -> {
                if (!isLevelIndex(level, levels)) {
                    throw invalid(name, "has a probability for level '%s', but the levels are 0 to %s", level, levels - 1);
                }
            });
            for (int level = 0; level < levels; level++) {
                Double probability = answer.getProbabilities().getOrDefault(String.valueOf(level), 0.0);
                probabilities.add(probability(name, "probability of level " + level, probability));
            }
        }
        return ScaleAnswer.builder()
                .mean(level(name, required(name, "score", answer.getScore()), levels))
                .probabilities(probabilities)
                .confidence(confidence(name, answer.getConfidence()))
                .build();
    }

    private static void ensureType(String name, OllamaDecisionResponse.Answer answer, String expectedType) {
        if (answer.getType() != null && !answer.getType().equals(expectedType)) {
            throw invalid(name, "has type '%s' instead of '%s'", answer.getType(), expectedType);
        }
    }

    private static <T> T required(String name, String field, T value) {
        if (value == null) {
            throw invalid(name, "has no '%s'", field);
        }
        return value;
    }

    private static Double confidence(String name, Double confidence) {
        return confidence == null ? null : probability(name, "confidence", confidence);
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
