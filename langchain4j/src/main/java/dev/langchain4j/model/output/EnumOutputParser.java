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
        return "one of " + Arrays.toString(enumClass.getEnumConstants());
    }
}
