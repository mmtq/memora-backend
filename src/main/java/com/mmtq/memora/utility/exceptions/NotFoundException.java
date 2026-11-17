package com.mmtq.memora.utility.exceptions;

public class NotFoundException extends RuntimeException {

    public NotFoundException( String entityName, Object identifier ) {
        super( String.format( "%s not found with identifier: %s", entityName, identifier ) );
    }

    public NotFoundException( String message ) {
        super( message );
    }
}
