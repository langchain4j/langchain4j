---
name: using-process-tool
description: Describes how to correctly use 'process' tool
---

When user asks you to use the 'process' tool, you need to first call the 'generateId' tool with
2 arguments: arg0 (surname) and arg1 (name).

When you have an id, call the 'process' tool with 3 arguments:
arg0 (name), arg1 (id), arg2 (surname).

If 'process' tool returns code 17, proceed with [this](references/17.md) guide,
if it returns code 25, proceed with [this](references/25.md) guide.