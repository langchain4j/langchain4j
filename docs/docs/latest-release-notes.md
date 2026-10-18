---
sidebar_position: 20
---

# Latest Release Notes
Please find the latest release notes [here](https://github.com/langchain4j/langchain4j/releases).

## Unreleased

- AI Service reactive streams now use a distinct `invocationId` for each subscription.
  The first subscription retains the original ID; later subscriptions receive new IDs.
  Listeners correlating events by `invocationId` will now observe separate invocations for
  re-subscriptions, including concurrent subscriptions and retries. See [#6745](https://github.com/langchain4j/langchain4j/issues/6745).
