package dev.langchain4j.model.output;

public class ByteOutputParser implements OutputParser<Byte> {

    @Override
    public Byte parse(String string) {
        return Byte.parseByte(string.trim());
    }

    @Override
    public String formatInstructions() {
        return "integer number in range [-128, 127]";
    }
}
