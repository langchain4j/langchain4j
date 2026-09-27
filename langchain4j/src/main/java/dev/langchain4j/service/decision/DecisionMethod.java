package dev.langchain4j.service.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;

import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.ParameterNameResolver;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * The analysis of one decision service method: the questions it asks, how its parameters become the state, and how
 * the answers are mapped back to its return type. Created once per method, when the service is built.
 */
final class DecisionMethod {

    private static final double DEFAULT_THRESHOLD = 0.5;

    private final Method method;
    private final boolean async;
    private final boolean withResponse;
    private final List<StateParameter> stateParameters = new ArrayList<>();
    private Integer requestParametersIndex;
    private final Map<String, QuestionMapping> mappings = new LinkedHashMap<>();
    private final Map<String, Question> questions = new LinkedHashMap<>();
    private final Function<Map<String, Object>, Object> resultFactory;

    DecisionMethod(Method method) {
        this.method = method;

        Type type = method.getGenericReturnType();
        this.async = isParameterizedBy(type, CompletableFuture.class);
        if (async) {
            type = typeArgument(type);
        }
        this.withResponse = isParameterizedBy(type, DecisionResult.class);
        if (withResponse) {
            type = typeArgument(type);
        }

        Decide decide = method.getAnnotation(Decide.class);
        QuestionMapping single = mappingFor(method.getName(), type, decide == null ? null : decide.value());
        if (single != null) {
            if (decide == null) {
                throw illegalConfiguration(
                        "Method '%s' must be annotated with @Decide, which contains the question to answer",
                        method.getName());
            }
            add(single);
            this.resultFactory = values -> values.get(single.name());
        } else if (type instanceof Class<?> objectType && isObjectType(objectType)) {
            if (decide != null) {
                throw illegalConfiguration(
                        "Method '%s' returns %s, which contains several questions. "
                                + "@Decide is not supported on such methods: annotate the fields of %s instead",
                        method.getName(), objectType.getSimpleName(), objectType.getSimpleName());
            }
            this.resultFactory = objectFactory(objectType);
        } else {
            throw illegalConfiguration(
                    "Method '%s' has an unsupported return type: %s. Supported types are boolean, YesNo, an enum, "
                            + "Choice<enum>, and objects whose fields are of these types, optionally wrapped in "
                            + "DecisionResult<> and/or CompletableFuture<>",
                    method.getName(), method.getGenericReturnType().getTypeName());
        }

        analyzeParameters();
    }

