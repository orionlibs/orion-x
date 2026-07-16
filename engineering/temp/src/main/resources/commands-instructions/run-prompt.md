# /p

## Description

Sends a prompt to the currently selected agent and returns the response. The entire input after `/p` is treated as the prompt text.

## Usage

```
/p <prompt>
```

## Parameters

| Name   | Short | Type   | Required | Description                        |
|--------|-------|--------|----------|------------------------------------|
| prompt | —     | String | Yes      | The prompt text to send to the agent |

## Returns

The agent's response as a string.

## Example

```
> /p What is the capital of France?
Paris is the capital of France.

> /p Summarize the following in one sentence: The sky is blue because of Rayleigh scattering.
The sky appears blue due to the scattering of sunlight by atmospheric particles.
```
