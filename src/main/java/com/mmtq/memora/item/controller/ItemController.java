package com.mmtq.memora.item.controller;

import com.mmtq.memora.item.dto.CreateArticleRequest;
import com.mmtq.memora.item.dto.CreateNoteRequest;
import com.mmtq.memora.item.dto.ItemResponse;
import com.mmtq.memora.item.dto.PageResponse;
import com.mmtq.memora.item.dto.UpdateItemRequest;
import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.item.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping( "/api/v1/items" )
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @PostMapping( "/notes" )
    public ResponseEntity<ItemResponse> createNote( @Valid @RequestBody CreateNoteRequest request ) {
        ItemResponse response = itemService.createNote( request );
        return ResponseEntity.status( HttpStatus.CREATED ).body( response );
    }

    @PostMapping( "/articles" )
    public ResponseEntity<ItemResponse> createArticle( @Valid @RequestBody CreateArticleRequest request ) {
        ItemResponse response = itemService.createArticle( request );
        return ResponseEntity.status( HttpStatus.CREATED ).body( response );
    }

    @PostMapping( value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE )
    public ResponseEntity<ItemResponse> uploadFile(
            @RequestParam( "file" ) MultipartFile file,
            @RequestParam( value = "title", required = false ) String title,
            @RequestParam( value = "description", required = false ) String description ) {
        ItemResponse response = itemService.uploadFileItem( file, title, description );
        return ResponseEntity.status( HttpStatus.CREATED ).body( response );
    }

    @GetMapping
    public ResponseEntity<PageResponse<ItemResponse>> getItems(
            @RequestParam( value = "type", required = false ) ItemType type,
            @PageableDefault( size = 20 ) Pageable pageable ) {
        PageResponse<ItemResponse> response = itemService.getItems( type, pageable );
        return ResponseEntity.ok( response );
    }

    @GetMapping( "/{id}" )
    public ResponseEntity<ItemResponse> getItemById( @PathVariable( "id" ) UUID id ) {
        ItemResponse response = itemService.getItemById( id );
        return ResponseEntity.ok( response );
    }

    @PutMapping( "/{id}" )
    public ResponseEntity<ItemResponse> updateItem(
            @PathVariable( "id" ) UUID id,
            @Valid @RequestBody UpdateItemRequest request ) {
        ItemResponse response = itemService.updateItem( id, request );
        return ResponseEntity.ok( response );
    }

    @DeleteMapping( "/{id}" )
    public ResponseEntity<Void> deleteItem( @PathVariable( "id" ) UUID id ) {
        itemService.deleteItem( id );
        return ResponseEntity.noContent().build();
    }

    @PostMapping( "/{id}/reindex" )
    public ResponseEntity<Void> reindexItem( @PathVariable( "id" ) UUID id ) {
        itemService.reindexItem( id );
        return ResponseEntity.accepted().build();
    }
}
