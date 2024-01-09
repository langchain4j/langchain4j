---
sidebar_position: 6
---

# 3. Chat (Memory)

In this example we will have a conversation with OpenAI's GPT-3.5 model. As the models themselves have no memory, the user has to take care of passing the former conversation along with every new prompt. 
Langchain4j provides convenient methods with good defaults that will take care of this for you ([Simple setup of ChatModel](chat#simple-setup-of-chatmodel)). 
If you want more control, you can specify all the parameters manually ([ChatModel with full control over parameters](chat#chatModel-with-full-control-over-parameters)).

First, set up you basic project with Java8+ and the desired langchain4j version as described in [Get Started](/docs/get-started).
Set up you OpenAI API key as described in [Get Started](/docs/get-started#write-a-hello-world-program).

### Simple setup of ChatModel

Coming soon

### ChatModel with full control over parameters

Coming soon


- [Chat memory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatMemoryExamples.java)
- [Persistent chat memory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithPersistentMemoryForEachUserExample.java)
