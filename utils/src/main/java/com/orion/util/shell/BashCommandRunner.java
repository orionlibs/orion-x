package com.orion.util.shell;

import java.io.BufferedReader;
import java.io.IOException;

public class BashCommandRunner
{
    public static String run(String command)
    {
        try
        {
            Process process = new ProcessBuilder("/bin/sh", "-c", command)
                            .redirectErrorStream(true)
                            .start();
            process.waitFor();
            try(BufferedReader reader = process.inputReader())
            {
                return String.join("\n", reader.readAllLines());
            }
        }
        catch(IOException | InterruptedException e)
        {
            return "Fatal exception: " + e.getMessage();
        }
    }
}