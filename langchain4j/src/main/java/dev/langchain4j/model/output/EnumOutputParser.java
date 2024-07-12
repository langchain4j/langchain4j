package dev.langchain4j.model.output;

import dev.langchain4j.model.output.structured.Description;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Optional;

@SuppressWarnings("rawtypes")
public class EnumOutputParser implements OutputParser<Enum> {

    private final Class<? extends Enum> enumClass;

    public EnumOutputParser(Class<? extends Enum> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public Enum parse(String string) {
        string = string.trim();
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
            instruction.append("one of these enums:");

            for (Enum enumConstant : enumConstants) {
                instruction.append("\n").append(enumConstant.name().toUpperCase(Locale.ROOT));
                Optional<String> optionalEnumDescription = getEnumDescription(enumConstant);
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
    private Optional<String> getEnumDescription(Enum enumConstant) throws NoSuchFieldException {
        Field field = enumClass.getDeclaredField(enumConstant.name());

        if (field.isAnnotationPresent(Description.class)) {
            Description annotation = field.getAnnotation(Description.class);
            if (annotation != null) {
                return Optional.of(String.join(" ", annotation.value()));
            }
        }

        return Optional.empty();
    }
}
