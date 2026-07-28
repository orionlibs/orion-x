package com.orion.engineering.project.sdk.exception;

public enum SDKErrorType
{
    // 4xx Client Errors (Generally non-retryable)
    /**
     * 400 Bad Request - Invalid request parameters or format.
     * Non-retryable (client should fix the request).
     */
    BAD_REQUEST(400, false),
    /**
     * 401 Unauthorized - Missing or invalid authentication credentials.
     * Non-retryable (client should refresh credentials).
     */
    UNAUTHORIZED(401, false),
    /**
     * 403 Forbidden - Client doesn't have permission to access resource.
     * Non-retryable (client should not retry with same credentials).
     */
    FORBIDDEN(403, false),
    /**
     * 404 Not Found - Requested resource doesn't exist.
     * Non-retryable (resource won't appear on retry).
     */
    NOT_FOUND(404, false),
    /**
     * 405 Method Not Allowed - HTTP method not supported for this endpoint.
     * Non-retryable (client should use different method).
     */
    METHOD_NOT_ALLOWED(405, false),
    /**
     * 406 Not Acceptable - Server cannot produce response matching Accept headers.
     * Non-retryable (client should adjust Accept headers).
     */
    NOT_ACCEPTABLE(406, false),
    /**
     * 408 Request Timeout - Server timed out waiting for request.
     * Retryable (temporary network issue).
     */
    REQUEST_TIMEOUT(408, true),
    /**
     * 409 Conflict - Request conflicts with current state of resource.
     * Non-retryable (client should resolve conflict).
     */
    CONFLICT(409, false),
    /**
     * 410 Gone - Resource permanently deleted.
     * Non-retryable (resource won't come back).
     */
    GONE(410, false),
    /**
     * 415 Unsupported Media Type - Request payload format not supported.
     * Non-retryable (client should use different Content-Type).
     */
    UNSUPPORTED_MEDIA_TYPE(415, false),
    /**
     * 422 Unprocessable Entity - Request syntactically correct but semantically invalid.
     * Non-retryable (client should fix semantic issues).
     */
    UNPROCESSABLE_ENTITY(422, false),
    /**
     * 429 Too Many Requests - Rate limit exceeded.
     * Retryable (with exponential backoff).
     */
    TOO_MANY_REQUESTS(429, true),
    // 5xx Server Errors (Generally retryable)
    /**
     * 500 Internal Server Error - Generic server error.
     * Retryable (may be transient).
     */
    INTERNAL_SERVER_ERROR(500, true),
    /**
     * 501 Not Implemented - Server doesn't support requested functionality.
     * Non-retryable (feature not available).
     */
    NOT_IMPLEMENTED(501, false),
    /**
     * 502 Bad Gateway - Invalid response from upstream server.
     * Retryable (upstream may recover).
     */
    BAD_GATEWAY(502, true),
    /**
     * 503 Service Unavailable - Server temporarily unavailable.
     * Retryable (service should recover).
     */
    SERVICE_UNAVAILABLE(503, true),
    /**
     * 504 Gateway Timeout - Upstream server didn't respond in time.
     * Retryable (upstream may respond on retry).
     */
    GATEWAY_TIMEOUT(504, true),
    // Non-HTTP Errors
    /**
     * Network error (connection refused, DNS failure, etc.).
     * Retryable (network may recover).
     */
    NETWORK_ERROR(-1, true),
    /**
     * Read/write timeout error.
     * Retryable (may succeed on retry).
     */
    TIMEOUT_ERROR(-2, true),
    /**
     * Unknown error (couldn't determine specific type).
     * Retryable (conservative approach).
     */
    UNKNOWN_ERROR(-3, true),
    /**
     * Generic client error (4xx not covered by specific types).
     * Non-retryable (likely a client-side issue).
     */
    CLIENT_ERROR(-4, false),
    /**
     * Generic server error (5xx not covered by specific types).
     * Retryable (likely transient).
     */
    SERVER_ERROR(-5, true);
    private final int statusCode;
    private final boolean retryable;


    SDKErrorType(int statusCode, boolean retryable)
    {
        this.statusCode = statusCode;
        this.retryable = retryable;
    }


    /**
     * Determines the error type from an HTTP status code.
     *
     * @param statusCode HTTP status code
     * @return corresponding ConnectorErrorType
     */
    public static SDKErrorType fromStatusCode(int statusCode)
    {
        return switch(statusCode)
        {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 408 -> REQUEST_TIMEOUT;
            case 409 -> CONFLICT;
            case 410 -> GONE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 422 -> UNPROCESSABLE_ENTITY;
            case 429 -> TOO_MANY_REQUESTS;
            case 500 -> INTERNAL_SERVER_ERROR;
            case 501 -> NOT_IMPLEMENTED;
            case 502 -> BAD_GATEWAY;
            case 503 -> SERVICE_UNAVAILABLE;
            case 504 -> GATEWAY_TIMEOUT;
            default ->
            {
                if(statusCode >= 400 && statusCode < 500)
                {
                    yield CLIENT_ERROR;
                }
                else if(statusCode >= 500 && statusCode < 600)
                {
                    yield SERVER_ERROR;
                }
                else
                {
                    yield UNKNOWN_ERROR;
                }
            }
        };
    }


    /**
     * Determines if a given HTTP status code represents a retryable error.
     *
     * @param statusCode HTTP status code
     * @return true if the status code is retryable, false otherwise
     */
    public static boolean isRetryableStatusCode(int statusCode)
    {
        return fromStatusCode(statusCode).isRetryable();
    }


    /**
     * Checks if status code represents a server error (5xx).
     *
     * @param statusCode HTTP status code
     * @return true if 5xx error
     */
    public static boolean isServerError(int statusCode)
    {
        return statusCode >= 500 && statusCode < 600;
    }


    /**
     * Checks if status code represents a client error (4xx).
     *
     * @param statusCode HTTP status code
     * @return true if 4xx error
     */
    public static boolean isClientError(int statusCode)
    {
        return statusCode >= 400 && statusCode < 500;
    }


    /**
     * Checks if status code represents a rate limit error (429).
     *
     * @param statusCode HTTP status code
     * @return true if 429
     */
    public static boolean isRateLimitError(int statusCode)
    {
        return statusCode == 429;
    }


    /**
     * Gets the HTTP status code associated with this error type.
     *
     * @return status code, or negative value for non-HTTP errors
     */
    public int getStatusCode()
    {
        return statusCode;
    }


    /**
     * Determines if this error type should trigger a retry.
     *
     * @return true if retryable, false otherwise
     */
    public boolean isRetryable()
    {
        return retryable;
    }


    @Override
    public String toString()
    {
        return String.format("%s (HTTP %d, retryable=%s)", name(), statusCode, retryable);
    }
}
