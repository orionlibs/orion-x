# log.error

## Description

Logs a message at ERROR level and returns it.

## Usage

```
log.error --message <message>
log.error -m <message>
```

## Parameters

| Name      | Short | Type   | Required | Description        |
|-----------|-------|--------|----------|--------------------|
| --message | -m    | String | Yes      | The message to log |

## Returns

The message string that was logged.

## Example

```
> log.error --message "Something went wrong"
Something went wrong

> log.error -m "Connection timeout"
Connection timeout
```
