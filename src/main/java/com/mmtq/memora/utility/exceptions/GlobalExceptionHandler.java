package com.mmtq.memora.utility.exceptions;

import com.mmtq.memora.utility.response.ApiErrorResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler( Exception.class )
    public ResponseEntity<ApiErrorResponse> handleGenericException( Exception ex ) {
        log.error( "Unhandled exception: ", ex );
        return buildErrorResponse( HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An internal server error occurred" );
    }

    @ExceptionHandler( MethodArgumentNotValidException.class )
    public ResponseEntity<ApiErrorResponse> handleValidationException( MethodArgumentNotValidException ex ) {
        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map( DefaultMessageSourceResolvable::getDefaultMessage )
                .distinct()
                .collect( Collectors.joining( ". " ) ) + ".";

        return buildErrorResponse( HttpStatus.BAD_REQUEST, "Bad Request", errorMessage );
    }

    @ExceptionHandler( HttpMessageNotReadableException.class )
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable( HttpMessageNotReadableException ex ) {
        if ( ex.getCause() instanceof InvalidFormatException ife && ife.getTargetType().isEnum() ) {
            String enumType = ife.getTargetType().getSimpleName();
            String invalidValue = ife.getValue() != null ? ife.getValue().toString() : "";
            String allowed = String.join( ", ",
                    Arrays.stream( ife.getTargetType().getEnumConstants() )
                            .map( Object::toString ).toList() );

            String message = "Invalid value '" + invalidValue + "' for enum " + enumType + ". Allowed: " + allowed;
            return buildErrorResponse( HttpStatus.BAD_REQUEST, "Bad Request", message );
        }
        return buildErrorResponse( HttpStatus.BAD_REQUEST, "Bad Request", "Invalid request body" );
    }

    @ExceptionHandler( NotFoundException.class )
    public ResponseEntity<ApiErrorResponse> handleNotFoundException( NotFoundException ex ) {
        return buildErrorResponse( HttpStatus.NOT_FOUND, "Not Found", ex.getMessage() );
    }

    @ExceptionHandler( DataIntegrityViolationException.class )
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation( DataIntegrityViolationException ex ) {
        String message = ex.getMessage();
        Pattern pattern = Pattern.compile( "Detail: Key \\(([^)]+)\\)=\\(([^)]+)\\) already exists\\." );
        Matcher matcher = pattern.matcher( message != null ? message : "" );

        if ( matcher.find() ) {
            String field = matcher.group( 1 );
            String value = matcher.group( 2 );
            return buildErrorResponse( HttpStatus.CONFLICT, "Conflict", field + " '" + value + "' already exists" );
        }

        return buildErrorResponse( HttpStatus.CONFLICT, "Conflict", "Database constraint violation" );
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse( HttpStatus httpStatus, String error, String message ) {
        return new ResponseEntity<>( new ApiErrorResponse( httpStatus.value(), error, message ), httpStatus );
    }
}
