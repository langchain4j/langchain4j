# LangChain4j :: OpenAI Realtime

Inbound WebSocket gateway that speaks OpenAI Realtime–shaped events, with an outbound
WebSocket to OpenAI Realtime. Tools are registered at build time; the gateway runs the
function-call loop on the server.

```
Browser ── inbound WS (OpenAI-like events) ──► Gateway ── outbound WS ──► OpenAI Realtime
```

## Differences from a pure OpenAI Realtime client

1. **Tools schema is authoritative from the app registry**  
   Schema and executors come from `ToolSpecification` + `ToolExecutor` pairs registered when
   building the gateway. The client may omit `session.update.session.tools` to enable the
   **full** registry. If the client sends a tools list, it is treated as a **name whitelist**
   only; schemas are still taken from the registry. Unknown tool names fail that
   `session.update` (downlink `error`; outbound is not updated with a partial tools set).

2. **Function-call loop runs on the server**  
   On outbound `response.done` (or equivalent) with `function_call` items, the gateway
   executes registered tools, sends `conversation.item.create` (`function_call_output`),
   then a single `response.create`. Clients must not send `function_call_output`.

3. **Inbound Bearer is used for outbound OpenAI (mode B)**  
   The inbound `Authorization: Bearer <openai-api-key-or-ek_...>` header is forwarded as-is
   on the outbound Realtime connection. Suitable for local/dev and trusted networks.

4. **Barge-in is client-driven**  
   - **Stop playback**: when the client receives `input_audio_buffer.speech_started`, it
     should clear local audio playback.  
   - **Truncate**: the client sends `conversation.item.truncate` (it knows how much audio
     was already played). The gateway does **not** auto-truncate.

Either side disconnecting closes the other (symmetric session lifecycle).

## Minimal example

```java
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.openai.realtime.OpenAiRealtimeGateway;

public class WeatherTools {
    @Tool("Get the weather for a city")
    public String getWeather(String city) {
        return "sunny in " + city;
    }
}

public class Main {
    public static void main(String[] args) throws Exception {
        try (OpenAiRealtimeGateway gateway = OpenAiRealtimeGateway.builder()
                .host("127.0.0.1")
                .port(8080)
                .tools(new WeatherTools())
                .build()) {
            gateway.start();
            System.out.println("Connect WS clients to " + gateway.wsUri());
            // Client: Authorization: Bearer <OPENAI_API_KEY>
            // Client: send session.update (tools optional), then audio / response.create
            Thread.currentThread().join();
        }
    }
}
```

WebSocket client (sketch):

```text
ws://127.0.0.1:8080/
Authorization: Bearer sk-...

→ {"type":"session.update","session":{"type":"realtime","instructions":"..."}}
→ {"type":"input_audio_buffer.append","audio":"<base64 pcm>..."}
→ {"type":"input_audio_buffer.commit"}
→ {"type":"response.create"}
```

## Optional integration test

`OpenAiRealtimeSessionIT` is run by Failsafe (`verify`) and only when `OPENAI_API_KEY` is set;
otherwise it is skipped. Unit tests:

```bash
./mvnw -pl langchain4j-open-ai-realtime -am -Denforcer.skip=true test
```
