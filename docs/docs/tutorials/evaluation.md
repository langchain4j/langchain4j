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
| Result | Pass or fail for each behavior you check | Scores and pass rates over many realistic inputs, compared over time |
| Cost | Mostly fast and cheap | Slower, and every model call costs money |

Start with testing: it is useful from the first day of a project. Evaluation becomes important as your application
gets closer to production and real users.

## Repeated runs

The same input can get a good answer in one run and a bad one in the next. So evaluate each input several times, and
look at how often its answers are good:

- **Choose the required pass rate with a margin.** The number of passes varies between runs even when nothing changed.
  An application that answers well 9 times out of 10 on average gets fewer than 9 good answers out of 10 in about one
  run out of four, and fewer than 8 in about one out of fourteen. Require fewer passes than you usually see, or run
  more times.
- **Small differences are usually chance.** With few runs, a small difference between two pass rates says little. Run
  more times before you decide that a new model version, prompt or retrieval setup is better or worse.
- **Keep the runs independent.** An AI Service with [chat memory](/tutorials/chat-memory) must start a new
  conversation in each run, for example with a new memory id.
- **Check the judge first.** When a model judges the answers, run it on a few answers that you labelled yourself,
  including bad ones, and see whether it agrees with you.

## Tools

In a Quarkus application, Quarkus LangChain4j has its own evaluation support, which runs each input several times
and reports the pass rates with a confidence interval: see
[Testing AI-Infused Applications](https://docs.quarkiverse.io/quarkus-langchain4j/dev/testing.html).

The [judges](/tutorials/testing#judging-answers-with-a-model) of the `langchain4j-test` module check an answer against
criteria written in natural language.
[`Judge.evaluate(...)`](https://docs.langchain4j.dev/apidocs/dev/langchain4j/test/judge/Judge.html) returns their
verdicts as values (`JudgeResponse.verdicts()`) instead of failing, so you can also use them to score answers. Judging
an answer this way adds at least one request to the judge model for each answer, and `evaluate(...)` throws a
`JudgeException` when the judge's answer cannot be understood.

## Recommended reading

- [Your AI Product Needs Evals](https://hamel.dev/blog/posts/evals/)
- [Creating a LLM-as-a-Judge That Drives Business Results](https://hamel.dev/blog/posts/llm-judge/)
- [A Practical Guide to RAG Pipeline Evaluation (Part 1: Retrieval)](https://medium.com/relari/a-practical-guide-to-rag-pipeline-evaluation-part-1-27a472b09893)
- [A Practical Guide to RAG Pipeline Evaluation (Part 2: Generation)](https://medium.com/relari/a-practical-guide-to-rag-evaluation-part-2-generation-c79b1bde0f5d)
- [How important is a Golden Dataset for LLM evaluation?](https://medium.com/relari/how-important-is-a-golden-dataset-for-llm-pipeline-evaluation-4ef6deb14dc5)
- [Case Study: Reference-free vs Reference-based evaluation of RAG pipeline](https://medium.com/relari/case-study-reference-free-vs-reference-based-evaluation-of-rag-pipeline-9a49ef49866c)
- [How to evaluate complex GenAI Apps: a granular approach](https://medium.com/relari/how-to-evaluate-complex-genai-apps-a-granular-approach-0ab929d5b3e2)
- [Generate Synthetic Data to Test LLM Applications](https://medium.com/relari/generate-synthetic-data-to-test-llm-applications-4bffeb51b80e)
