package dev.langchain4j.micrometer.listeners;

import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerChatModelListenerBuilder {
    private MeterRegistry meterRegistry;
    private String metricPrefix = "langchain4j.chat.";
    
    public MicrometerChatModelListenerBuilder withMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        return this;
    }
    
    public MicrometerChatModelListenerBuilder withMetricPrefix(String prefix) {
        this.metricPrefix = prefix;
        return this;
    }
    
    public MicrometerChatModelListener build() {
        if (meterRegistry == null) {
            throw new IllegalStateException("MeterRegistry must be provided");
        }
        return new MicrometerChatModelListener(meterRegistry);
    }
} 
