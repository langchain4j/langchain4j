package dev.langchain4j.model.chat.policy;

import java.util.concurrent.Callable;

import static dev.langchain4j.model.chat.policy.RetryUtils.DEFAULT_RETRY_POLICY;

@FunctionalInterface
public interface InvocationPolicy {

    InvocationPolicy DEFAULT = ExceptionMapper.DEFAULT.andThen(RetryUtils.DEFAULT_RETRY_POLICY);

    Callable<?> apply(Callable<?> action);

    default InvocationPolicy andThen(InvocationPolicy invocationPolicy) {
        return action -> invocationPolicy.apply(this.apply(action));
    }

    static InvocationPolicyBuilder builder() {
        return new InvocationPolicyBuilder();
    }

    class InvocationPolicyBuilder {
        private RetryUtils.RetryPolicy retryPolicy = DEFAULT_RETRY_POLICY;
        private ExceptionMapper exceptionMapper = ExceptionMapper.DEFAULT;

        public InvocationPolicyBuilder withRetryPolicy(RetryUtils.RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public InvocationPolicyBuilder withExceptionMapper(ExceptionMapper exceptionMapper) {
            this.exceptionMapper = exceptionMapper;
            return this;
        }

        public InvocationPolicy build() {
            return exceptionMapper.andThen(retryPolicy);
        }
    }
}
