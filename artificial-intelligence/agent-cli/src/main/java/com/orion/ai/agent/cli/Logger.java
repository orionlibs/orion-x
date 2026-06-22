package com.orion.ai.agent.cli;

public class Logger
{
    private final Level level = Level.INFO;


    public void debug(String message)
    {
        if(level != Level.DEBUG)
        {
            return;
        }
        System.out.println(message);
    }


    public void info(String message)
    {
        System.out.println(message);
    }


    public void error(String message)
    {
        System.err.println(message);
    }


    enum Level
    {DEBUG, INFO, ERROR}
}
