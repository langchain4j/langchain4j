package com.example.support.knowledge;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseRetriever implements ContentRetriever {

    @Override
    public List<Content> retrieve(Query query) {
        String text = query.text().toLowerCase(Locale.ROOT);
        if (text.contains("policy") || text.contains("return")) {
            return List.of(
                    Content.from("Cars can be returned free of charge within 14 days of the booking."),
                    Content.from("Late returns cost 30 EUR per started day."));
        }
        return List.of();
    }
}
