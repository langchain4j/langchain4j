package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.DocumentSplitter;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.util.stream.Collectors.toList;

public class SentenceSplitter implements DocumentSplitter {

    @Override
    public List<TextSegment> split(Document document) {
        String text = document.text();
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Document text should not be null or empty");
        }

        List<String> sentences = splitIntoSentences(text);

        return sentences.stream()
                .map(sentence -> TextSegment.from(sentence.trim(), document.metadata()))
                .collect(toList());
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();

        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.getDefault());
        iterator.setText(text);

        int start = iterator.first();
        for (int end = iterator.next();
             end != BreakIterator.DONE;
             start = end, end = iterator.next()) {
            sentences.add(text.substring(start, end).trim());
        }

        return sentences;
    }
}
