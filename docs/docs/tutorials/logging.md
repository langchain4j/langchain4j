---
sidebar_position: 30
---

# 15. Logging

### Model requests and responses
Console output can be switched on and off by setting `.logRequests()` and `.logResponses()` on the model

```
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(ApiKeys.OPENAI_API_KEY)
                .logRequests(true)
                .logResponses(true)
                .build();
```

### Default logging: Tinylog
By default, we offer the Tinylog framework. An example can be found in langchain4j-examples/tutorials.
Logging properties are set in `tinylog.properties`, as follows
```
writer.level = info
```
Typical log level settings are `error`, `warn`, `info` and `debug`. 

An overview of all the options:
- `off`: No log messages will be written. This effectively disables logging.
- `trace`: All log messages, including trace, debug, info, warn, and error, will be written to the log output.
- `debug`: Log messages of debug, info, warn, and error levels will be written to the log output. Trace messages will be ignored.
- `info`: Log messages of info, warn, and error levels will be written to the log output. Debug and trace messages will be ignored.
- `warn`: Log messages of warn and error levels will be written to the log output. Info, debug, and trace messages will be ignored.
- `error`: Only log messages of error level will be written to the log output. Warn, info, debug, and trace messages will be ignored.
- `fatal`: This level is not part of the standard log levels in Tinylog. You can use it to specify a custom level for log messages. By default, it behaves the same as the `error` level.

Or you can choose to add you own logger.

## Spring Boot
In Spring Boot examples, logging properties are set in the `application.properties` file
```
logging.level.dev.langchain4j=INFO
logging.level.dev.ai4j.openai4j=INFO
```

_This documentation page is a stub - help us make it better_
