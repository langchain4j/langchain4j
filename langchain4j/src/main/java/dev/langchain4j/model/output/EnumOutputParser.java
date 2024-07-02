package dev.langchain4j.model.output;

import java.util.Arrays;

@SuppressWarnings("rawtypes")
public class EnumOutputParser implements OutputParser<Enum> {

    private final Class<? extends Enum> enumClass;

    public EnumOutputParser(Class<? extends Enum> enumClass) {
        this.enumClass = enumClass;
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
        return "one of " + Arrays.toString(enumClass.getEnumConstants());
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
