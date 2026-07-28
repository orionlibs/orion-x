package com.orion.engineering.project.sdk.factory;

import com.orion.engineering.project.sdk.SDKFeignClient;
import com.orion.engineering_util.api.payload.APIResponse;
import com.orion.sdk.decoder.SDKErrorDecoder;
import com.orion.sdk.exception.SDKClientException;
import com.orion.sdk.factory.SDKRetryConfigBuilder;
import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.jackson3.Jackson3Decoder;
import feign.jackson3.Jackson3Encoder;
import feign.slf4j.Slf4jLogger;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Factory for creating SDKFeignClient instances with full configuration.
 * <p>
 * Features:
 * <ul>
 *   <li>Resilience4J retry support with configurable backoff strategies</li>
 *   <li>Custom error decoding with SDKClientException</li>
 *   <li>Jackson JSON encoding/decoding with Java 8 time support</li>
 *   <li>Configurable timeouts and connection settings</li>
 *   <li>SLF4J logging integration</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Simple usage with defaults
 * SDKFeignClient client = SDKFeignClientFactory
 *     .builder()
 *     .baseUrl("http://localhost:8080")
 *     .build()
 *     .create();
 *
 * // Advanced usage with custom retry
 * RetryConfig retryConfig = RetryConfigBuilder
 *     .withStrategy(BackoffStrategy.EXPONENTIAL_FULL)
 *     .maxAttempts(5)
 *     .build();
 *
 * SDKFeignClient client = SDKFeignClientFactory
 *     .builder()
 *     .baseUrl("http://localhost:8080")
 *     .retryConfig(retryConfig)
 *     .connectTimeout(Duration.ofSeconds(5))
 *     .readTimeout(Duration.ofSeconds(30))
 *     .logLevel(Logger.Level.FULL)
 *     .build()
 *     .create();
 * }</pre>
 */
public class SDKFeignClientFactory
{
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SDKFeignClientFactory.class);
    private final String baseUrl;
    private final RetryConfig retryConfig;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Logger.Level logLevel;
    private final JsonMapper objectMapper;


    private SDKFeignClientFactory(Builder builder)
    {
        this.baseUrl = builder.baseUrl;
        this.retryConfig = builder.retryConfig;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.logLevel = builder.logLevel;
        this.objectMapper = builder.objectMapper;
    }


    public static Builder builder()
    {
        return new Builder();
    }


    public static SDKFeignClient createSimple(String baseUrl)
    {
        return builder().baseUrl(baseUrl).build().create();
    }


    public static SDKFeignClient createWithRetry(String baseUrl, SDKRetryConfigBuilder.BackoffStrategy backoffStrategy)
    {
        RetryConfig retryConfig = SDKRetryConfigBuilder.withStrategy(backoffStrategy).build();
        return builder().baseUrl(baseUrl).retryConfig(retryConfig).build().create();
    }


    public SDKFeignClient create()
    {
        logger.info("Creating SDKFeignClient for baseUrl={}, connectTimeout={}, readTimeout={}", baseUrl, connectTimeout, readTimeout);
        Feign.Builder feignBuilder = Feign.builder()
                                          .encoder(new Jackson3Encoder(objectMapper))
                                          .decoder(new Jackson3Decoder(objectMapper))
                                          .errorDecoder(new SDKErrorDecoder())
                                          .logger(new Slf4jLogger(SDKFeignClient.class))
                                          .logLevel(logLevel)
                                          .retryer(Retryer.NEVER_RETRY) // We use Resilience4J for retry
                                          .options(new Request.Options(connectTimeout.toMillis(),
                                                                       TimeUnit.MILLISECONDS,
                                                                       readTimeout.toMillis(), TimeUnit.MILLISECONDS,
                                                                       true // followRedirects
                                          ));
        SDKFeignClient client = feignBuilder.target(SDKFeignClient.class, baseUrl);
        // Wrap with Resilience4J retry if configured
        if(retryConfig != null)
        {
            logger.info("Wrapping client with Resilience4J retry: maxAttempts={}", retryConfig.getMaxAttempts());
            return wrapWithRetry(client, retryConfig);
        }
        return client;
    }


    private SDKFeignClient wrapWithRetry(SDKFeignClient client, RetryConfig retryConfig)
    {
        Retry retry = Retry.of("sdk-client", retryConfig);
        // Add event listeners for debugging
        retry.getEventPublisher()
             .onRetry(event -> logger.warn("Retry attempt #{} for {}: {}",
                             event.getNumberOfRetryAttempts(),
                             event.getName(),
                             event.getLastThrowable().getMessage()))
             .onError(event -> logger.error("All retry attempts exhausted for {}: {}",
                             event.getName(),
                             event.getLastThrowable().getMessage()));
        return new SDKFeignClient()
        {
            @Override
            public APIResponse getProjectsSummaries(Map<String, String> headers)
            {
                return executeWithRetry(() -> client.getProjectsSummaries(headers), retry);
            }


            @Override
            public APIResponse getNumberOfProjects(Map<String, String> headers)
            {
                return executeWithRetry(() -> client.getNumberOfProjects(headers), retry);
            }


            /*@Override
            public APIResponse createProject(CreateProjectRequest request)
            {
                return executeWithRetry(() -> client.createProject(request), retry);
            }*/
        };
    }


    private <T> T executeWithRetry(Supplier<T> supplier, Retry retry)
    {
        return retry.executeSupplier(() -> {
            try
            {
                return supplier.get();
            }
            catch(SDKClientException e)
            {
                // Only retry if the error is retryable
                if(e.isRetryable())
                {
                    throw e;
                }
                else
                {
                    // Wrap in RuntimeException to stop retry
                    throw new IllegalStateException("Non-retryable error", e);
                }
            }
        });
    }


    public static class Builder
    {
        private String baseUrl;
        private RetryConfig retryConfig = SDKRetryConfigBuilder.defaultConfig();
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Logger.Level logLevel = Logger.Level.BASIC;
        private JsonMapper objectMapper = createDefaultObjectMapper();


        private Builder()
        {
        }


        private static JsonMapper createDefaultObjectMapper()
        {
            return JsonMapper.builder()
                             .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                             .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                             .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                             .build();
        }


        public Builder baseUrl(String baseUrl)
        {
            this.baseUrl = baseUrl;
            return this;
        }


        public Builder retryConfig(RetryConfig retryConfig)
        {
            this.retryConfig = retryConfig;
            return this;
        }


        public Builder noRetry()
        {
            this.retryConfig = null;
            return this;
        }


        public Builder connectTimeout(Duration connectTimeout)
        {
            this.connectTimeout = connectTimeout;
            return this;
        }


        public Builder readTimeout(Duration readTimeout)
        {
            this.readTimeout = readTimeout;
            return this;
        }


        public Builder logLevel(Logger.Level logLevel)
        {
            this.logLevel = logLevel;
            return this;
        }


        public Builder objectMapper(@Qualifier("sdkObjectMapper") JsonMapper objectMapper)
        {
            this.objectMapper = objectMapper;
            return this;
        }


        public SDKFeignClientFactory build()
        {
            if(baseUrl == null || baseUrl.isEmpty())
            {
                throw new IllegalStateException("baseUrl is required");
            }
            return new SDKFeignClientFactory(this);
        }
    }
}
