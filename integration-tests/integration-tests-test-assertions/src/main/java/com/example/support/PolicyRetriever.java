package com.example.support;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Finds the policy paragraphs that share the first 4 letters of a word with the query.
 */
public class PolicyRetriever implements ContentRetriever {

    static final List<String> POLICIES = List.of(
            "Cancellation: bookings can be cancelled free of charge up to 7 days before the start date.",
            "Refunds: refunds are paid to the original payment method within 14 days.",
            "Pets: small pets are allowed in all rooms for a fee of 10 EUR per day.");

    @Override
    public List<Content> retrieve(Query query) {
        List<String> words = Arrays.stream(query.text().toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(word -> word.length() >= 4)
                .map(word -> word.substring(0, 4))
                .toList();
        return POLICIES.stream()
                .filter(policy -> words.stream().anyMatch(policy.toLowerCase(Locale.ROOT)::contains))
                .map(Content::from)
                .toList();
    }
}
