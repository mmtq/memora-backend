package com.mmtq.memora.ingestion.controller;

import com.mmtq.memora.common.currentuser.CurrentUserProvider;
import com.mmtq.memora.common.response.ApiResponse;
import com.mmtq.memora.common.response.PaginatedApiResponse;
import com.mmtq.memora.ingestion.dto.IngestionStatsResponse;
import com.mmtq.memora.ingestion.dto.ItemIndexingStatusResponse;
import com.mmtq.memora.ingestion.dto.JobResponse;
import com.mmtq.memora.ingestion.dto.RetryFailedResponse;
import com.mmtq.memora.ingestion.enums.JobStatus;
import com.mmtq.memora.ingestion.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping( "/api/v1" )
@RequiredArgsConstructor
public class IngestionController {

    private final JobService jobService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping( "/ingestion/stats" )
    public ApiResponse<IngestionStatsResponse> getStats() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        IngestionStatsResponse response = jobService.getStats( currentUserId );
        return ApiResponse.success( "Ingestion stats retrieved successfully", response );
    }

    @GetMapping( "/ingestion/jobs" )
    public PaginatedApiResponse<List<JobResponse>> getJobs(
            @RequestParam( value = "status", required = false ) JobStatus status,
            @RequestParam( value = "page", defaultValue = "0" ) int page,
            @RequestParam( value = "size", defaultValue = "20" ) int size ) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        int resolvedPage = Math.max( page, 0 );
        int resolvedSize = size > 0 ? Math.min( size, 100 ) : 20;
        Pageable pageable = PageRequest.of( resolvedPage, resolvedSize );

        return jobService.getJobs( currentUserId, status, pageable );
    }

    @GetMapping( "/items/{id}/indexing" )
    public ApiResponse<ItemIndexingStatusResponse> getItemIndexingStatus( @PathVariable( "id" ) UUID id ) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        ItemIndexingStatusResponse response = jobService.getItemIndexingStatus( id, currentUserId );
        return ApiResponse.success( "Item indexing status retrieved successfully", response );
    }

    @PostMapping( "/ingestion/jobs/{id}/retry" )
    public ApiResponse<JobResponse> retryJob( @PathVariable( "id" ) UUID id ) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        JobResponse response = jobService.retryJob( id, currentUserId );
        return ApiResponse.success( "Job retry scheduled successfully", response );
    }

    @PostMapping( "/ingestion/retry-failed" )
    public ApiResponse<RetryFailedResponse> retryAllFailedJobs() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        RetryFailedResponse response = jobService.retryAllFailedJobs( currentUserId );
        return ApiResponse.success( "Failed jobs retry scheduled successfully", response );
    }
}
