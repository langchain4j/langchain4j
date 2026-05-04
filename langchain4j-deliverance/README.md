### Deliverance integration for langchain4j

[Deliverance](https://github.com/edwardcapriolo/deliverance) is a Java inference engine for local generation and embeddings.

This module integrates Deliverance with LangChain4j chat, language, streaming, and embedding model APIs.

Supported chat sampling controls include:

- `temperature`
- `topP`
- `maxOutputTokens`
- `stopSequences`

Tool calling is supported when the underlying Deliverance model and prompt template support it.
