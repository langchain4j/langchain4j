package com.example.support;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class PolicyRetriever implements ContentRetriever {

    @Override
    public List<Content> retrieve(Query query) {
        if (!query.text().contains("policy")) {
            return List.of();
        }
        return List.of(
                Content.from("Cars can be returned free of charge within 14 days."),
                Content.from("Late returns cost 30 EUR per started day."));
    }
}
