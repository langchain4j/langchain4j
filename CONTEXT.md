# Decision models

**Decision model:** A model that evaluates a supplied state against one or more typed questions and returns corresponding answers. _Avoid:_ Structured decision model; that name can be confused with structured output from a chat model.

**Decision request:** The state, named questions, and optional request parameters submitted together to a decision model. _Avoid:_ Structured decision request.

**Decision response:** The named answers and response metadata returned by a decision model. _Avoid:_ Structured decision response.

**System One:** A wire protocol that an adapter may use to serve decision requests. It is not the name of the general model abstraction.
