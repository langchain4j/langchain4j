package dev.langchain4j.model.chat.policy;

import java.util.concurrent.Callable;

public class PolicyUtil {
    public static <T> T invokePolicy(InvocationPolicy policy, Callable<T> callable) {
        try {
            return (T) policy.apply(callable).call();
        } catch (Exception e) {
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }
}
