# MockAgent Design

**Date:** 2026-06-22
**Module:** `artificial-intelligence/agent-cli`

## Goal

Provide a `MockAgent` that returns configurable canned responses without hitting any LLM API. Used in JUnit tests for deterministic assertions, and at runtime when the user selects the `mock` agent to experiment without real API calls.

## Context

`RunPromptCommand` autowires `Agent` and calls `agent.prompt(apiKey, baseUrl, Agent.SELECTED_AGENT, prompt)`. When the user runs `/agent mock`, `Agent.SELECTED_AGENT` is set to `"mock-agent"` (configured in `application.yml`). The `modelId` passed to `prompt()` therefore becomes `"mock-agent"` for all subsequent `/p` commands.

## Architecture

`MockAgent extends Agent`. It is annotated `@Primary @Component` so Spring injects it wherever `Agent` is required — including `RunPromptCommand`. For non-mock model IDs it delegates to `super.prompt()`, so real LLM agents continue to work without any change to `RunPromptCommand` or other callers.

## Class Design

**Package:** `com.orion.ai.agent.cli`

**Constructors:**

| Constructor | Used by |
|---|---|
| `MockAgent()` | Spring (no-arg, empty map + default fallback) |
| `MockAgent(Map<String, String> responses)` | JUnit tests |
| `MockAgent(Map<String, String> responses, String defaultResponse)` | JUnit tests needing a custom fallback |

**`prompt()` override logic:**

1. If `modelId.equals("mock-agent")` → look up `prompt` in the map. Return the mapped value, or `defaultResponse` if absent.
2. Otherwise → `return super.prompt(apiKey, baseUrl, modelId, prompt)`.

The `initialise()`, `validate()`, and OpenAI client code are never invoked for mock calls.

**Default fallback message:** `"This is a mock response. No matching response configured for this prompt."`

## Lookup Semantics

Exact string match on the full prompt. No fuzzy matching, no regex. This is intentional: tests should be explicit about what prompt they send, and runtime experiments get the fallback for anything not explicitly mapped.

## Spring Wiring

`@Primary` ensures Spring resolves the `Agent`-typed injection point to `MockAgent`. The no-arg constructor is used by Spring; map-based constructors are for programmatic instantiation only.

## Testing

A `MockAgentTest` class in `src/test/java` should cover:
- Returns mapped response for a known prompt when modelId is `"mock-agent"`
- Returns `defaultResponse` for an unknown prompt
- Returns custom `defaultResponse` when overridden via constructor
- Delegates to `super.prompt()` for a non-mock modelId (via spy or subclass override)
