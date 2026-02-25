# LangChain4j — ThingsBoard Fork

A fork of [LangChain4j](https://github.com/langchain4j/langchain4j) maintained by ThingsBoard, published under the `org.thingsboard.langchain4j` group ID. This is a Java library for integrating LLMs into applications.

## Conventions

### Commit messages

All ThingsBoard-specific commits must use the `[TB]` prefix in the commit message (e.g., `[TB] feat: add caching support`).

### Versioning

Versions follow the `{upstream_version}-TB{N}` scheme, where `{upstream_version}` is the base LangChain4j release and `{N}` is an incrementing number for each TB-specific release on top of it (e.g., `1.11.0-TB1`, `1.11.0-TB2`, `1.11.0-TB3`). The counter starts at 1 and resets to 1 when upgrading to a new upstream version.
