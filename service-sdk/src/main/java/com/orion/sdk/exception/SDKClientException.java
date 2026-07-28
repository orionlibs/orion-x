package com.orion.sdk.exception;

import java.util.List;
import java.util.Map;

/**
 * Exception thrown when a client request fails.
 * <p>
 * Includes detailed error information:
 * <ul>
 *   <li>Error type with retryability logic</li>
 *   <li>HTTP status code</li>
 *   <li>Response body (if available)</li>
 *   <li>Request details (method, URL)</li>
 *   <li>Additional metadata</li>
 * </ul>
 */
public class SDKClientException extends RuntimeException
{
    private final SDKErrorType errorType;
    private final int statusCode;
    private final String responseBody;
    private final String requestMethod;
    private final String requestUrl;
    private final Map<String, String> metadata;
    private final Map<String, List<String>> responseHeaders;


    public SDKClientException(String message,
                              SDKErrorType errorType,
                              int statusCode,
                              String responseBody,
                              String requestMethod,
                              String requestUrl,
                              Map<String, String> metadata,
                              Map<String, List<String>> responseHeaders,
                              Throwable cause)
    {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.requestMethod = requestMethod;
        this.requestUrl = requestUrl;
        this.metadata = metadata;
        this.responseHeaders = responseHeaders;
    }


    public SDKClientException(String message, SDKErrorType errorType, int statusCode)
    {
        this(message, errorType, statusCode, null, null, null, Map.of(), null, null);
    }


    public SDKClientException(String message, SDKErrorType errorType, int statusCode, Throwable cause)
    {
        this(message, errorType, statusCode, null, null, null, Map.of(), null, cause);
    }


    public static SDKClientException fromHttpError(int statusCode,
                                                   String responseBody,
                                                   String requestMethod,
                                                   String requestUrl)
    {
        SDKErrorType errorType = SDKErrorType.fromStatusCode(statusCode);
        String message = String.format("HTTP %d error calling %s %s", statusCode, requestMethod, requestUrl);
        return new SDKClientException(message, errorType, statusCode, responseBody, requestMethod, requestUrl, Map.of(), null, null);
    }


    public static SDKClientException fromHttpError(int statusCode,
                                                   String responseBody,
                                                   String requestMethod,
                                                   String requestUrl,
                                                   Map<String, List<String>> responseHeaders)
    {
        SDKErrorType errorType = SDKErrorType.fromStatusCode(statusCode);
        String message = String.format("HTTP %d error calling %s %s", statusCode, requestMethod, requestUrl);
        return new SDKClientException(message, errorType, statusCode, responseBody, requestMethod, requestUrl, Map.of(), responseHeaders, null);
    }


    public static SDKClientException networkError(String message, Throwable cause)
    {
        return new SDKClientException(message,
                                      SDKErrorType.NETWORK_ERROR,
                                      -1,
                                      null,
                                      null,
                                      null,
                                      Map.of(),
                                      null,
                                      cause);
    }


    public static SDKClientException timeoutError(String message, String requestMethod, String requestUrl)
    {
        return new SDKClientException(message,
                                      SDKErrorType.TIMEOUT_ERROR,
                                      -2,
                                      null,
                                      requestMethod,
                                      requestUrl,
                                      Map.of(),
                                      null,
                                      null);
    }


    public static SDKClientException unknownError(String message, Throwable cause)
    {
        return new SDKClientException(message,
                                      SDKErrorType.UNKNOWN_ERROR,
                                      -3,
                                      null,
                                      null,
                                      null,
                                      Map.of(),
                                      null,
                                      cause);
    }


    public static SDKClientException rateLimitError(String responseBody, String requestMethod, String requestUrl)
    {
        String message = String.format("Rate limit exceeded calling %s %s", requestMethod, requestUrl);
        return new SDKClientException(message,
                                      SDKErrorType.TOO_MANY_REQUESTS,
                                      429,
                                      responseBody,
                                      requestMethod,
                                      requestUrl,
                                      Map.of(),
                                      null,
                                      null);
    }


    public static SDKClientException unauthorizedError(String responseBody, String requestMethod, String requestUrl)
    {
        String message = String.format("Unauthorized calling %s %s", requestMethod, requestUrl);
        return new SDKClientException(message,
                                      SDKErrorType.UNAUTHORIZED,
                                      401,
                                      responseBody,
                                      requestMethod,
                                      requestUrl,
                                      Map.of(),
                                      null,
                                      null);
    }


    public SDKErrorType getErrorType()
    {
        return errorType;
    }


    public int getStatusCode()
    {
        return statusCode;
    }
    // Factory methods for common scenarios


    public String getResponseBody()
    {
        return responseBody;
    }


    public String getRequestMethod()
    {
        return requestMethod;
    }


    public String getRequestUrl()
    {
        return requestUrl;
    }


    public Map<String, String> getMetadata()
    {
        return metadata != null ? metadata : Map.of();
    }


    public Map<String, List<String>> getResponseHeaders()
    {
        return responseHeaders != null ? responseHeaders : Map.of();
    }


    public boolean isRetryable()
    {
        return errorType.isRetryable();
    }


    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("ConnectorClientException{");
        sb.append("errorType=").append(errorType);
        sb.append(", statusCode=").append(statusCode);
        sb.append(", message='").append(getMessage()).append('\'');
        if(requestMethod != null && requestUrl != null)
        {
            sb.append(", request=").append(requestMethod).append(" ").append(requestUrl);
        }
        if(responseBody != null)
        {
            // Truncate response body for readability
            String truncated = responseBody.length() > 200
                            ? responseBody.substring(0, 200) + "..."
                            : responseBody;
            sb.append(", responseBody='").append(truncated).append('\'');
        }
        if(metadata != null && !metadata.isEmpty())
        {
            sb.append(", metadata=").append(metadata);
        }
        sb.append('}');
        return sb.toString();
    }
}
