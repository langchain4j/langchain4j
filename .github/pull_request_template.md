<!-- Thank you so much for your contribution! -->
<!-- Please fill in all the sections below. -->

<!-- Please open the PR as a draft initially. Once it is reviewed and approved, we will ask you to add documentation and examples. -->
<!-- Please note that PRs with breaking changes will be rejected. -->
<!-- Please note that PRs without tests will be rejected. -->

<!-- Please note that PRs will be reviewed based on the priority of the issues they address. -->
<!-- We ask for your patience. We are doing our best to review your PR as quickly as possible. -->
<!-- Please refrain from pinging and asking when it will be reviewed. Thank you for understanding! -->


## Issue
<!-- Please paste the link to the issue this PR is addressing. For example: https://github.com/langchain4j/langchain4j/issues/1012 -->


## Change
<!-- Please describe the changes you made. -->


## General checklist
<!-- Please double-check the following points and mark them like this: [X] -->
- [ ] There are no breaking changes
- [ ] I have added unit and integration tests for my change
- [ ] I have manually run all the unit and integration tests in the module I have added/changed, and they are all green
- [ ] I have manually run all the unit and integration tests in the [core](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-core) and [main](https://github.com/langchain4j/langchain4j/tree/main/langchain4j) modules, and they are all green
<!-- Before adding documentation and example(s) (below), please wait until the PR is reviewed and approved. -->
- [ ] I have added/updated the [documentation](https://github.com/langchain4j/langchain4j/tree/main/docs/docs)
- [ ] I have added an example in the [examples repo](https://github.com/langchain4j/langchain4j-examples) (only for "big" features)


## Checklist for adding new model integration
<!-- Please double-check the following points and mark them like this: [X] -->
- [ ] I have added my new module in the [BOM](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-bom/pom.xml)


## Checklist for adding new embedding store integration
<!-- Please double-check the following points and mark them like this: [X] -->
- [ ] I have added a `{NameOfIntegration}EmbeddingStoreIT` that extends from either `EmbeddingStoreIT` or `EmbeddingStoreWithFilteringIT`
- [ ] I have added my new module in the [BOM](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-bom/pom.xml)


## Checklist for changing existing embedding store integration
<!-- Please double-check the following points and mark them like this: [X] -->
- [ ] I have manually verified that the `{NameOfIntegration}EmbeddingStore` works correctly with the data persisted using the latest released version of LangChain4j
