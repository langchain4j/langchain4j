package dev.langchain4j.model.chat.policy;

import java.util.concurrent.Callable;

public interface ExceptionMapper extends InvocationPolicy {

    ExceptionMapper DEFAULT = new DefaultExceptionMapper();

    @Override
    default Callable<?> apply(final Callable<?> action) {
        return () -> {
            try {
                return action.call();
            } catch (Exception e) {
                throw mapException(e);
            }
        };
    }

    RuntimeException mapException(Exception e);

    class DefaultExceptionMapper implements ExceptionMapper {
        @Override
        public RuntimeException mapException(Exception e) {
            System.out.println(e.getMessage());
            return e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }
}
