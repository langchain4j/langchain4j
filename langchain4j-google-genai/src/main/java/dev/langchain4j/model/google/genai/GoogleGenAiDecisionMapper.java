package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import com.google.genai.gaos.models.decisions.Answer;
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
import com.google.genai.gaos.models.interactions.AudioContentMimeType;
import com.google.genai.gaos.models.interactions.DocumentContent;
import com.google.genai.gaos.models.interactions.DocumentContentMimeType;
import com.google.genai.gaos.models.interactions.ImageContentMimeType;
import com.google.genai.gaos.models.interactions.MediaResolution;
import com.google.genai.gaos.models.interactions.Usage;
import com.google.genai.gaos.models.interactions.VideoContentMimeType;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class GoogleGenAiDecisionMapper {

    private static final String PROVIDER = "GoogleGenAiDecisionModel";
    private static final double PROBABILITY_TOLERANCE = 1e-6;

    private GoogleGenAiDecisionMapper() {}

    static CreateDecisionRequest toCreateDecisionRequest(DecisionRequest request) {
        if (isNullOrBlank(request.modelName())) {
            throw new IllegalArgumentException("The model name must be set, either on the decision model builder "
                    + "with modelName(...) or on the request with DecisionRequestParameters.builder().modelName(...)");
        }
        Map<String, com.google.genai.gaos.models.decisions.Question> questions = new LinkedHashMap<>();
        request.questions().forEach((name, question) -> questions.put(name, toQuestion(question)));
        return CreateDecisionRequest.builder()
                .model(request.modelName())
                .input(toInput(request.input()))
                .questions(questions)
                .build();
    }

    private static Input toInput(DecisionInput input) {
        if (input instanceof TextInput text) {
            return Input.of(text.text());
        } else if (input instanceof FieldsInput fields) {
            return Input.of(toLabeledContents(fields.fields()));
        } else if (input instanceof ContentsInput contents) {
            return Input.of(toInputContents(contents.contents()));
        } else if (input instanceof MessagesInput messages) {
            return Input.of(toTranscriptContents(messages.messages()));
        }
        throw new UnsupportedFeatureException(
                PROVIDER + " does not support " + input.getClass().getName() + " as input");
    }

    private static List<DecisionInputContent> toLabeledContents(Map<String, Object> fields) {
        List<DecisionInputContent> contents = new ArrayList<>();
        fields.forEach((name, value) -> {
            if (value instanceof Content content) {
                contents.add(text(name + ":"));
                contents.add(toInputContent(content));
            } else if (isContents(value)) {
                contents.add(text(name + ":"));
                ((List<?>) value).forEach(content -> contents.add(toInputContent((Content) content)));
            } else {
                contents.add(text(name + ": " + Json.toJson(value)));
            }
        });
        return contents;
    }

    private static List<DecisionInputContent> toInputContents(List<Content> contents) {
        List<DecisionInputContent> inputContents = new ArrayList<>();
        contents.forEach(content -> inputContents.add(toInputContent(content)));
        return inputContents;
    }

    private static List<DecisionInputContent> toTranscriptContents(List<ChatMessage> messages) {
        Transcript transcript = DecisionModelInputUtils.transcript(messages, PROVIDER);
        List<DecisionInputContent> contents = new ArrayList<>();
        contents.add(text("messages: " + Json.toJson(transcript.messages())));
        for (int i = 0; i < transcript.images().size(); i++) {
            contents.add(text("[image " + (i + 1) + "]:"));
            contents.add(toInputContent(transcript.images().get(i)));
        }
        return contents;
    }

    private static boolean isContents(Object value) {
        return value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Content;
    }

    private static DecisionInputContent text(String text) {
        return com.google.genai.gaos.models.interactions.TextContent.builder()
                .text(text)
                .build();
    }

    private static DecisionInputContent toInputContent(Content content) {
        if (content instanceof TextContent text) {
            return text(text.text());
        } else if (content instanceof ImageContent image) {
            return toImage(image);
        } else if (content instanceof PdfFileContent pdf) {
            return toDocument(pdf.pdfFile());
        } else if (content instanceof AudioContent audio) {
            return toAudio(audio.audio());
        } else if (content instanceof VideoContent video) {
            return toVideo(video.video());
        }
        throw new UnsupportedFeatureException(PROVIDER + " supports text, image, PDF, audio and video input, but the "
                + "input contains " + content.getClass().getName());
    }

    private static DecisionInputContent toImage(ImageContent content) {
        Image image = content.image();
        var builder = com.google.genai.gaos.models.interactions.ImageContent.builder();
        if (image.base64Data() != null) {
            builder.data(image.base64Data()).mimeType(ImageContentMimeType.of(mimeType(image.mimeType(), "image")));
        } else {
            builder.uri(image.url().toString());
            if (image.mimeType() != null) {
                builder.mimeType(ImageContentMimeType.of(image.mimeType()));
            }
        }
        MediaResolution resolution = toResolution(content.detailLevel());
        if (resolution != null) {
            builder.resolution(resolution);
        }
        return builder.build();
    }

    private static MediaResolution toResolution(ImageContent.DetailLevel detailLevel) {
        return switch (detailLevel) {
            case LOW -> MediaResolution.LOW;
            case MEDIUM -> MediaResolution.MEDIUM;
            case HIGH -> MediaResolution.HIGH;
            case ULTRA_HIGH -> MediaResolution.ULTRA_HIGH;
            case AUTO -> null;
        };
    }

    private static DecisionInputContent toDocument(PdfFile pdf) {
        var builder = DocumentContent.builder();
        if (pdf.base64Data() != null) {
            builder.data(pdf.base64Data()).mimeType(DocumentContentMimeType.of(getOrPdf(pdf.mimeType())));
        } else {
            builder.uri(pdf.url().toString()).mimeType(DocumentContentMimeType.of(getOrPdf(pdf.mimeType())));
        }
        return builder.build();
    }

    private static String getOrPdf(String mimeType) {
        return isNullOrBlank(mimeType) ? "application/pdf" : mimeType;
    }

    private static DecisionInputContent toAudio(Audio audio) {
        var builder = com.google.genai.gaos.models.interactions.AudioContent.builder();
        if (audio.base64Data() != null) {
            builder.data(audio.base64Data()).mimeType(AudioContentMimeType.of(mimeType(audio.mimeType(), "audio")));
        } else if (audio.binaryData() != null) {
            builder.data(Base64.getEncoder().encodeToString(audio.binaryData()))
                    .mimeType(AudioContentMimeType.of(mimeType(audio.mimeType(), "audio")));
        } else {
            builder.uri(audio.url().toString());
            if (audio.mimeType() != null) {
                builder.mimeType(AudioContentMimeType.of(audio.mimeType()));
            }
        }
        return builder.build();
    }

    private static DecisionInputContent toVideo(Video video) {
        var builder = com.google.genai.gaos.models.interactions.VideoContent.builder();
        if (video.base64Data() != null) {
            builder.data(video.base64Data()).mimeType(VideoContentMimeType.of(mimeType(video.mimeType(), "video")));
        } else {
            builder.uri(video.url().toString());
            if (video.mimeType() != null) {
                builder.mimeType(VideoContentMimeType.of(video.mimeType()));
            }
        }
        return builder.build();
    }

    private static String mimeType(String mimeType, String kind) {
        if (isNullOrBlank(mimeType)) {
            throw new IllegalArgumentException("The MIME type of an " + kind + " in base64 must be set");
        }
        return mimeType;
    }

    private static com.google.genai.gaos.models.decisions.Question toQuestion(Question question) {
        if (question instanceof YesNoQuestion yesNo) {
            var builder = NoulQuestion.builder().instructions(NoulQuestionInstructions.of(yesNo.text()));
            if (yesNo.yesWhen() != null || yesNo.noWhen() != null) {
                var criteria = NoulCriteria.builder();
                if (yesNo.yesWhen() != null) {
                    criteria.trueDescription(yesNo.yesWhen());
                }
                if (yesNo.noWhen() != null) {
                    criteria.falseDescription(yesNo.noWhen());
                }
                builder.criteria(criteria.build());
            }
            return builder.build();
        } else if (question instanceof ChoiceQuestion choice) {
            return com.google.genai.gaos.models.decisions.ChoiceQuestion.builder()
                    .instructions(com.google.genai.gaos.models.decisions.ChoiceQuestionInstructions.of(choice.text()))
                    .criteria(choice.options())
                    .build();
        } else if (question instanceof ScaleQuestion scale) {
            List<String> criteria = new ArrayList<>();
            for (int i = 0; i < scale.levels().size(); i++) {
                String description = scale.levelDescriptions().get(i);
                criteria.add(
                        description == null
                                ? scale.levels().get(i)
                                : scale.levels().get(i) + ": " + description);
            }
            return ScoreQuestion.builder()
                    .instructions(ScoreQuestionInstructions.of(scale.text()))
                    .criteria(criteria)
                    .build();
        }
        throw new UnsupportedFeatureException(
                PROVIDER + " does not support " + question.getClass().getName() + " questions");
    }

    static DecisionResponse toDecisionResponse(CreateDecisionResponse response, DecisionRequest request) {
        DecisionResponse.Builder builder = DecisionResponse.builder().modelName(request.modelName());
        if (isBlocked(response)) {
            request.questions().keySet().forEach(name -> builder.answer(name, RefusalAnswer.of()));
        } else {
            response.answers()
                    .ifPresent(answers -> answers.forEach((name, answer) -> {
                        if (!request.questions().containsKey(name)) {
                            throw new InvalidDecisionResponseException(
                                    "The response contains an answer to the unknown question '%s'".formatted(name));
                        }
                        builder.answer(
                                name, toAnswer(name, answer, request.questions().get(name)));
                    }));
        }
        response.usage().ifPresent(usage -> builder.tokenUsage(toTokenUsage(usage)));
        return builder.build();
    }

    private static boolean isBlocked(CreateDecisionResponse response) {
        return response.promptFeedback().flatMap(PromptFeedback::blockReason).isPresent();
    }

    private static DecisionAnswer toAnswer(String name, Answer answer, Question question) {
        if (answer instanceof NoulAnswer noul) {
            return YesNoAnswer.from(probability(name, required(name, noul.noul(), "noul"), "noul"));
        } else if (answer instanceof com.google.genai.gaos.models.decisions.ChoiceAnswer choice) {
            ChoiceAnswer.Builder builder = ChoiceAnswer.builder()
                    .value(required(name, choice.choice(), "choice"))
                    .confidence(probability(name, required(name, choice.confidence(), "confidence"), "confidence"));
            choice.probabilities()
                    .ifPresent(probabilities -> probabilities.forEach((option, probability) ->
                            builder.probability(option, probability(name, probability, "probability"))));
            return builder.build();
        } else if (answer instanceof ScoreAnswer score) {
            int levels =
                    question instanceof ScaleQuestion scale ? scale.levels().size() : 0;
            ScaleAnswer.Builder builder = ScaleAnswer.builder()
                    .mean(level(name, required(name, score.score(), "score"), levels))
                    .confidence(probability(name, required(name, score.confidence(), "confidence"), "confidence"));
            if (score.probabilities().isPresent() && levels > 0) {
                builder.probabilities(
                        toLevelProbabilities(name, score.probabilities().get(), levels));
            }
            return builder.build();
        }
        throw new InvalidDecisionResponseException(
                "The answer to question '%s' has an unknown type: %s".formatted(name, answer.type()));
    }

    private static List<Double> toLevelProbabilities(String name, Map<String, Double> probabilities, int levels) {
        Double[] byLevel = new Double[levels];
        Arrays.fill(byLevel, 0.0);
        probabilities.forEach((level, probability) -> {
            int index = index(name, level, levels);
            byLevel[index] = probability(name, probability, "probability");
        });
        return Arrays.asList(byLevel);
    }

    private static int index(String name, String level, int levels) {
        int index;
        try {
            index = Integer.parseInt(level);
        } catch (NumberFormatException e) {
            throw new InvalidDecisionResponseException(
                    "The answer to question '%s' has a probability for the unknown level '%s'".formatted(name, level));
        }
        if (index < 0 || index >= levels) {
            throw new InvalidDecisionResponseException(
                    "The answer to question '%s' has a probability for the level %s, but the levels are 0 to %s"
                            .formatted(name, index, levels - 1));
        }
        return index;
    }

    private static <T> T required(String name, Optional<T> value, String field) {
        return value.orElseThrow(() ->
                new InvalidDecisionResponseException("The answer to question '%s' has no %s".formatted(name, field)));
    }

    private static double probability(String name, double value, String field) {
        if (Double.isNaN(value) || value < -PROBABILITY_TOLERANCE || value > 1 + PROBABILITY_TOLERANCE) {
            throw new InvalidDecisionResponseException(
                    "The answer to question '%s' has an invalid %s: %s".formatted(name, field, value));
        }
        return Math.min(1.0, Math.max(0.0, value));
    }

    private static double level(String name, double value, int levels) {
        if (levels == 0) {
            return value;
        }
        if (Double.isNaN(value) || value < -PROBABILITY_TOLERANCE || value > levels - 1 + PROBABILITY_TOLERANCE) {
            throw new InvalidDecisionResponseException(
                    "The answer to question '%s' has an invalid score: %s, but the levels are 0 to %s"
                            .formatted(name, value, levels - 1));
        }
        return Math.min(levels - 1, Math.max(0.0, value));
    }

    private static GoogleGenAiTokenUsage toTokenUsage(Usage usage) {
        return GoogleGenAiTokenUsage.builder()
                .inputTokenCount(usage.totalInputTokens().orElse(null))
                .outputTokenCount(usage.totalOutputTokens().orElse(null))
                .totalTokenCount(usage.totalTokens().orElse(null))
                .cachedContentTokenCount(usage.totalCachedTokens().orElse(null))
                .thoughtsTokenCount(usage.totalThoughtTokens().orElse(null))
                .toolUsePromptTokenCount(usage.totalToolUseTokens().orElse(null))
                .initialPromptTokenCount(usage.initialInputTokens().orElse(null))
                .build();
    }
}
