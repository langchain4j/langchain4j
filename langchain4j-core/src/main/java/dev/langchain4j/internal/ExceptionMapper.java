package dev.langchain4j.internal;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.UnrecoverableException;

import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.Callable;

@FunctionalInterface
public interface ExceptionMapper {

    ExceptionMapper DEFAULT = new DefaultImpl();

    static <T> T mappingException(Callable<T> action) {
        return DEFAULT.withExceptionMapper(action);
    }

    default <T> T withExceptionMapper(Callable<T> action) {
        try {
            return action.call();
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    RuntimeException mapException(Exception e);

    class DefaultImpl implements ExceptionMapper {

        @Override
        public RuntimeException mapException(Exception e) {
            Throwable rootCause = findRoot(e);

            if (rootCause instanceof HttpException httpException) {
                return switch (httpException.statusCode()) {
                    case 401, 404 -> new UnrecoverableException(rootCause);
                    case 429 -> new RateLimitException(rootCause);
                    default -> httpException;
                };
            }

            if (rootCause instanceof UnresolvedAddressException) {
                return new UnrecoverableException(rootCause);
            }

            return e instanceof RuntimeException re ? re : new RuntimeException(e);
        }

        private static Throwable findRoot(Throwable e) {
            Throwable cause = e.getCause();
            return cause == null || cause == e ? e : findRoot(cause);
        }
    }
}
