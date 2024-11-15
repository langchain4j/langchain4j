package dev.langchain4j.llama3;

public record RequestResponse(int totalTokens, String completion) {
}