    Object invoke(DecisionModel decisionModel, Object[] args, Function<String, Double> thresholdProvider) {
        if (!async) {
            return result(decisionModel.decide(request(args)), thresholdProvider);
        }
        DecisionRequest request;
        try {
            request = request(args);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return decisionModel.decideAsync(request).thenApply(response -> result(response, thresholdProvider));
    }

    private DecisionRequest request(Object[] args) {
        return DecisionRequest.builder()
                .state(state(args))
                .questions(questions)
                .parameters(requestParameters(args))
                .build();
    }

    private Object result(DecisionResponse response, Function<String, Double> thresholdProvider) {
        Map<String, Object> values = new LinkedHashMap<>();
        mappings.forEach((name, mapping) ->
                values.put(name, mapping.value(response, () -> threshold(name, thresholdProvider))));
        Object content = resultFactory.apply(values);
        return withResponse ? new DecisionResult<>(content, response) : content;
    }

    private double threshold(String questionName, Function<String, Double> thresholdProvider) {
        Double threshold = thresholdProvider == null ? null : thresholdProvider.apply(questionName);
        if (threshold == null) {
            threshold = DEFAULT_THRESHOLD;
        }
        return YesNo.ensureProbability(threshold, "threshold for '" + questionName + "'");
    }

    private DecisionRequestParameters requestParameters(Object[] args) {
        if (requestParametersIndex == null) {
            return null;
        }
        return ensureNotNull(
                (DecisionRequestParameters) args[requestParametersIndex], DecisionRequestParameters.class.getSimpleName());
    }

    private Map<String, Object> state(Object[] args) {
        Map<String, Object> state = new LinkedHashMap<>();
        for (StateParameter parameter : stateParameters) {
            Object value = args[parameter.index()];
            if (value != null) {
                state.put(parameter.name(), value);
            }
        }
        return state;
    }

    private void analyzeParameters() {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (DecisionRequestParameters.class.isAssignableFrom(parameter.getType())) {
                if (requestParametersIndex != null) {
                    throw illegalConfiguration(
                            "Method '%s' has several DecisionRequestParameters parameters", method.getName());
                }
                requestParametersIndex = i;
                continue;
            }
            if (!ParameterNameResolver.hasName(parameter) && !parameter.isNamePresent()) {
                throw illegalConfiguration(
                        "The name of parameter %s of method '%s' is not available, but it is sent to the model "
                                + "together with the value. Compile with the '-parameters' option "
                                + "or annotate the parameter with @V(\"name\")",
                        i, method.getName());
            }
            String name = ParameterNameResolver.name(parameter);
            if (stateParameters.stream().anyMatch(p -> p.name().equals(name))) {
                throw illegalConfiguration(
                        "Method '%s' has several parameters named '%s'", method.getName(), name);
            }
            stateParameters.add(new StateParameter(name, i));
        }
        if (stateParameters.isEmpty()) {
            throw illegalConfiguration(
                    "Method '%s' must have at least one parameter that is not DecisionRequestParameters: "
                            + "the parameters are what the model evaluates",
                    method.getName());
        }
    }

