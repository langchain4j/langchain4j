---
sidebar_position: 34.5
---

# Evaluation

Evaluation measures how good the answers of your application are: how often they are correct, based on the right
sources and helpful, over many realistic inputs, ideally taken from real use. You evaluate when something changes that
can affect the answers, for example a new model version, a new prompt or new content for retrieval, and you compare the
results over time.

## Testing or evaluation?

Testing and evaluation answer different questions. [Testing](/tutorials/testing) checks that your code works.
Evaluation measures how good the answers are.

| | Testing | Evaluation |
|---|---|---|
| Question | Does my code work? | How good are the answers? |
| When | While you develop and on every build | When something that affects the answers changes, and on data from real use |
| Model | Mostly a mock, a real model in a few integration tests | The real model of your application |
| Data | A few cases you write | Many realistic inputs, often from real use |
| Result | Pass or fail | Scores and pass rates, compared over time |
| Cost | Mostly fast and cheap | Slower, and every model call costs money |

Start with testing: it is useful from the first day of a project. Evaluation becomes important as your application
gets closer to production and real users.

## Tools

The [judges](/tutorials/testing#judging-answers-with-a-model) of the `langchain4j-test` module check an answer against
criteria written in natural language. `Judge.evaluate(...)` returns their verdicts as values, so you can also use them
to score answers in an evaluation.

In a Quarkus application, Quarkus LangChain4j has its own evaluation support: see
[Testing AI-Infused Applications](https://docs.quarkiverse.io/quarkus-langchain4j/dev/testing.html).

## Recommended reading

- [Your AI Product Needs Evals](https://hamel.dev/blog/posts/evals/)
- [Creating a LLM-as-a-Judge That Drives Business Results](https://hamel.dev/blog/posts/llm-judge/)
- [A Practical Guide to RAG Pipeline Evaluation (Part 1: Retrieval)](https://medium.com/relari/a-practical-guide-to-rag-pipeline-evaluation-part-1-27a472b09893)
- [A Practical Guide to RAG Pipeline Evaluation (Part 2: Generation)](https://medium.com/relari/a-practical-guide-to-rag-evaluation-part-2-generation-c79b1bde0f5d)
- [How important is a Golden Dataset for LLM evaluation?](https://medium.com/relari/how-important-is-a-golden-dataset-for-llm-pipeline-evaluation-4ef6deb14dc5)
- [Case Study: Reference-free vs Reference-based evaluation of RAG pipeline](https://medium.com/relari/case-study-reference-free-vs-reference-based-evaluation-of-rag-pipeline-9a49ef49866c)
- [How to evaluate complex GenAI Apps: a granular approach](https://medium.com/relari/how-to-evaluate-complex-genai-apps-a-granular-approach-0ab929d5b3e2)
- [Generate Synthetic Data to Test LLM Applications](https://medium.com/relari/generate-synthetic-data-to-test-llm-applications-4bffeb51b80e)
