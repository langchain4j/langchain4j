# Open structured decisions and System One adapters

## Status and scope

This design replaces, rather than amends, the implementation proposed in core PR
[#6469](https://github.com/langchain4j/langchain4j/pull/6469) and community PR
[#797](https://github.com/langchain4j/langchain4j-community/pull/797). It produces two
new, cross-linked PRs: one experimental core abstraction and one community
implementation. The old PRs remain open. After the new PRs are published, we will
draft explanatory comments on the old PRs for the contributor to approve before
posting. Maintainers choose which PRs to merge or close.

Only servers that expose a System One-compatible `POST /v1/systemone` participate
in the common adapter. A provider's other operations may be exposed by its
concrete adapter without becoming part of `StructuredDecisionModel`. This work
includes the discussed CLM rank endpoint because CLM also serves System One.
It does not add a generic model for servers without that contract, nor a public
dialect or capability SPI.

## Core interface

The existing experimental artifact and `dev.langchain4j.model.structureddecision`
package remain the location of the provider-neutral interface. The model keeps
`decide(StructuredDecisionRequest)`, `decideAsync` with its unsupported default,
and default request parameters. Requests keep JSON-like or text `state`, ordered
named questions, optional `List<Content>`, and per-call parameters. Providers
reject unsupported content and parameters explicitly.

`Question` becomes a plain interface rather than a sealed one. The core ships
immutable `NoulQuestion`, `ChoiceQuestion`, and `ScoreQuestion`; a provider module
may implement additional question types. A provider never silently serializes an
unknown question type or passes it through as arbitrary JSON.

`StructuredDecisionAnswer` becomes a plain interface with `value()`,
`confidence()`, `confidenceProvenance()`, and `metadata()`. The core ships
immutable `NoulAnswer`, `ChoiceAnswer`, and `ScoreAnswer` with typed accessors
and covariant scalar `value()`. `StructuredDecisionResponse.answers()` remains a
named map, but its values are now the answer interface so a response can contain
standard and provider-specific answers in one call. This changes the draft
snapshot's source interface; downstream users such as OneRing must be compiled
and migrated against the replacement before compatibility is claimed.

Each answer exposes an immutable `Map<String, Object> metadata`, nullable
numeric `confidence`, and nullable `ConfidenceProvenance`. Provenance
distinguishes `PROVIDER_REPORTED` from `ADAPTER_DERIVED`; null is permitted only
when no confidence exists. The numeric value is not a cross-provider calibration
promise. For `noul`, the probability is its typed value, not an invented
confidence. Provider-specific meanings and distributions remain in metadata or
provider-specific answer types.

The response retains immutable top-level metadata. The community decoder keeps
`model`, `usage`, and any unknown top-level fields there, including KEV's
`latency_ms`; it does not promote that field to the original System One protocol.
Optional per-answer fields such as `probabilities` and `legend`, plus unknown
fields, remain accessible in that answer's metadata. JSON numbers, maps, and
lists are preserved without stringifying them.

## Decision receipt

`StructuredDecisionReceipt` is an explicit client-side snapshot made from a
request, its response, and an application-supplied schema ID and version. It
captures question names, types, ordered choice option IDs or score level IDs,
answer values, confidence, and provenance. It neither adds fields to the System
One request nor automatically logs or stores the state. Both schema values are
required; the library does not infer a version from question text. Construction
validates that every returned answer name belongs to the request; missing
answers remain distinguishable from returned answers. A receipt does not claim
that a provider's confidence was calibrated.

## Community adapters

The new public `SystemOneStructuredDecisionModel` in a dedicated community
module implements only the shared JSON System One path: named `noul`, `choice`,
and `score` questions over supported text or JSON state. Its builder requires a
base URL and optionally takes a bearer key, default model name, and HTTP client
builder. With no model configured, the `model` field is omitted. It rejects
attachments and unsupported per-call properties instead of dropping them. It
maps standard answers, top-level metadata, and per-answer metadata without loss
of the fields described above. A malformed or unknown answer type fails with a
clear error; unknown optional fields do not break decoding.

`TypeSafeStructuredDecisionModel` remains in its existing artifact and retains
its public class, builder methods, required API key, TypeSafe base URL, and
default model. It delegates shared wire handling to the common module while
retaining TypeSafe-specific validation. KEV and Laya are supported through the
configurable adapter; no named classes are created solely to change defaults.

`ClmStructuredDecisionModel` implements the common decision interface, accepts
typed `ClmRequestParameters` with an optional per-call temperature for
`/v1/systemone`, and exposes a typed
`rank(context, question, candidates)` operation for CLM's separate `/v1/rank`
endpoint. The ranking result is not a `StructuredDecisionResponse`; ranking
does not become a `Question` type. The adapter validates temperature and
candidate inputs and rejects unrelated extra request properties. The CLM
parameter type implements `StructuredDecisionRequestParameters` and keeps its
temperature when merged with adapter defaults. When present,
the `X-CLM-Latency-Ms` header is preserved verbatim as the string metadata
entry `clm_latency_ms`, separate from the System One JSON protocol.

Multimodal adapters accept inline `ImageContent` with base64 data and MIME types
`image/png`, `image/jpeg`, or `image/webp`. They do not fetch remote URLs
implicitly or reinterpret other `Content` types. The openjev adapter sends JSON
`images` data URLs and enforces its eight-image limit. The djev
adapter supports JSON data URLs and multipart; multipart uses a JSON field named
`request` and individual image file parts through the existing `HttpRequest`
form-data support. No client-side djev image-count limit is imposed where its
contract gives none; the server remains authoritative. The common adapter stays
text/JSON-only.

The djev adapter also supports provider-specific `SpanQuestion` and
`SpansQuestion` plus typed `SpanAnswer` and `SpansAnswer` through the same
`decide` method. The answer types retain found status, text, offsets, and
provider-specific diagnostics. Standard and span questions may be mixed in one
request and retain their names in one response. Unsupported djev schema
extensions, such as conditional question features, are rejected rather than
silently ignored; they are outside this design's scope.

Shared HTTP, serialization, and decoding behavior is internal to the community
implementation. No public inheritance base class, dialect registry, or generic
raw-JSON escape hatch is introduced. Each concrete adapter owns its supported
question types, parameters, content formats, and extra operations.

## Errors and compatibility

Unsupported questions, contents, and provider parameters raise
`UnsupportedFeatureException` before HTTP. Invalid local values raise the
repository's ordinary validation exceptions. HTTP errors retain the normal
LangChain4j transport behavior. A valid response with unknown optional JSON
must decode; a structurally invalid response must not be misreported as a
successful decision. The replacement core API is experimental and source-
incompatible with the draft `StructuredDecisionAnswer` class; TypeSafe and
consumer migration tests must demonstrate the transition explicitly.

## Verification and delivery

Use consumer-visible TDD: a failing test for each new public behavior, then
minimal implementation and a passing test. Core tests cover open third-party
questions and answers, immutable metadata, confidence provenance, ordered
receipts, and invalid receipts. Community tests use a fake HTTP client and
fixture responses for TypeSafe, KEV, CLM, Laya, openjev, and djev. They assert
request JSON or multipart parts, answer types and metadata, mixed djev batches,
rank responses, rejection before HTTP, and preservation of TypeSafe builder
defaults. No regular test requires a GPU or live provider.

Verify the experimental core Maven module with its dependencies, install the
core snapshot locally, then verify the affected community modules. The
community PR depends on an unpublished experimental core snapshot, so it may
need to remain draft or show a documented dependency-related CI limitation
until the core artifact is available. Do not claim a green hosted build based
only on a local snapshot. Check an OneRing consumer rebuild before claiming
source compatibility.

Create new branches from the existing PR work, without rewriting or closing the
existing PR branches. Publish two new, cross-linked PRs only after local
verification. Their descriptions explain the common System One seam and the
provider-specific capabilities. Once both URLs exist, show the user concise
draft comments for the old PRs. On approval, post comments linking the new PRs
and explaining that the new pair generalizes questions and answers, preserves
response data, supports System One-compatible local implementations and their
optional capabilities, and leaves merge/closure decisions to maintainers.
