package com.mmtq.memora.common.exception;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler( NotFoundException.class )
    public ResponseEntity<ErrorResponse> handleNotFound( NotFoundException ex ) {
        return buildErrorResponse( HttpStatus.NOT_FOUND, ex.getMessage() );
    }

    @ExceptionHandler( BadRequestException.class )
    public ResponseEntity<ErrorResponse> handleBadRequest( BadRequestException ex ) {
        return buildErrorResponse( HttpStatus.BAD_REQUEST, ex.getMessage() );
    }

    @ExceptionHandler( MethodArgumentNotValidException.class )
    public ResponseEntity<ErrorResponse> handleValidation( MethodArgumentNotValidException ex ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map( DefaultMessageSourceResolvable::getDefaultMessage )
                .distinct()
                .collect( Collectors.joining( "; " ) );

        return buildErrorResponse( HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message );
    }

    @ExceptionHandler( MaxUploadSizeExceededException.class )
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded( MaxUploadSizeExceededException ex ) {
        return buildErrorResponse( HttpStatus.BAD_REQUEST, "File size exceeds maximum allowed limit" );
    }

    @ExceptionHandler( HttpMessageNotReadableException.class )
    public ResponseEntity<ErrorResponse> handleNotReadable( HttpMessageNotReadableException ex ) {
        return buildErrorResponse( HttpStatus.BAD_REQUEST, "Malformed or missing request body" );
    }

    @ExceptionHandler( HttpMediaTypeNotSupportedException.class )
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported( HttpMediaTypeNotSupportedException ex ) {
        return buildErrorResponse( HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage() );
    }

    @ExceptionHandler( Exception.class )
    public ResponseEntity<ErrorResponse> handleGenericException( Exception ex ) {
        log.error( "Unhandled exception: ", ex );
        return buildErrorResponse( HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred" );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse( HttpStatus status, String message ) {
        return ResponseEntity.status( status ).body( new ErrorResponse( status.value(), message ) );
    }
}
