package com.mmtq.memora.common.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException( String message ) {
        super( message );
    }

    public NotFoundException( String entityName, Object identifier ) {
        super( String.format( "%s not found with identifier: %s", entityName, identifier ) );
    }
}
