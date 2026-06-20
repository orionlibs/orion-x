package com.orion.ai.agent.cli.command.agent;

import java.io.PrintWriter;
import java.util.List;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public class InteractiveSelectorInShell
{
    private final Terminal terminal;
    private final LineReader lineReader;


    public InteractiveSelectorInShell(Terminal terminal, LineReader lineReader)
    {
        this.terminal = terminal;
        this.lineReader = lineReader;
    }


    public String select(String title, List<String> options)
    {
        int maxOptionLen = options.stream().mapToInt(String::length).max().orElse(0);
        int innerWidth = Math.max(maxOptionLen + 8, title.length() + 9);
        PrintWriter writer = terminal.writer();
        writer.println();
        writer.println(dim(buildHeader(title, innerWidth)));
        for(int i = 0; i < options.size(); i++)
        {
            String option = options.get(i);
            String label = "  " + (i + 1) + ".  " + option;
            int pad = innerWidth - 6 - option.length();
            writer.println(dim("  │") + label + " ".repeat(pad) + dim("│"));
        }
        writer.println(dim("  └" + "─".repeat(innerWidth) + "┘"));
        writer.flush();
        String input = lineReader.readLine("  > ").trim();
        return resolve(input, options);
    }


    private String buildHeader(String title, int innerWidth)
    {
        int totalDashes = innerWidth - title.length() - 2;
        int leftDashes = totalDashes / 2;
        int rightDashes = totalDashes - leftDashes;
        return "  ┌" + "─".repeat(leftDashes) + " " + title + " " + "─".repeat(rightDashes) + "┐";
    }


    private String resolve(String input, List<String> options)
    {
        try
        {
            int index = Integer.parseInt(input) - 1;
            if(index >= 0 && index < options.size())
            {
                return options.get(index);
            }
        }
        catch(NumberFormatException ignored)
        {
        }
        return options.stream()
                      .filter(o -> o.equalsIgnoreCase(input))
                      .findFirst()
                      .orElse(null);
    }


    private String dim(String text)
    {
        return new AttributedStringBuilder().style(AttributedStyle.DEFAULT.faint())
                                            .append(text)
                                            .style(AttributedStyle.DEFAULT)
                                            .toAnsi();
    }
}
