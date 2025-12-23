package dev.langchain4j.http.client;

public record MultipartFile(String filename, String contentType, byte[] content) {}
