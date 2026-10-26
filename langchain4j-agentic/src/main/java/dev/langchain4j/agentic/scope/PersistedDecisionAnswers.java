package dev.langchain4j.agentic.scope;

import dev.langchain4j.Internal;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Scale;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The persistable form of the answers that decision agents write to the {@link AgenticScope}: {@link Choice} and
 * {@link Scale} are generic in their enum type, which the JSON serialization of the scope would lose, and none of
 * the three types has a constructor a JSON library can call. The {@link AgenticScopeJsonCodec} converts them to
 * these records before writing the scope and back after reading it.
 * <p>
 * The records keep one constant of the enum in a field declared as {@link Enum}, so that the codec writes its type
 * next to it and resolves that type when reading, with its own class loader and allowlist, which always admits
 * enums. The probabilities are kept by constant name.
 */
@Internal
public final class PersistedDecisionAnswers {

    private static final ClassValue<Boolean> IS_COMPOSITE_ANSWER = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return hasAnswerField(type);
        }
    };

    private PersistedDecisionAnswers() {}

    public record PersistedYesNoAnswer(double probability) {}

    public record PersistedChoice(Enum<?> value, Map<String, Double> probabilities, Double confidence) {}

    public record PersistedScale(
            Enum<?> mostLikely, Double mean, Map<String, Double> probabilities, Double confidence) {}

    /**
     * Whether the given value is an object with fields of the answer types, inherited ones included, like the record
     * a decision agent returns to answer several questions in one call. Its fields cannot be converted in place, so
     * such a value is not persisted, like a {@code DecisionResult}: the records of a recoverable system should have
     * {@code boolean} and enum fields instead. The check runs on every agent output and on every state entry read by
     * an untyped agent or persisted, so its result is computed once per class.
     */
    public static boolean isCompositeAnswer(Object value) {
        return value != null && IS_COMPOSITE_ANSWER.get(value.getClass());
    }

    private static boolean hasAnswerField(Class<?> valueType) {
        // the same fields DecisionMethod fills when it builds the answer
        for (Class<?> type = valueType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers)
                        && !Modifier.isTransient(modifiers)
                        && isAnswerType(field.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAnswerType(Class<?> type) {
        return type == YesNoAnswer.class || type == Choice.class || type == Scale.class;
    }

    /**
     * The persistable form of the given value, or the value itself when it is not an answer of a decision agent.
     */
    public static Object toPersisted(Object value) {
        if (value instanceof YesNoAnswer yesNo) {
            return new PersistedYesNoAnswer(yesNo.probability());
        }
        if (value instanceof Choice<?> choice && choice.value() instanceof Enum<?> option) {
            return new PersistedChoice(option, names(choice.probabilities()), choice.confidence());
        }
        if (value instanceof Scale<?> scale) {
            return new PersistedScale(
                    scale.mostLikely(), scale.mean(), names(scale.probabilities()), scale.confidence());
        }
        return value;
    }

    /**
     * The answer restored from its persistable form, or the value itself when it is not one.
     */
    public static Object fromPersisted(Object value) {
        if (value instanceof PersistedYesNoAnswer yesNo) {
            return YesNoAnswer.from(yesNo.probability());
        }
        if (value instanceof PersistedChoice choice) {
            return restoreChoice(choice);
        }
        if (value instanceof PersistedScale scale) {
            return restoreScale(scale);
        }
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Choice<?> restoreChoice(PersistedChoice choice) {
        Class optionType = choice.value().getDeclaringClass();
        return Choice.builder()
                .value(choice.value())
                .probabilities(constants(choice.probabilities(), optionType))
                .confidence(choice.confidence())
                .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Scale<?> restoreScale(PersistedScale scale) {
        Class levelType = scale.mostLikely().getDeclaringClass();
        return Scale.builder(levelType)
                .mean(scale.mean())
                .probabilities(constants(scale.probabilities(), levelType))
                .confidence(scale.confidence())
                .build();
    }

    private static Map<String, Double> names(Map<?, Double> probabilities) {
        if (probabilities == null) {
            return null;
        }
        Map<String, Double> names = new LinkedHashMap<>();
        probabilities.forEach((constant, probability) -> names.put(((Enum<?>) constant).name(), probability));
        return names;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map constants(Map<String, Double> probabilities, Class type) {
        if (probabilities == null) {
            return null;
        }
        Map<Enum, Double> constants = new EnumMap<>(type);
        probabilities.forEach((name, probability) -> constants.put(Enum.valueOf(type, name), probability));
        return constants;
    }
}
