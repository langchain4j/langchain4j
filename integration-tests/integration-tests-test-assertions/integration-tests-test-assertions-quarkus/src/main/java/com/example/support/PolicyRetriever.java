package com.example.support;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class PolicyRetriever implements ContentRetriever {

    static final String RETURN_POLICY = "Items can be returned within 30 days of delivery for a full refund.";
    static final String SHIPPING_POLICY = "Standard shipping takes 3 to 5 business days.";

    @Override
    public List<Content> retrieve(Query query) {
        String text = query.text().toLowerCase(Locale.ROOT);
        if (text.contains("return") || text.contains("refund")) {
            return List.of(Content.from(RETURN_POLICY));
        }
        if (text.contains("shipping") || text.contains("delivery")) {
            return List.of(Content.from(SHIPPING_POLICY));
        }
        return List.of();
    }

    @Override
    public CompletableFuture<List<Content>> retrieveAsync(Query query) {
        return CompletableFuture.completedFuture(retrieve(query));
    }
}
