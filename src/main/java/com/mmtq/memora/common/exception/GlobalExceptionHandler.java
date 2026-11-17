package com.mmtq.memora.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger( GlobalExceptionHandler.class );

    @ExceptionHandler( NotFoundException.class )
    public ResponseEntity<ErrorResponse> handleNotFound( NotFoundException ex, HttpServletRequest request ) {
        log.warn( "Resource not found (404) on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage() );
        return buildErrorResponse( HttpStatus.NOT_FOUND, ex.getMessage() );
    }

    @ExceptionHandler( BadRequestException.class )
    public ResponseEntity<ErrorResponse> handleBadRequest( BadRequestException ex, HttpServletRequest request ) {
        log.warn( "Bad request (400) on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage() );
        return buildErrorResponse( HttpStatus.BAD_REQUEST, ex.getMessage() );
    }

    @ExceptionHandler( MethodArgumentNotValidException.class )
    public ResponseEntity<ErrorResponse> handleValidation( MethodArgumentNotValidException ex, HttpServletRequest request ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map( DefaultMessageSourceResolvable::getDefaultMessage )
                .distinct()
                .collect( Collectors.joining( "; " ) );

        String resolved = message.isBlank() ? "Validation failed" : message;
        log.warn( "Validation error (400) on {} {}: {}", request.getMethod(), request.getRequestURI(), resolved );
        return buildErrorResponse( HttpStatus.BAD_REQUEST, resolved );
    }

    @ExceptionHandler( MaxUploadSizeExceededException.class )
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded( MaxUploadSizeExceededException ex, HttpServletRequest request ) {
        log.warn( "Upload size exceeded (400) on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage() );
        return buildErrorResponse( HttpStatus.BAD_REQUEST, "File size exceeds maximum allowed limit" );
    }

    @ExceptionHandler( HttpMessageNotReadableException.class )
    public ResponseEntity<ErrorResponse> handleNotReadable( HttpMessageNotReadableException ex, HttpServletRequest request ) {
        log.warn( "Malformed or unreadable request body (400) on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage() );
        return buildErrorResponse( HttpStatus.BAD_REQUEST, "Malformed or missing request body" );
    }

    @ExceptionHandler( HttpMediaTypeNotSupportedException.class )
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported( HttpMediaTypeNotSupportedException ex, HttpServletRequest request ) {
        log.warn( "Unsupported media type (415) on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage() );
        return buildErrorResponse( HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage() );
    }

    @ExceptionHandler( Exception.class )
    public ResponseEntity<ErrorResponse> handleGenericException( Exception ex, HttpServletRequest request ) {
        log.error( "Unhandled exception (500) on {} {}: ", request.getMethod(), request.getRequestURI(), ex );
        return buildErrorResponse( HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred" );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse( HttpStatus status, String message ) {
        return ResponseEntity.status( status ).body( new ErrorResponse( status.value(), message ) );
    }
}
