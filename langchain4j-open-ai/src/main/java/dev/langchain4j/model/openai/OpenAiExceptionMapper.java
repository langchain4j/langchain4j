package dev.langchain4j.model.openai;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.policy.ExceptionMapper;

class OpenAiExceptionMapper implements ExceptionMapper {

    static final OpenAiExceptionMapper INSTANCE = new OpenAiExceptionMapper();

    @Override
    public RuntimeException mapException(Exception e) {
        if (e instanceof HttpException openAiHttpException) {
            return openAiHttpException;
        }
        if (e.getCause() instanceof HttpException openAiHttpException) {
            return openAiHttpException;
        }
        return e instanceof RuntimeException re ? re : new RuntimeException(e);
    }
}
