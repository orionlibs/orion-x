package com.orion.sdk.factory;

import com.orion.sdk.exception.SDKClientException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for creating Resilience4J RetryConfig instances with various exponential backoff strategies.
 * <p>
 * Provides 6 preset strategies:
 * <ul>
 *   <li>EXPONENTIAL_SIMPLE: Basic exponential backoff without jitter</li>
 *   <li>EXPONENTIAL_CAPPED: Exponential backoff with max interval cap</li>
 *   <li>EXPONENTIAL_JITTERED: Exponential backoff with randomized jitter</li>
 *   <li>EXPONENTIAL_FULL: Exponential backoff with both jitter and cap (RECOMMENDED)</li>
 *   <li>EXPONENTIAL_AGGRESSIVE: Faster retry attempts with shorter intervals</li>
 *   <li>EXPONENTIAL_CONSERVATIVE: Slower retry attempts with longer intervals</li>
 * </ul>
 */
public class SDKRetryConfigBuilder
{
    private static final Logger logger = LoggerFactory.getLogger(SDKRetryConfigBuilder.class);
    private static final SecureRandom secureRandom = new SecureRandom();
    private int maxAttempts = 3;
    private Duration initialInterval = Duration.ofMillis(100);
    private double multiplier = 2.0;
    private Duration maxInterval = Duration.ofSeconds(1);
    private BackoffStrategy strategy = BackoffStrategy.EXPONENTIAL_FULL;
    private Predicate<Throwable> retryOnExceptionPredicate = throwable -> {
        // Check if it's a SDKClientException with retryable flag
        if(throwable instanceof SDKClientException connectorException)
        {
            return connectorException.isRetryable();
        }
        // Check if wrapped in IllegalStateException (for backward compatibility with existing error wrapping)
        if(throwable instanceof IllegalStateException && throwable.getCause() instanceof SDKClientException cause)
        {
            return cause.isRetryable();
        }
        // For other exceptions (network errors, timeouts, unknown errors), retry
        return true;
    };


    public SDKRetryConfigBuilder()
    {
        // Intentionally empty - default values are set via field initializers above (lines 76-94)
        // This provides a clean public no-arg constructor while maintaining default configuration.
    }


    public static SDKRetryConfigBuilder withStrategy(BackoffStrategy strategy)
    {
        SDKRetryConfigBuilder builder = new SDKRetryConfigBuilder();
        builder.strategy = strategy;
        switch(strategy)
        {
            case EXPONENTIAL_AGGRESSIVE:
                builder.initialInterval = Duration.ofMillis(50);
                builder.multiplier = 1.5;
                builder.maxInterval = Duration.ofMillis(500);
                builder.maxAttempts = 5;
                break;
            case EXPONENTIAL_CONSERVATIVE:
                builder.initialInterval = Duration.ofMillis(500);
                builder.multiplier = 3.0;
                builder.maxInterval = Duration.ofSeconds(30);
                builder.maxAttempts = 3;
                break;
            case EXPONENTIAL_SIMPLE:
            case EXPONENTIAL_CAPPED:
            case EXPONENTIAL_JITTERED:
            case EXPONENTIAL_FULL:
            default:
                // Use defaults: 100ms initial, 2x multiplier, 1s max, 3 attempts
                break;
        }
        return builder;
    }


    public static RetryConfig defaultConfig()
    {
        return new SDKRetryConfigBuilder().build();
    }


    public static RetryConfig aggressiveConfig()
    {
        return withStrategy(BackoffStrategy.EXPONENTIAL_AGGRESSIVE).build();
    }


    public static RetryConfig conservativeConfig()
    {
        return withStrategy(BackoffStrategy.EXPONENTIAL_CONSERVATIVE).build();
    }


