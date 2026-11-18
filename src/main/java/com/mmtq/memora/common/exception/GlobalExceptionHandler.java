package com.mmtq.memora.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.mmtq.memora.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler( NotFoundException.class )
    public ResponseEntity<ApiErrorResponse> handleNotFound( NotFoundException ex, HttpServletRequest request ) {
        log.warn( "Resource not found (404) on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage() );
        return buildErrorResponse( HttpStatus.NOT_FOUND, "Not Found", ex.getMessage() );
    }

    @ExceptionHandler( BadRequestException.class )
    public ResponseEntity<ApiErrorResponse> handleBadRequest( BadRequestException ex, HttpServletRequest request ) {
        log.warn( "Bad request (400) on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage() );
        return buildErrorResponse( HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage() );
    }

    @ExceptionHandler( MethodArgumentNotValidException.class )
    public ResponseEntity<ApiErrorResponse> handleValidation( MethodArgumentNotValidException ex, HttpServletRequest request ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map( DefaultMessageSourceResolvable::getDefaultMessage )
                .distinct()
                .collect( Collectors.joining( "; " ) );

        String resolved = message.isBlank() ? "Validation failed" : message;
        log.warn( "Validation error (400) on {} {}: {}", request.getMethod(), request.getRequestURI(), resolved );
        return buildErrorResponse( HttpStatus.BAD_REQUEST, "Bad Request", resolved );
    }

    @ExceptionHandler( MaxUploadSizeExceededException.class )
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded( MaxUploadSizeExceededException ex, HttpServletRequest request ) {
        log.warn( "Upload size exceeded (400) on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage() );
        return buildErrorResponse( HttpStatus.BAD_REQUEST, "Bad Request", "File size exceeds maximum allowed limit" );
    }

    @ExceptionHandler( HttpMessageNotReadableException.class )
    public ResponseEntity<ApiErrorResponse> handleNotReadable( HttpMessageNotReadableException ex, HttpServletRequest request ) {
        if ( ex.getCause() instanceof InvalidFormatException ife && ife.getTargetType() != null && ife.getTargetType().isEnum() ) {
            String enumType = ife.getTargetType().getSimpleName();
            String invalidValue = ife.getValue() != null ? ife.getValue().toString() : "";
            String allowed = String.join( ", ",
                    Arrays.stream( ife.getTargetType().getEnumConstants() )
                            .map( Object::toString ).toList() );

            String message = "Invalid value '" + invalidValue + "' for enum " + enumType + ". Allowed: " + allowed;
            log.warn( "Enum parsing error (400) on {} {}: {}", request.getMethod(), request.getRequestURI(), message );
            return buildErrorResponse( HttpStatus.BAD_REQUEST, "Bad Request", message );
        }
        log.warn( "Malformed or unreadable request body (400) on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage() );
        return buildErrorResponse( HttpStatus.BAD_REQUEST, "Bad Request", "Malformed or missing request body" );
    }

    @ExceptionHandler( HttpMediaTypeNotSupportedException.class )
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported( HttpMediaTypeNotSupportedException ex, HttpServletRequest request ) {
        log.warn( "Unsupported media type (415) on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage() );
        return buildErrorResponse( HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type", ex.getMessage() );
    }

    @ExceptionHandler( DataIntegrityViolationException.class )
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation( DataIntegrityViolationException ex, HttpServletRequest request ) {
        String message = ex.getMessage();
        Pattern pattern = Pattern.compile( "Detail: Key \\(([^)]+)\\)=\\(([^)]+)\\) already exists\\." );
        Matcher matcher = pattern.matcher( message != null ? message : "" );

        if ( matcher.find() ) {
            String field = matcher.group( 1 );
            String value = matcher.group( 2 );
            String conflictMsg = field + " '" + value + "' already exists";
            log.warn( "Data integrity violation (409) on {} {}: {}", request.getMethod(), request.getRequestURI(), conflictMsg );
            return buildErrorResponse( HttpStatus.CONFLICT, "Conflict", conflictMsg );
        }
        log.warn( "Data integrity violation (409) on {} {}: {}", request.getMethod(), request.getRequestURI(), message );
        return buildErrorResponse( HttpStatus.CONFLICT, "Conflict", "Database constraint violation" );
    }

    @ExceptionHandler( Exception.class )
    public ResponseEntity<ApiErrorResponse> handleGenericException( Exception ex, HttpServletRequest request ) {
        log.error( "Unhandled exception (500) on {} {}: ", request.getMethod(), request.getRequestURI(), ex );
        return buildErrorResponse( HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An internal server error occurred" );
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse( HttpStatus status, String error, String message ) {
        return ResponseEntity.status( status ).body( new ApiErrorResponse( status.value(), error, message ) );
    }
}
