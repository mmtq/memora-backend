package com.mmtq.memora.document.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

public interface FileStorage {

    StoredFile save( InputStream inputStream, String originalFilename, String mimeType );

    Resource load( String storageKey );

    void delete( String storageKey );
}
