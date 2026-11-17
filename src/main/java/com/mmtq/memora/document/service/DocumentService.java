package com.mmtq.memora.document.service;

import com.mmtq.memora.common.currentuser.CurrentUserProvider;
import com.mmtq.memora.common.exception.NotFoundException;
import com.mmtq.memora.document.dto.DocumentDownload;
import com.mmtq.memora.document.model.Document;
import com.mmtq.memora.document.repository.DocumentRepository;
import com.mmtq.memora.document.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final FileStorage fileStorage;
    private final CurrentUserProvider currentUserProvider;

    @Transactional( readOnly = true )
    public DocumentDownload downloadDocument( UUID documentId ) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        Document document = documentRepository.findByIdAndItemUserId( documentId, currentUserId )
                .orElseThrow( () -> new NotFoundException( "Document not found with identifier: " + documentId ) );

        Resource resource = fileStorage.load( document.getStorageKey() );
        return new DocumentDownload( resource, document.getOriginalName(), document.getMimeType(), document.getSizeBytes() );
    }
}
