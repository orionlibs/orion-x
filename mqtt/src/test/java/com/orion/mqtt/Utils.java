package com.orion.mqtt;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Utils
{
    public static void nonblockingDelay(int numberOfSeconds)
    {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        CompletableFuture<Void> delayedTask = CompletableFuture.runAsync(() -> {
        }).thenCompose(ignored -> delay(numberOfSeconds, TimeUnit.SECONDS, scheduler));
        delayedTask.join(); //wait for the task to complete
        scheduler.shutdown();
    }


    public static void nonblockingDelay2(int numberOfSeconds)
    {
        long durationNanos = numberOfSeconds * 1_000_000_000L;
        long startTime = System.nanoTime();
        while(true)
        {
            long currentTime = System.nanoTime();
            long elapsedTime = currentTime - startTime;
            if(elapsedTime >= durationNanos)
            {
                break;
            }
            for(int i = 0; i < 10; i++)
            {
                Math.sin(i);
            }
        }
    }


    private static CompletableFuture<Void> delay(long delay, TimeUnit unit, ScheduledExecutorService scheduler)
    {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.schedule(() -> future.complete(null), delay, unit);
        return future;
    }
}
