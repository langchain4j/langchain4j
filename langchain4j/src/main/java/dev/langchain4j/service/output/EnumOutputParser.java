package dev.langchain4j.service.output;

import dev.langchain4j.model.output.structured.Description;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Optional;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@SuppressWarnings("rawtypes")
class EnumOutputParser implements OutputParser<Enum> {

    private final Class<? extends Enum> enumClass;

    EnumOutputParser(Class<? extends Enum> enumClass) {
        this.enumClass = ensureNotNull(enumClass, "enumClass");
    }

    @Override
    public Enum parse(String string) {
        string = trimAndRemoveBracketsIfPresent(string);
        for (Enum enumConstant : enumClass.getEnumConstants()) {
            if (enumConstant.name().equalsIgnoreCase(string)) {
                return enumConstant;
            }
        }
        throw new RuntimeException("Unknown enum value: " + string);
    }

    @Override
    public String formatInstructions() {
        try {
            Enum[] enumConstants = enumClass.getEnumConstants();

            if (enumConstants.length == 0) {
                throw new IllegalArgumentException("Should be at least one enum constant defined.");
            }

            StringBuilder instruction = new StringBuilder();

            // 'enums' keyword will hopefully make it clearer that
            // no description should be included (if present)
            instruction.append("\nYou must answer strictly with one of these enums:");

            for (Enum enumConstant : enumConstants) {
                instruction.append("\n").append(enumConstant.name().toUpperCase(Locale.ROOT));
                Optional<String> optionalEnumDescription = getEnumDescription(enumClass, enumConstant);
                optionalEnumDescription.ifPresent(description -> instruction.append(" - ").append(description));
            }

            return instruction.toString();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns Enum description (if present)
     *
     * @param enumConstant for which description should be returned
     * @return description of the provided enum
     */
    static Optional<String> getEnumDescription(Class<? extends Enum> enumClass, Enum enumConstant) throws NoSuchFieldException {
        Field field = enumClass.getDeclaredField(enumConstant.name());

        if (field.isAnnotationPresent(Description.class)) {
            Description annotation = field.getAnnotation(Description.class);
            if (annotation != null) {
                return Optional.of(String.join(" ", annotation.value()));
            }
        }

        return Optional.empty();
    }

    /**
     * In some rare cases, the model returns <b>[ENUM_NAME]</b> instead of <b>ENUM_NAME</b>.
     * This method normalizes the string by removing any brackets and trimming it.
     * Note: This is considered as temporary patch, as discussed in:
     * <a href="https://github.com/langchain4j/langchain4j/issues/725">#725</a>
     */
    private String trimAndRemoveBracketsIfPresent(String string) {
        string = string.trim();
        if (string.startsWith("[") && string.endsWith("]")) {
            string = string.substring(1, string.length() - 1);
        }
        return string.trim();
    }
}
