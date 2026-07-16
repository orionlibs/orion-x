package com.orion.ai.agent.cli.core.agent;

public class AgentReply
{
    public boolean shouldStop;
    public String answer;


    static AgentReply stop(String answer)
    {
        AgentReply reply = new AgentReply();
        reply.shouldStop = true;
        reply.answer = answer;
        return reply;
    }


    static AgentReply keepGoing()
    {
        AgentReply reply = new AgentReply();
        reply.shouldStop = false;
        return reply;
    }
}
