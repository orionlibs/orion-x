# run

## Description

Executes a bash command on the local machine and returns the combined stdout and stderr output.

## Usage

```
run --command <command>
run -c <command>
```

## Parameters

| Name      | Short | Type   | Required | Description                  |
|-----------|-------|--------|----------|------------------------------|
| --command | -c    | String | Yes      | The bash command to execute  |

## Returns

The combined stdout and stderr output of the command as a string.

## Notes

- Commands are executed via `/bin/sh -c`.
- Use double quotes as the outer delimiter in Spring Shell interactive mode. Use single quotes for inner quoting within the command.
- Avoid using escaped double quotes (`\"`) inside a double-quoted Spring Shell argument — Spring Shell's tokenizer does not handle them correctly.

## Example

```
> run --command "ls -la"
total 0
drwxr-xr-x  3 user  staff   96 Jan  1 00:00 .
...

> run -c "echo 'hello there' > /tmp/test.txt"

> run -c "cat /tmp/test.txt"
hello there
```
