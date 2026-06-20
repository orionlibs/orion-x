# log.info

## Description

Logs a message at INFO level and returns it.

## Usage

```
log.info --message <message>
log.info -m <message>
```

## Parameters

| Name      | Short | Type   | Required | Description        |
|-----------|-------|--------|----------|--------------------|
| --message | -m    | String | Yes      | The message to log |

## Returns

The message string that was logged.

## Example

```
> log.info --message "Hello, world!"
Hello, world!

> log.info -m "Deployment started"
Deployment started
```
