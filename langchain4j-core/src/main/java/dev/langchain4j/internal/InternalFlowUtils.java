package dev.langchain4j.internal;

import dev.langchain4j.Internal;

import java.util.concurrent.Flow;

@Internal
public class InternalFlowUtils {

    public static final Flow.Subscription EMPTY_SUBSCRIPTION = new Flow.Subscription() {
        @Override
        public void request(long n) {}

        @Override
        public void cancel() {}
    };

}