    public SDKRetryConfigBuilder maxAttempts(int maxAttempts)
    {
        if(maxAttempts < 1)
        {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        return this;
    }


    public SDKRetryConfigBuilder initialInterval(Duration initialInterval)
    {
        if(initialInterval.isNegative() || initialInterval.isZero())
        {
            throw new IllegalArgumentException("initialInterval must be positive");
        }
        this.initialInterval = initialInterval;
        return this;
    }


    public SDKRetryConfigBuilder multiplier(double multiplier)
    {
        if(multiplier <= 1.0)
        {
            throw new IllegalArgumentException("multiplier must be > 1.0");
        }
        this.multiplier = multiplier;
        return this;
    }


    public SDKRetryConfigBuilder maxInterval(Duration maxInterval)
    {
        if(maxInterval.isNegative() || maxInterval.isZero())
        {
            throw new IllegalArgumentException("maxInterval must be positive");
        }
        this.maxInterval = maxInterval;
        return this;
    }


    public SDKRetryConfigBuilder strategy(BackoffStrategy strategy)
    {
        this.strategy = strategy;
        return this;
    }


    public SDKRetryConfigBuilder retryOnExceptionPredicate(Predicate<Throwable> retryOnExceptionPredicate)
    {
        this.retryOnExceptionPredicate = retryOnExceptionPredicate;
        return this;
    }


    @SafeVarargs
    public final SDKRetryConfigBuilder retryOnExceptions(Class<? extends Throwable>... exceptionClasses)
    {
        this.retryOnExceptionPredicate = throwable -> {
            for(Class<? extends Throwable> exceptionClass : exceptionClasses)
            {
                if(exceptionClass.isInstance(throwable))
                {
                    return true;
                }
            }
            return false;
        };
        return this;
    }


    public RetryConfig build()
    {
        IntervalFunction intervalFunction = createIntervalFunction();
        RetryConfig config = RetryConfig.custom()
                                        .maxAttempts(maxAttempts)
                                        .intervalFunction(intervalFunction)
                                        .retryOnException(retryOnExceptionPredicate)
                                        .build();
        logger.debug("Created RetryConfig with strategy={}, maxAttempts={}, initialInterval={}, multiplier={}, maxInterval={}", strategy, maxAttempts, initialInterval.toMillis(), multiplier, maxInterval.toMillis());
        return config;
    }


    private IntervalFunction createIntervalFunction()
    {
        long initialIntervalMillis = initialInterval.toMillis();
        long maxIntervalMillis = maxInterval.toMillis();
        return switch(strategy)
        {
            case EXPONENTIAL_SIMPLE -> IntervalFunction.ofExponentialBackoff(initialIntervalMillis, multiplier);
            case EXPONENTIAL_CAPPED -> attempt -> {
                long exponential = (long)(initialIntervalMillis * Math.pow(multiplier, (double)attempt - 1));
                return Math.min(exponential, maxIntervalMillis);
            };
            case EXPONENTIAL_JITTERED -> IntervalFunction.ofExponentialRandomBackoff(initialIntervalMillis, multiplier);
            case EXPONENTIAL_FULL -> attempt -> {
                long exponential = (long)(initialIntervalMillis * Math.pow(multiplier, (double)attempt - 1));
                long capped = Math.min(exponential, maxIntervalMillis);
                // Add random jitter (0.5 to 1.5 times the interval)
                double jitter = 0.5 + secureRandom.nextDouble();
                return (long)(capped * jitter);
            };
            case EXPONENTIAL_AGGRESSIVE -> attempt -> {
                long exponential = (long)(initialIntervalMillis * Math.pow(multiplier, (double)attempt - 1));
                long capped = Math.min(exponential, maxIntervalMillis);
                double jitter = 0.5 + secureRandom.nextDouble();
                return (long)(capped * jitter);
            };
            case EXPONENTIAL_CONSERVATIVE -> attempt -> {
                long exponential = (long)(initialIntervalMillis * Math.pow(multiplier, (double)attempt - 1));
                long capped = Math.min(exponential, maxIntervalMillis);
                double jitter = 0.5 + secureRandom.nextDouble();
                return (long)(capped * jitter);
            };
        };
    }


    public enum BackoffStrategy
    {
        /**
         * Basic exponential backoff: interval * (multiplier ^ attempt)
         * Example: 100ms, 200ms, 400ms, 800ms, 1600ms
         */
        EXPONENTIAL_SIMPLE,
        /**
         * Exponential backoff with maximum interval cap.
         * Example: 100ms, 200ms, 400ms, 800ms, 1000ms (capped at maxInterval)
         */
        EXPONENTIAL_CAPPED,
        /**
         * Exponential backoff with randomized jitter to prevent thundering herd.
         * Example: 100ms, ~180ms, ~380ms, ~750ms, ~1450ms (randomized)
         */
        EXPONENTIAL_JITTERED,
        /**
         * Exponential backoff with both jitter and cap (RECOMMENDED).
         * Combines benefits of EXPONENTIAL_CAPPED and EXPONENTIAL_JITTERED.
         * Example: 100ms, ~190ms, ~390ms, ~780ms, ~1000ms (jittered and capped)
         */
        EXPONENTIAL_FULL,
        /**
         * Aggressive strategy with faster retry attempts.
         * Initial: 50ms, Multiplier: 1.5, Max: 500ms
         */
        EXPONENTIAL_AGGRESSIVE,
        /**
         * Conservative strategy with slower retry attempts.
         * Initial: 500ms, Multiplier: 3, Max: 30000ms
         */
        EXPONENTIAL_CONSERVATIVE
    }
}
