package dev.langchain4j.service.tool;

public class DuplicateToolException extends RuntimeException {
    public DuplicateToolException(String duplicatedToolName) {
        super("Duplicated definition for tool: " + duplicatedToolName);
    }
}
