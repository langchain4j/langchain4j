package dev.langchain4j.model.structureddecision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An explicit, state-free snapshot of the schema and returned decisions. */
@Experimental
public record StructuredDecisionReceipt(String schemaId, String schemaVersion,
                                        List<QuestionSnapshot> questions,
                                        Map<String, AnswerSnapshot> answers) {

    public StructuredDecisionReceipt {
        schemaId = ensureNotBlank(schemaId, "schemaId");
        schemaVersion = ensureNotBlank(schemaVersion, "schemaVersion");
        questions = List.copyOf(ensureNotNull(questions, "questions"));
        answers = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(ensureNotNull(answers, "answers")));
    }

    public static StructuredDecisionReceipt from(String schemaId, String schemaVersion,
                                                 StructuredDecisionRequest request,
                                                 StructuredDecisionResponse response) {
        ensureNotNull(request, "request");
        ensureNotNull(response, "response");
        List<QuestionSnapshot> questions = new ArrayList<>();
        request.questions().forEach((name, question) -> questions.add(new QuestionSnapshot(
                name, question.getClass().getName(), optionIds(question))));
        Map<String, AnswerSnapshot> answers = new LinkedHashMap<>();
        response.answers().forEach((name, answer) -> {
            ensureTrue(request.questions().containsKey(name), "Unknown answer name: " + name);
            answers.put(name, new AnswerSnapshot(answer.value(), answer.confidence(),
                    answer.confidenceProvenance()));
        });
        return new StructuredDecisionReceipt(schemaId, schemaVersion, questions, answers);
    }

    private static List<String> optionIds(Question question) {
        if (question instanceof ChoiceQuestion choice) {
            return List.copyOf(choice.options().keySet());
        }
        if (question instanceof ScoreQuestion score) {
            return List.copyOf(score.levels().keySet());
        }
        return List.of();
    }

    public record QuestionSnapshot(String name, String type, List<String> optionIds) {
        public QuestionSnapshot {
            name = ensureNotBlank(name, "name");
            type = ensureNotBlank(type, "type");
            optionIds = List.copyOf(ensureNotNull(optionIds, "optionIds"));
        }
    }

    public record AnswerSnapshot(Object value, Double confidence, ConfidenceProvenance confidenceProvenance) {
        public AnswerSnapshot {
            ensureNotNull(value, "value");
            value = DecisionSnapshots.value(value);
            AnswerValidation.validateConfidence(confidence, confidenceProvenance);
        }
    }
}
