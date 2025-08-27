package dev.langchain4j.agentic.agent;

public record ErrorRecoveryResult(Type type, Object result) {

    public enum Type {
        THROW_EXCEPTION, RETURN_RESULT, RETRY
    }

    public static ErrorRecoveryResult throwException() {
        return new ErrorRecoveryResult(Type.THROW_EXCEPTION, null);
    }

    public static ErrorRecoveryResult retry() {
        return new ErrorRecoveryResult(Type.RETRY, null);
    }

    public static ErrorRecoveryResult result(Object result) {
        return new ErrorRecoveryResult(Type.RETURN_RESULT, result);
    }
}
