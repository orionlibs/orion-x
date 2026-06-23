# /agent

## Description

Selects the active agent to use for prompts. Without an argument, displays the currently selected agent and opens an interactive selector. With an agent ID, sets the agent directly.

## Usage

```
/agent
/agent --agent <agentId>
/agent -a <agentId>
```

## Parameters

| Name    | Short | Type   | Required | Description                              |
|---------|-------|--------|----------|------------------------------------------|
| --agent | -a    | String | No       | ID of the agent to select from config    |

## Returns

"Agent set to {agentId}" on success, or "Invalid selection" if the provided ID does not exist in configuration.

## Example

```
> /agent
Currently selected agent is gpt-4o
[interactive selector opens]
Agent set to claude-3-5-sonnet

> /agent --agent claude-3-5-sonnet
Agent set to claude-3-5-sonnet

> /agent -a unknown-id
Invalid selection
```
