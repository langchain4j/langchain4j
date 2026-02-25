# LangChain4j — ThingsBoard Fork

This is a fork of [LangChain4j](https://github.com/langchain4j/langchain4j) maintained by [ThingsBoard](https://thingsboard.io/).

Published under the `org.thingsboard.langchain4j` group ID.

## Conventions

### Commit messages

All ThingsBoard-specific commits must use the `[TB]` prefix in the commit message (e.g., `[TB] feat: add caching support`).

### Versioning

Versions follow the `{upstream_version}-TB{N}` scheme, where `{upstream_version}` is the base LangChain4j release and `{N}` is an incrementing number for each TB-specific release on top of it (e.g., `1.11.0-TB1`, `1.11.0-TB2`, `1.11.0-TB3`). The counter starts at 1 and resets to 1 when upgrading to a new upstream version.

## Changes compared to upstream

### Gemini caching support
- Full integration with the Google Gemini cached content API — reuse large contexts across requests to save cost and latency (`a13c8fb`).
- Checksum-based deduplication to avoid re-caching identical content (`cd48ffb`).
- Caching of `systemInstruction` alongside user content (`ab27f35`).
- Handling of cached content duplicates (`55486d9`).
- TTL extension via PATCH instead of delete+recreate (`2982dfc`).

### Gemini enhancements
- Send original content parts back to the model when continuing a conversation (`df6179c`).
- VALIDATED function calling mode — Gemini validates tool call arguments against the schema before returning them (`7219c5f`).

### AiServices framework extensions
- Custom metadata support for `AiServices` and `Result` (token counts, model info, etc.) (`aa3063d`).
- Per-request `ResponseFormat` via `@Format` annotation — each AiService method can specify its own response format (`d6953f5`).

### Build & distribution
- Maven group ID changed to `org.thingsboard.langchain4j` (`d18c050`).
- Distribution management configured for ThingsBoard's Maven repository (`4075ddf`, `ae6de7e`, `615a46d`).
- Upstream GitHub workflows removed (TB uses its own CI) (`b79d357`).
