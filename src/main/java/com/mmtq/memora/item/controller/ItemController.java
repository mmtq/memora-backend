package com.mmtq.memora.item.controller;

import com.mmtq.memora.common.response.ApiResponse;
import com.mmtq.memora.common.response.PaginatedApiResponse;
import com.mmtq.memora.item.dto.CreateArticleRequest;
import com.mmtq.memora.item.dto.CreateNoteRequest;
import com.mmtq.memora.item.dto.ItemResponse;
import com.mmtq.memora.item.dto.UpdateItemRequest;
import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.item.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping( "/api/v1/items" )
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @PostMapping( "/notes" )
    public ApiResponse<ItemResponse> createNote( @Valid @RequestBody CreateNoteRequest request ) {
        ItemResponse response = itemService.createNote( request );
        return new ApiResponse<>( "Note created successfully", HttpStatus.CREATED.value(), response );
    }

    @PostMapping( "/articles" )
    public ApiResponse<ItemResponse> createArticle( @Valid @RequestBody CreateArticleRequest request ) {
        ItemResponse response = itemService.createArticle( request );
        return new ApiResponse<>( "Article created successfully", HttpStatus.CREATED.value(), response );
    }

    @PostMapping( value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE )
    public ApiResponse<ItemResponse> uploadFile(
            @RequestParam( "file" ) MultipartFile file,
            @RequestParam( value = "title", required = false ) String title,
            @RequestParam( value = "description", required = false ) String description ) {
        ItemResponse response = itemService.uploadFileItem( file, title, description );
        return new ApiResponse<>( "File uploaded successfully", HttpStatus.CREATED.value(), response );
    }

    @GetMapping
    public PaginatedApiResponse<List<ItemResponse>> getItems(
            @RequestParam( value = "type", required = false ) ItemType type,
            @PageableDefault( size = 20 ) Pageable pageable ) {
        return itemService.getItems( type, pageable );
    }

    @GetMapping( "/{id}" )
    public ApiResponse<ItemResponse> getItemById( @PathVariable( "id" ) UUID id ) {
        ItemResponse response = itemService.getItemById( id );
        return ApiResponse.success( "Item retrieved successfully", response );
    }

    @PutMapping( "/{id}" )
    public ApiResponse<ItemResponse> updateItem(
            @PathVariable( "id" ) UUID id,
            @Valid @RequestBody UpdateItemRequest request ) {
        ItemResponse response = itemService.updateItem( id, request );
        return ApiResponse.success( "Item updated successfully", response );
    }

    @DeleteMapping( "/{id}" )
    public ApiResponse<Void> deleteItem( @PathVariable( "id" ) UUID id ) {
        itemService.deleteItem( id );
        return ApiResponse.success( "Item deleted successfully" );
    }

    @PostMapping( "/{id}/reindex" )
    public ApiResponse<Void> reindexItem( @PathVariable( "id" ) UUID id ) {
        itemService.reindexItem( id );
        return new ApiResponse<>( "Item queued for reindexing", HttpStatus.ACCEPTED.value(), null );
    }
}
