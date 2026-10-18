---
sidebar_position: 20
---

# Latest Release Notes
Please find the latest release notes [here](https://github.com/langchain4j/langchain4j/releases).

## Unreleased

### Changed

- Reactive AI Service streams without `ChatMemory` now use a fresh `invocationId` for each subsequent subscription,
  instead of reusing the original ID. The first subscription retains the ID created when the AI Service method was called.
  See [#6745](https://github.com/langchain4j/langchain4j/issues/6745).
