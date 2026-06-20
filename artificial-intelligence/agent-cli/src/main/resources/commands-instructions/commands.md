# commands

## Description

Lists all available commands and their usage instructions.

## Usage

```
commands
```

## Parameters

None.

## Returns

A JSON object containing an array of command entries, each with the command name and its full usage instructions.

## Example

```
> commands
{"commands":[{"command":"commands","instructions":"..."},{"command":"log.info","instructions":"..."},{"command":"log.error","instructions":"..."},{"command":"math.random.from.0.to.1","instructions":"..."},{"command":"math.random.integer","instructions":"..."},{"command":"math.random.integer.except.0","instructions":"..."}]}
```
