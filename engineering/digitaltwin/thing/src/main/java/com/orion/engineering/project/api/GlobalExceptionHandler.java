package com.orion.engineering.project.api;

import com.orion.engineering_util.api.payload.APIError;
import com.orion.engineering_util.api.payload.APIErrorIssue;
import com.orion.engineering_util.api.payload.APIMeta;
import com.orion.engineering_util.api.payload.APIResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler
{
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException exception)
    {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMostSpecificCause().getMessage());
    }


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<APIResponse> handleIllegalArgument(IllegalArgumentException exception)
    {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }


    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception)
    {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMostSpecificCause().getMessage());
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse> handleUnexpectedException(Exception exception)
    {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }


    private ResponseEntity<APIResponse> buildErrorResponse(HttpStatus httpStatus, String message)
    {
        APIErrorIssue issue = APIErrorIssue.of(httpStatus.value(), message);
        APIError error = APIError.of(httpStatus.value(), List.of(issue));
        APIResponse response = new APIResponse(APIMeta.of(UUID.randomUUID()), error);
        return ResponseEntity.status(httpStatus).body(response);
    }
}
