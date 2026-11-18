package com.mmtq.memora.search.controller;

import com.mmtq.memora.common.response.ApiResponse;
import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.search.dto.ReindexAllResponse;
import com.mmtq.memora.search.dto.SearchResultItem;
import com.mmtq.memora.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping( "/api/v1/search" )
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ApiResponse<List<SearchResultItem>> search(
            @RequestParam( "q" ) String q,
            @RequestParam( value = "type", required = false ) ItemType type,
            @RequestParam( value = "limit", defaultValue = "10" ) int limit ) {
        List<SearchResultItem> results = searchService.search( q, type, limit );
        return ApiResponse.success( "Search completed successfully", results );
    }

    @PostMapping( "/reindex-all" )
    public ApiResponse<ReindexAllResponse> reindexAll() {
        ReindexAllResponse response = searchService.reindexAll();
        return new ApiResponse<>( "All items queued for reindexing", HttpStatus.ACCEPTED.value(), response );
    }
}