    private Function<Map<String, Object>, Object> objectFactory(Class<?> type) {
        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] componentTypes = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];
                componentTypes[i] = component.getType();
                add(fieldMapping(declaredField(type, component.getName()), component.getGenericType()));
            }
            Constructor<?> constructor = constructor(type, componentTypes);
            return values -> {
                Object[] arguments = new Object[components.length];
                for (int i = 0; i < components.length; i++) {
                    arguments[i] = values.get(components[i].getName());
                }
                return newInstance(constructor, arguments);
            };
        }

        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        if (fields.isEmpty()) {
            throw illegalConfiguration("%s, returned by method '%s', has no fields to decide on",
                    type.getSimpleName(), method.getName());
        }
        for (Field field : fields) {
            add(fieldMapping(field, field.getGenericType()));
            field.setAccessible(true);
        }
        Constructor<?> constructor = constructor(type);
        return values -> {
            Object instance = newInstance(constructor);
            for (Field field : fields) {
                try {
                    field.set(instance, values.get(field.getName()));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot set field '%s'".formatted(field.getName()), e);
                }
            }
            return instance;
        };
    }

    private QuestionMapping fieldMapping(Field field, Type type) {
        String questionText = Optional.ofNullable(field.getAnnotation(Decide.class))
                .map(Decide::value)
                .or(() -> Optional.ofNullable(field.getAnnotation(Description.class))
                        .map(description -> String.join(" ", description.value())))
                .orElse(field.getName());
        QuestionMapping mapping = mappingFor(field.getName(), type, questionText);
        if (mapping == null) {
            throw illegalConfiguration(
                    "Field '%s' of %s, returned by method '%s', has an unsupported type: %s. "
                            + "Supported types are boolean, YesNo, an enum and Choice<enum>",
                    field.getName(), field.getDeclaringClass().getSimpleName(), method.getName(),
                    type.getTypeName());
        }
        return mapping;
    }

    private void add(QuestionMapping mapping) {
        mappings.put(mapping.name(), mapping);
        questions.put(mapping.name(), mapping.question());
    }

    private QuestionMapping mappingFor(String name, Type type, String questionText) {
        Kind kind;
        Class<?> enumType = null;
        if (type == boolean.class || type == Boolean.class) {
            kind = Kind.BOOLEAN;
        } else if (type == YesNo.class) {
            kind = Kind.YES_NO;
        } else if (type instanceof Class<?> c && c.isEnum()) {
            kind = Kind.ENUM;
            enumType = c;
        } else if (isParameterizedBy(type, Choice.class)) {
            kind = Kind.CHOICE;
            if (!(typeArgument(type) instanceof Class<?> c) || !c.isEnum()) {
                throw illegalConfiguration("'%s' of method '%s': Choice must be parameterized with an enum",
                        name, method.getName());
            }
            enumType = c;
        } else {
            return null;
        }
        if (questionText == null) {
            return new QuestionMapping(name, null, kind, enumType);
        }
        Question question = enumType == null
                ? YesNoQuestion.builder().instructions(questionText).build()
                : choiceQuestion(name, questionText, enumType);
        return new QuestionMapping(name, question, kind, enumType);
    }

    private ChoiceQuestion choiceQuestion(String name, String questionText, Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        if (constants.length < 2) {
            throw illegalConfiguration("'%s' of method '%s': enum %s must have at least 2 constants",
                    name, method.getName(), enumType.getSimpleName());
        }
        ChoiceQuestion.Builder builder = ChoiceQuestion.builder().instructions(questionText);
        for (Object constant : constants) {
            String option = ((Enum<?>) constant).name();
            Description description = declaredField(enumType, option).getAnnotation(Description.class);
            builder.option(option, description == null ? option : String.join(" ", description.value()));
        }
        return builder.build();
    }

    private static boolean isObjectType(Class<?> type) {
        return !type.isPrimitive()
                && !type.isArray()
                && !type.isInterface()
                && !type.isEnum()
                && !Modifier.isAbstract(type.getModifiers())
                && !type.getName().startsWith("java.")
                && !Collection.class.isAssignableFrom(type)
                && !Map.class.isAssignableFrom(type);
    }

    private static boolean isParameterizedBy(Type type, Class<?> rawType) {
        return type instanceof ParameterizedType parameterized && parameterized.getRawType() == rawType;
    }

    private static Type typeArgument(Type type) {
        return ((ParameterizedType) type).getActualTypeArguments()[0];
    }

    private static Field declaredField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private Constructor<?> constructor(Class<?> type, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw illegalConfiguration("%s, returned by method '%s', must have a no-argument constructor",
                    type.getSimpleName(), method.getName());
        }
    }

    private static Object newInstance(Constructor<?> constructor, Object... arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot create an instance of " + constructor.getDeclaringClass().getName(), e);
        }
    }

    private enum Kind {
        BOOLEAN,
        YES_NO,
        ENUM,
        CHOICE
    }

    private record StateParameter(String name, int index) {}

    private record QuestionMapping(String name, Question question, Kind kind, Class<?> enumType) {

        Object value(DecisionResponse response, DoubleSupplier threshold) {
            return switch (kind) {
                case BOOLEAN -> response.yesNo(name).probability() >= threshold.getAsDouble();
                case YES_NO -> new YesNo(response.yesNo(name).probability());
                case ENUM -> constant(response.choice(name).choice());
                case CHOICE -> choice(response.choice(name));
            };
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Enum constant(String option) {
            try {
                return Enum.valueOf((Class) enumType, option);
            } catch (IllegalArgumentException e) {
                throw new LangChain4jException("The model chose '%s' for '%s', which is not a constant of %s"
                        .formatted(option, name, enumType.getSimpleName()));
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Choice choice(ChoiceAnswer answer) {
            Map probabilities = new EnumMap(enumType);
            answer.probabilities().forEach((option, probability) -> {
                for (Object constant : enumType.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equals(option)) {
                        probabilities.put(constant, probability);
                    }
                }
            });
            return new Choice(constant(answer.choice()), probabilities, answer.confidence());
        }
    }
}
