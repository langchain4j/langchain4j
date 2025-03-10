package dev.langchain4j.internal;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnrecoverableException;
import dev.langchain4j.exception.UnresolvedModelServerException;

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
                return mapHttpStatusCode(httpException, httpException.statusCode());
            }

            if (rootCause instanceof UnresolvedAddressException) {
                return new UnresolvedModelServerException(rootCause);
            }

            return e instanceof RuntimeException re ? re : new RuntimeException(e);
        }

        protected RuntimeException mapHttpStatusCode(Exception rootException, int httpStatusCode) {
            return switch (httpStatusCode) {
                case 400 -> new InvalidRequestException(rootException);
                case 401, 403 -> new AuthenticationException(rootException);
                case 404 -> new ModelNotFoundException(rootException);
                case 408 -> new TimeoutException(rootException);
                case 429 -> new RateLimitException(rootException);
                case 500 -> new InternalServerException(rootException);
                default -> rootException instanceof RuntimeException re ? re : new RuntimeException(rootException);
            };
        }

        private static Throwable findRoot(Throwable e) {
            Throwable cause = e.getCause();
            return cause == null || cause == e ? e : findRoot(cause);
        }
    }
}
