package com.mmtq.memora.document.controller;

import com.mmtq.memora.document.dto.DocumentDownload;
import com.mmtq.memora.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping( "/api/v1/documents" )
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping( "/{id}/download" )
    public ResponseEntity<Resource> downloadDocument( @PathVariable( "id" ) UUID id ) {
        DocumentDownload download = documentService.downloadDocument( id );

        String contentDisposition = ContentDisposition.attachment()
                .filename( download.originalName(), StandardCharsets.UTF_8 )
                .build()
                .toString();

        return ResponseEntity.ok()
                .header( HttpHeaders.CONTENT_DISPOSITION, contentDisposition )
                .contentType( MediaType.parseMediaType( download.mimeType() ) )
                .contentLength( download.sizeBytes() )
                .body( download.resource() );
    }
}
