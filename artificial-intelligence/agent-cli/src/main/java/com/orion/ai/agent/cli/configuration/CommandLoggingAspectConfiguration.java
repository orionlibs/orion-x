package com.orion.ai.agent.cli.configuration;

import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
public class CommandLoggingAspectConfiguration
{
    private static final Logger auditLog = LoggerFactory.getLogger("com.orion.ai.agent.cli.command.audit");


    @Around("@annotation(org.springframework.shell.core.command.annotation.Command)")
    public Object logCommand(ProceedingJoinPoint pjp) throws Throwable
    {
        MethodSignature signature = (MethodSignature)pjp.getSignature();
        String commandName = signature.getMethod().getAnnotation(Command.class).name()[0];
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] argValues = pjp.getArgs();
        String args = IntStream.range(0, argValues.length)
                               .mapToObj(i -> {
                                   Option option = parameters[i].getAnnotation(Option.class);
                                   String name = option != null ? option.longName() : "param" + i;
                                   String value = argValues[i] == null ? "null" : argValues[i].toString();
                                   return name + "=" + value;
                               })
                               .collect(Collectors.joining(", "));
        try
        {
            Object result = pjp.proceed();
            //auditLog.info("{} | command={} | args=[{}] | result={}", Instant.now(), commandName, args, result);
            return result;
        }
        catch(Throwable t)
        {
            auditLog.info("{} | command={} | args=[{}] | error={}", Instant.now(), commandName, args, t.getMessage());
            throw t;
        }
    }
}
