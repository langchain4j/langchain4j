package dev.langchain4j.model.embedding;

import ai.djl.modality.nlp.DefaultVocabulary;
import ai.djl.modality.nlp.Vocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;

import java.util.List;

public class BertTokenizer implements Tokenizer {

    private final BertFullTokenizer tokenizer;

    public BertTokenizer() {
        try {
            Vocabulary vocabulary = DefaultVocabulary.builder()
                    .addFromTextFile(getClass().getResource("/bert-vocabulary.txt"))
                    .build();
            this.tokenizer = new BertFullTokenizer(vocabulary, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int countTokens(String text) {
        return tokenizer.tokenize(text).size();
    }

    @Override
    public int countTokens(ChatMessage message) {
        return countTokens(message.text());
    }

    @Override
    public int countTokens(Iterable<ChatMessage> messages) {
        int tokens = 0;
        for (ChatMessage message : messages) {
            tokens += countTokens(message);
        }
        return tokens;
    }

    public List<String> tokenize(String text) {
        return tokenizer.tokenize(text);
    }

    public long tokenId(String token) {
        return tokenizer.getVocabulary().getIndex(token);
    }
}
