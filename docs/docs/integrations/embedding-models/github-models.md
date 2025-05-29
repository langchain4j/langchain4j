---
sidebar_position: 6
---

# GitHub Models

:::note

This is the documentation for the `GitHub Models` integration, that uses the Azure AI Inference API to access GitHub Models.

LangChain4j provides 4 different integrations with OpenAI for using embedding models, and this is #4 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).
- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.
- [GitHub Models](/integrations/language-models/github-models) uses the Azure AI Inference API to access GitHub Models.

:::

If you want to develop a generative AI application, you can use GitHub Models to find and experiment with AI models for free.
Once you are ready to bring your application to production, you can switch to a token from a paid Azure account.

## GitHub Models Documentation

- [GitHub Models Documentation](https://docs.github.com/en/github-models)
- [GitHub Models Marketplace](https://github.com/marketplace/models)

## Maven Dependency

### Plain Java

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-github-models</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

## GitHub token

To use GitHub Models, you need to use a GitHub token for authentication.

Token are created and managed in [GitHub Developer Settings > Personal access tokens](https://github.com/settings/tokens).

Once you have a token, you can set it as an environment variable and use it in your code:

```bash
export GITHUB_TOKEN="<your-github-token-goes-here>"
```

## Creating a `GitHubModelsEmbeddingModel` with a GitHub token

```java
GitHubModelsEmbeddingModel model = GitHubModelsEmbeddingModel.builder()
        .gitHubToken(System.getenv("GITHUB_TOKEN"))
        .modelName(TEXT_EMBEDDING_3_SMALL)
        .logRequestsAndResponses(true)
        .build();
```

This will create an instance of `GitHubModelsEmbeddingModel`.

## Using the model

```java
Response<Embedding> response = model.embed("Please embed this sentence.");
```

## Examples

- [GitHub Models Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/github-models-examples/src/main/java)
