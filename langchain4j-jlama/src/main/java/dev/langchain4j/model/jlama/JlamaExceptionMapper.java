package dev.langchain4j.model.jlama;

import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.internal.ExceptionMapper;

import java.io.IOException;

class JlamaExceptionMapper extends ExceptionMapper.DefaultExceptionMapper {

    static final JlamaExceptionMapper INSTANCE = new JlamaExceptionMapper();

    private static final String JLAMA_IOEXCEPTION_START_MESSAGE = "HTTP response code: ";

    private JlamaExceptionMapper() { }

    @Override
    public RuntimeException mapException(Throwable t) {
        if (t instanceof IOException && t.getMessage().startsWith(JLAMA_IOEXCEPTION_START_MESSAGE)) {
            String httpStatusCode = t.getMessage().substring(JLAMA_IOEXCEPTION_START_MESSAGE.length(), JLAMA_IOEXCEPTION_START_MESSAGE.length() + 3);
            try {
                return mapHttpStatusCode(t, Integer.parseInt(httpStatusCode));
            } catch (NumberFormatException nfe) {
                // ignore
            }
        }

        return new InternalServerException(t);
    }
}
