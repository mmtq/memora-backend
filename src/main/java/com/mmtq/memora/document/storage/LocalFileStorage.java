package com.mmtq.memora.document.storage;

import com.mmtq.memora.common.exception.BadRequestException;
import com.mmtq.memora.common.exception.NotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    private final Path storageDirectory;

    public LocalFileStorage( @Value("${app.storage.path:./uploads}") String storagePath ) {
        this.storageDirectory = Paths.get( storagePath ).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories( this.storageDirectory );
            log.info( "File storage directory initialized at: {}", this.storageDirectory );
        } catch ( IOException e ) {
            throw new RuntimeException( "Could not initialize file storage directory: " + this.storageDirectory, e );
        }
    }

    @Override
    public StoredFile save( InputStream inputStream, String originalFilename, String mimeType ) {
        String sanitizedName = sanitizeFilename( originalFilename );
        String storageKey = UUID.randomUUID().toString();
        Path targetPath = resolvePath( storageKey );

        try {
            MessageDigest digest = MessageDigest.getInstance( "SHA-256" );
            long sizeBytes;
            try ( DigestInputStream dis = new DigestInputStream( inputStream, digest ) ) {
                sizeBytes = Files.copy( dis, targetPath, StandardCopyOption.REPLACE_EXISTING );
            }
            String contentHash = HexFormat.of().formatHex( digest.digest() );
            return new StoredFile( storageKey, sanitizedName, mimeType, sizeBytes, contentHash );
        } catch ( NoSuchAlgorithmException e ) {
            deleteIfExists( targetPath );
            throw new RuntimeException( "SHA-256 algorithm not available", e );
        } catch ( IOException e ) {
            deleteIfExists( targetPath );
            throw new RuntimeException( "Failed to store file: " + sanitizedName, e );
        }
    }

    @Override
    public Resource load( String storageKey ) {
        Path targetPath = resolvePath( storageKey );
        try {
            Resource resource = new UrlResource( targetPath.toUri() );
            if ( !resource.exists() || !resource.isReadable() ) {
                throw new NotFoundException( "Stored file not found: " + storageKey );
            }
            return resource;
        } catch ( MalformedURLException e ) {
            throw new NotFoundException( "Could not read stored file: " + storageKey );
        }
    }

    @Override
    public void delete( String storageKey ) {
        if ( storageKey == null || storageKey.isBlank() ) {
            return;
        }
        Path targetPath = resolvePath( storageKey );
        deleteIfExists( targetPath );
    }

    private Path resolvePath( String storageKey ) {
        Path resolved = this.storageDirectory.resolve( storageKey ).normalize();
        if ( !resolved.startsWith( this.storageDirectory ) ) {
            throw new BadRequestException( "Invalid file path traversal detected" );
        }
        return resolved;
    }

    private String sanitizeFilename( String filename ) {
        if ( filename == null || filename.isBlank() ) {
            return "file";
        }
        String clean = Paths.get( filename ).getFileName().toString();
        clean = clean.replaceAll( "[^a-zA-Z0-9._-]", "_" );
        return clean.isBlank() ? "file" : clean;
    }

    private void deleteIfExists( Path path ) {
        try {
            Files.deleteIfExists( path );
        } catch ( IOException e ) {
            log.warn( "Failed to delete file at path: {}", path, e );
        }
    }
}
