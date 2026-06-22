# MockAgent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `MockAgent` class that returns configurable canned responses without calling any LLM API, usable in JUnit tests and at runtime when the user selects the mock agent.

**Architecture:** `MockAgent extends Agent` and is annotated `@Primary @Component` so Spring injects it everywhere `Agent` is required. Its `prompt()` override intercepts calls with modelId `"mock-agent"` and looks up the response in a caller-supplied `Map<String, String>`; for any other modelId it delegates to `super.prompt()` unchanged, keeping real LLM agents functional.

**Tech Stack:** Java 26, Spring Boot, JUnit 5, AssertJ

## Global Constraints

- All tests must be run with Java 26: `JAVA_HOME=~/.sdkman/candidates/java/26-oracle mvn test -f artificial-intelligence/agent-cli/pom.xml`
- Package: `com.orion.ai.agent.cli`
- Follow the code style of surrounding files: Allman brace style, 4-space indentation, blank line between methods
- No Mockito — existing tests use plain instantiation; follow that pattern

---

### Task 1: MockAgent class and tests

**Files:**
- Create: `artificial-intelligence/agent-cli/src/main/java/com/orion/ai/agent/cli/MockAgent.java`
- Create: `artificial-intelligence/agent-cli/src/test/java/com/orion/ai/agent/cli/MockAgentTest.java`

**Interfaces:**
- Produces: `MockAgent(Map<String, String>)` and `MockAgent(Map<String, String>, String)` constructors for test/programmatic use; no-arg constructor for Spring
- Produces: `public String prompt(String apiKey, String baseUrl, String modelId, String prompt)` — overrides `Agent.prompt()`

- [ ] **Step 1: Write the failing tests**

Create `artificial-intelligence/agent-cli/src/test/java/com/orion/ai/agent/cli/MockAgentTest.java`:

```java
package com.orion.ai.agent.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MockAgentTest
{
    @Test
    void returnsMappedResponseForKnownPrompt()
    {
        MockAgent agent = new MockAgent(Map.of("list files", "file1.txt\nfile2.txt"));
        assertThat(agent.prompt("key", "url", "mock-agent", "list files"))
                        .isEqualTo("file1.txt\nfile2.txt");
    }


    @Test
    void returnsDefaultResponseForUnknownPrompt()
    {
        MockAgent agent = new MockAgent(Map.of("list files", "file1.txt"));
        assertThat(agent.prompt("key", "url", "mock-agent", "unknown prompt"))
                        .isEqualTo("This is a mock response. No matching response configured for this prompt.");
    }


    @Test
    void returnsCustomDefaultResponseWhenOverridden()
    {
        MockAgent agent = new MockAgent(Map.of(), "custom fallback");
        assertThat(agent.prompt("key", "url", "mock-agent", "anything"))
                        .isEqualTo("custom fallback");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
JAVA_HOME=~/.sdkman/candidates/java/26-oracle mvn test -f artificial-intelligence/agent-cli/pom.xml -Dtest=MockAgentTest 2>&1 | grep -E "Tests run|BUILD|ERROR" | grep -v "at "
```

Expected: compilation error — `MockAgent` does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `artificial-intelligence/agent-cli/src/main/java/com/orion/ai/agent/cli/MockAgent.java`:

```java
package com.orion.ai.agent.cli;

import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class MockAgent extends Agent
{
    private static final String DEFAULT_RESPONSE =
                    "This is a mock response. No matching response configured for this prompt.";

    private final Map<String, String> responses;
    private final String defaultResponse;


    public MockAgent()
    {
        this.responses = Map.of();
        this.defaultResponse = DEFAULT_RESPONSE;
    }


    public MockAgent(Map<String, String> responses)
    {
        this.responses = responses;
        this.defaultResponse = DEFAULT_RESPONSE;
    }


    public MockAgent(Map<String, String> responses, String defaultResponse)
    {
        this.responses = responses;
        this.defaultResponse = defaultResponse;
    }


    @Override
    public String prompt(String apiKey, String baseUrl, String modelId, String prompt)
    {
        if("mock-agent".equals(modelId))
        {
            return responses.getOrDefault(prompt, defaultResponse);
        }
        return super.prompt(apiKey, baseUrl, modelId, prompt);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
JAVA_HOME=~/.sdkman/candidates/java/26-oracle mvn test -f artificial-intelligence/agent-cli/pom.xml 2>&1 | grep -E "Tests run|BUILD"
```

Expected:
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.orion.ai.agent.cli.MockAgentTest
[INFO] Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
rtk git add artificial-intelligence/agent-cli/src/main/java/com/orion/ai/agent/cli/MockAgent.java artificial-intelligence/agent-cli/src/test/java/com/orion/ai/agent/cli/MockAgentTest.java && rtk git commit -m "feat: add MockAgent with configurable canned responses"
```
