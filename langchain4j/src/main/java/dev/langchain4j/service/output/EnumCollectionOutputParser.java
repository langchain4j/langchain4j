package dev.langchain4j.service.output;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.output.EnumOutputParser.getEnumDescription;

@SuppressWarnings("rawtypes")
abstract class EnumCollectionOutputParser<T extends Enum> implements OutputParser<Collection<T>> {

    private final Class<? extends Enum> enumClass;
    protected final EnumOutputParser enumOutputParser;

    EnumCollectionOutputParser(Class<? extends Enum> enumClass) {
        this.enumClass = ensureNotNull(enumClass, "enumClass");
        this.enumOutputParser = new EnumOutputParser(enumClass);
    }

    @Override
    public String formatInstructions() {
        try {
            Enum<?>[] enumConstants = enumClass.getEnumConstants();

            if (enumConstants.length == 0) {
                throw new IllegalArgumentException("Should be at least one enum constant defined.");
            }

            StringBuilder instruction = new StringBuilder();

            // 'enums' keyword will hopefully make it clearer that
            // no description should be included (if present)
            instruction.append("\nYou must answer strictly with zero or more of these enums on a separate line:");

            for (Enum<?> enumConstant : enumConstants) {
                instruction.append("\n").append(enumConstant.name().toUpperCase(Locale.ROOT));
                Optional<String> optionalEnumDescription = getEnumDescription(enumClass, enumConstant);
                optionalEnumDescription.ifPresent(description -> instruction.append(" - ").append(description));
            }

            return instruction.toString();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
