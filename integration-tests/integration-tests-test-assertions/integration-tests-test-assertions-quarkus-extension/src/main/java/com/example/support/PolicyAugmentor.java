package com.example.support;

import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

@ApplicationScoped
public class PolicyAugmentor implements Supplier<RetrievalAugmentor> {

    private final RetrievalAugmentor augmentor;

    public PolicyAugmentor(PolicyRetriever policyRetriever) {
        this.augmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(policyRetriever)
                .build();
    }

    @Override
    public RetrievalAugmentor get() {
        return augmentor;
    }
}
