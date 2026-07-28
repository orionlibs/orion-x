package com.orion.engineering.project.sdk;

import com.orion.engineering.project.sdk.factory.SDKFeignClientFactory;

/**
 * Main entry point for creating SDK Client instances.
 * <p>
 * This factory provides a simple, explicit way to create blocking clients
 * without Spring Boot auto-configuration magic. Consumers have full control over which
 * client type they want to use and how it's configured.
 * <p>
 * <b>Blocking Client (Framework-Independent):</b>
 * <pre>{@code
 * RetryConfig retryConfig = RetryConfigBuilder
 *     .withStrategy(BackoffStrategy.EXPONENTIAL_FULL)
 *     .maxAttempts(5)
 *     .build();
 *
 * SDKFeignClient client = SDKClientFactory
 *     .blocking()
 *     .baseUrl("http://localhost:8080")
 *     .retryConfig(retryConfig)
 *     .connectTimeout(Duration.ofSeconds(5))
 *     .readTimeout(Duration.ofSeconds(30))
 *     .build()
 *     .create();
 * }</pre>
 *
 * @see SDKFeignClientFactory
 */
public class SDKClientFactory
{
    private SDKClientFactory()
    {
        // Utility class, prevent instantiation
    }


    public static SDKFeignClientFactory.Builder blocking()
    {
        return SDKFeignClientFactory.builder();
    }


    public static SDKFeignClient createSimpleBlocking(String baseUrl)
    {
        return SDKFeignClientFactory.createSimple(baseUrl);
    }
}
