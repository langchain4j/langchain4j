package dev.langchain4j.model.googleai.internal;

enum GeminiBlockReason {
    BLOCK_REASON_UNSPECIFIED,
    SAFETY,
    OTHER,
    BLOCKLIST,
    PROHIBITED_CONTENT
}
