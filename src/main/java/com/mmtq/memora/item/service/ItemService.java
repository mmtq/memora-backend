package com.mmtq.memora.item.service;

import com.mmtq.memora.common.currentuser.CurrentUserProvider;
import com.mmtq.memora.common.exception.BadRequestException;
import com.mmtq.memora.common.exception.NotFoundException;
import com.mmtq.memora.document.model.Document;
import com.mmtq.memora.document.storage.FileStorage;
import com.mmtq.memora.document.storage.StoredFile;
import com.mmtq.memora.item.dto.CreateArticleRequest;
import com.mmtq.memora.item.dto.CreateNoteRequest;
import com.mmtq.memora.item.dto.ItemResponse;
import com.mmtq.memora.item.dto.PageResponse;
import com.mmtq.memora.item.dto.UpdateItemRequest;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.item.model.Item;
import com.mmtq.memora.item.repository.ItemRepository;
import com.mmtq.memora.item.util.TipTapTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "application/pdf"
    );

    private final ItemRepository itemRepository;
    private final FileStorage fileStorage;
    private final CurrentUserProvider currentUserProvider;

    @Value("${app.upload.max-file-size-bytes:26214400}")
    private long maxFileSizeBytes;

    @Transactional
    public ItemResponse createNote( CreateNoteRequest request ) {
        if ( request.description() == null || request.description().isBlank() ) {
            throw new BadRequestException( "description is required for notes" );
        }

        UUID currentUserId = currentUserProvider.getCurrentUserId();

        Item item = new Item();
        item.setUserId( currentUserId );
        item.setType( ItemType.NOTE );
        item.setTitle( request.title() );
        item.setDescription( request.description() );
        item.setPlainText( request.description() );
        item.setStatus( ItemStatus.READY );

        Item saved = itemRepository.save( item );
        return ItemResponse.from( saved );
    }

    @Transactional
    public ItemResponse createArticle( CreateArticleRequest request ) {
        if ( request.title() == null || request.title().isBlank() ) {
            throw new BadRequestException( "title is required for articles" );
        }
        if ( request.description() == null || request.description().isBlank() ) {
            throw new BadRequestException( "description is required for articles" );
        }

        UUID currentUserId = currentUserProvider.getCurrentUserId();
        String plainText = TipTapTextExtractor.extract( request.description() );

        Item item = new Item();
        item.setUserId( currentUserId );
        item.setType( ItemType.ARTICLE );
        item.setTitle( request.title() );
        item.setDescription( request.description() );
        item.setPlainText( plainText );
        item.setStatus( ItemStatus.READY );

        Item saved = itemRepository.save( item );
        return ItemResponse.from( saved );
    }

    public ItemResponse uploadFileItem( MultipartFile file, String title, String description ) {
        validateUploadedFile( file );

        UUID currentUserId = currentUserProvider.getCurrentUserId();
        String contentType = file.getContentType();
        if ( contentType != null ) {
            contentType = contentType.toLowerCase().trim();
        }

        StoredFile storedFile;
        try {
            storedFile = fileStorage.save( file.getInputStream(), file.getOriginalFilename(), contentType );
        } catch ( IOException e ) {
            throw new BadRequestException( "Failed to read uploaded file: " + e.getMessage(), e );
        }

        try {
            return saveFileItemAndDocument( currentUserId, title, description, storedFile );
        } catch ( Exception ex ) {
            log.error( "Database save failed after file was stored, deleting file: {}", storedFile.storageKey(), ex );
            fileStorage.delete( storedFile.storageKey() );
            throw ex;
        }
    }

    @Transactional
    protected ItemResponse saveFileItemAndDocument( UUID userId, String title, String description, StoredFile storedFile ) {
        Item item = new Item();
        item.setUserId( userId );
        item.setType( ItemType.FILE );
        item.setTitle( title );
        item.setDescription( description );
        item.setPlainText( description );
        item.setStatus( ItemStatus.READY );

        Document document = new Document();
        document.setStorageKey( storedFile.storageKey() );
        document.setOriginalName( storedFile.originalName() );
        document.setMimeType( storedFile.mimeType() );
        document.setSizeBytes( storedFile.sizeBytes() );
        document.setContentHash( storedFile.contentHash() );

        document.setItem( item );
        item.setDocument( document );

        Item saved = itemRepository.save( item );
        return ItemResponse.from( saved );
    }

    @Transactional( readOnly = true )
    public PageResponse<ItemResponse> getItems( ItemType type, Pageable pageable ) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by( Sort.Direction.DESC, "createdAt" )
        );

        Page<Item> page = ( type != null )
                ? itemRepository.findAllByUserIdAndType( currentUserId, type, sortedPageable )
                : itemRepository.findAllByUserId( currentUserId, sortedPageable );

        List<ItemResponse> content = page.getContent().stream()
                .map( ItemResponse::from )
                .toList();

        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional( readOnly = true )
    public ItemResponse getItemById( UUID id ) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        Item item = itemRepository.findByIdAndUserId( id, currentUserId )
                .orElseThrow( () -> new NotFoundException( "Item not found with identifier: " + id ) );

        return ItemResponse.from( item );
    }

    @Transactional
    public ItemResponse updateItem( UUID id, UpdateItemRequest request ) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        Item item = itemRepository.findByIdAndUserId( id, currentUserId )
                .orElseThrow( () -> new NotFoundException( "Item not found with identifier: " + id ) );

        // Validate according to the item's immutable type
        switch ( item.getType() ) {
            case NOTE -> {
                if ( request.description() == null || request.description().isBlank() ) {
                    throw new BadRequestException( "description is required for notes" );
                }
                item.setTitle( request.title() );
                item.setDescription( request.description() );
                item.setPlainText( request.description() );
            }
            case ARTICLE -> {
                if ( request.title() == null || request.title().isBlank() ) {
                    throw new BadRequestException( "title is required for articles" );
                }
                if ( request.description() == null || request.description().isBlank() ) {
                    throw new BadRequestException( "description is required for articles" );
                }
                item.setTitle( request.title() );
                item.setDescription( request.description() );
                item.setPlainText( TipTapTextExtractor.extract( request.description() ) );
            }
            case FILE -> {
                item.setTitle( request.title() );
                item.setDescription( request.description() );
                item.setPlainText( request.description() );
            }
        }

        Item saved = itemRepository.save( item );
        return ItemResponse.from( saved );
    }

    @Transactional
    public void deleteItem( UUID id ) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        Item item = itemRepository.findByIdAndUserId( id, currentUserId )
                .orElseThrow( () -> new NotFoundException( "Item not found with identifier: " + id ) );

        String storageKey = ( item.getDocument() != null ) ? item.getDocument().getStorageKey() : null;

        itemRepository.delete( item );
        itemRepository.flush();

        if ( storageKey != null ) {
            fileStorage.delete( storageKey );
        }
    }

    private void validateUploadedFile( MultipartFile file ) {
        if ( file == null || file.isEmpty() ) {
            throw new BadRequestException( "Exactly one image or PDF file is required" );
        }

        if ( file.getSize() > maxFileSizeBytes ) {
            throw new BadRequestException( String.format( "File size exceeds the maximum allowed limit of %d MB", maxFileSizeBytes / ( 1024 * 1024 ) ) );
        }

        String contentType = file.getContentType();
        if ( contentType == null || !ALLOWED_CONTENT_TYPES.contains( contentType.toLowerCase().trim() ) ) {
            throw new BadRequestException( "Unsupported file type: " + contentType + ". Allowed types: image/png, image/jpeg, image/webp, application/pdf" );
        }
    }
}
