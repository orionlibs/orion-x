package com.orion.engineering.project.sdk.decoder;

import com.orion.engineering.project.sdk.exception.SDKClientException;
import com.orion.engineering.project.sdk.exception.SDKErrorType;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Error decoder for blocking Feign client that converts HTTP errors to SDKClientException.
 * <p>
 * This decoder:
 * <ul>
 *   <li>Extracts HTTP status code and response body</li>
 *   <li>Determines error type with retryability logic</li>
 *   <li>Creates SDKClientException with full error details</li>
 *   <li>Logs error information for debugging</li>
 * </ul>
 */
public class SDKErrorDecoder implements ErrorDecoder
{
    private static final Logger logger = LoggerFactory.getLogger(SDKErrorDecoder.class);
    private static final int MAX_RESPONSE_BODY_LENGTH = 1000;


    @Override
    public Exception decode(String methodKey, Response response)
    {
        int statusCode = response.status();
        String requestUrl = response.request().url();
        String requestMethod = response.request().httpMethod().name();
        String responseBody = null;
        try
        {
            if(response.body() != null)
            {
                byte[] bodyBytes = response.body().asInputStream().readAllBytes();
                responseBody = new String(bodyBytes, StandardCharsets.UTF_8);
                if(responseBody.length() > MAX_RESPONSE_BODY_LENGTH)
                {
                    responseBody = responseBody.substring(0, MAX_RESPONSE_BODY_LENGTH) + "... (truncated)";
                }
            }
        }
        catch(IOException e)
        {
            logger.warn("Failed to read response body for {} {}: {}", requestMethod, requestUrl, e.getMessage());
        }
        // Determine error type
        SDKErrorType errorType = SDKErrorType.fromStatusCode(statusCode);
        // Log error
        logger.error("Connector client error: {} {} returned {} ({}), retryable={}", requestMethod, requestUrl, statusCode, errorType, errorType.isRetryable());
        if(responseBody != null && !responseBody.isEmpty())
        {
            logger.debug("Response body: {}", responseBody);
        }
        // Extract response headers from Feign Response
        Map<String, List<String>> responseHeaders = new HashMap<>();
        if(response.headers() != null && !response.headers().isEmpty())
        {
            for(Map.Entry<String, java.util.Collection<String>> entry : response.headers().entrySet())
            {
                responseHeaders.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        return SDKClientException.fromHttpError(statusCode, responseBody, requestMethod, requestUrl, responseHeaders);
    }
}
