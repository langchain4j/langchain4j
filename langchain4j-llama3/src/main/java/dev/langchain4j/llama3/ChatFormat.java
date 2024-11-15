package dev.langchain4j.llama3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility tailored for Llama 3 instruct prompt format.
 */
public class ChatFormat {

    final Tokenizer tokenizer;
    final int beginOfText;
    final int endHeader;
    final int startHeader;
    final int endOfTurn;
    final int endOfText;
    final int endOfMessage;
    final Set<Integer> stopTokens;

    public ChatFormat(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        Map<String, Integer> specialTokens = this.tokenizer.getSpecialTokens();
        this.beginOfText = specialTokens.get("<|begin_of_text|>");
        this.startHeader = specialTokens.get("<|start_header_id|>");
        this.endHeader = specialTokens.get("<|end_header_id|>");
        this.endOfTurn = specialTokens.get("<|eot_id|>");
        this.endOfText = specialTokens.get("<|end_of_text|>");
        this.endOfMessage = specialTokens.getOrDefault("<|eom_id|>", -1); // only in 3.1
        this.stopTokens = Set.of(endOfText, endOfTurn);
    }

    public Tokenizer getTokenizer() {
        return tokenizer;
    }

    public Set<Integer> getStopTokens() {
        return stopTokens;
    }

    public List<Integer> encodeHeader(Message message) {
        List<Integer> tokens = new ArrayList<>();
        tokens.add(startHeader);
        tokens.addAll(this.tokenizer.encodeAsList(message.role().name()));
        tokens.add(endHeader);
        tokens.addAll(this.tokenizer.encodeAsList("\n"));
        return tokens;
    }

    public List<Integer> encodeMessage(Message message) {
        List<Integer> tokens = this.encodeHeader(message);
        tokens.addAll(this.tokenizer.encodeAsList(message.content().strip()));
        tokens.add(endOfTurn);
        return tokens;
    }

    public List<Integer> encodeDialogPrompt(boolean appendAssistantTurn, List<Message> dialog) {
        List<Integer> tokens = new ArrayList<>();
        tokens.add(beginOfText);
        for (Message message : dialog) {
            tokens.addAll(this.encodeMessage(message));
        }
        if (appendAssistantTurn) {
            // Add the start of an assistant message for the model to complete.
            tokens.addAll(this.encodeHeader(new Message(Role.ASSISTANT, "")));
        }
        return tokens;
    }

    public record Message(Role role, String content) {
    }

    public record Role(String name) {
        public static Role SYSTEM = new Role("system");
        public static Role USER = new Role("user");
        public static Role ASSISTANT = new Role("assistant");

        @Override
        public String toString() {
            return name;
        }
    }
}
