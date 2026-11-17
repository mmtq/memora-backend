package com.mmtq.memora.search.service;

import com.mmtq.memora.ai.service.EmbeddingClient;
import com.mmtq.memora.common.currentuser.CurrentUserProvider;
import com.mmtq.memora.common.exception.BadRequestException;
import com.mmtq.memora.ingestion.service.JobService;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.item.model.Item;
import com.mmtq.memora.item.repository.ItemRepository;
import com.mmtq.memora.search.dto.ReindexAllResponse;
import com.mmtq.memora.search.dto.SearchResultItem;
import com.mmtq.memora.search.repository.SearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class SearchService {

    private final SearchRepository searchRepository;
    private final EmbeddingClient embeddingClient;
    private final CurrentUserProvider currentUserProvider;
    private final ItemRepository itemRepository;
    private final JobService jobService;

    public SearchService(
            SearchRepository searchRepository,
            EmbeddingClient embeddingClient,
            CurrentUserProvider currentUserProvider,
            ItemRepository itemRepository,
            JobService jobService ) {
        this.searchRepository = searchRepository;
        this.embeddingClient = embeddingClient;
        this.currentUserProvider = currentUserProvider;
        this.itemRepository = itemRepository;
        this.jobService = jobService;
    }

    public List<SearchResultItem> search( String query, ItemType typeFilter, int limit ) {
        if ( query == null || query.trim().isEmpty() ) {
            throw new BadRequestException( "Search query cannot be empty" );
        }

        UUID currentUserId = currentUserProvider.getCurrentUserId();
        int resolvedLimit = limit > 0 ? Math.min( limit, 100 ) : 10;

        float[] queryEmbedding = embeddingClient.embedQuery( query.trim() );
        if ( queryEmbedding == null || queryEmbedding.length == 0 ) {
            return Collections.emptyList();
        }

        return searchRepository.findSimilarItems( currentUserId, queryEmbedding, typeFilter, resolvedLimit );
    }

    @Transactional
    public ReindexAllResponse reindexAll() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        List<Item> items = itemRepository.findAllByUserId( currentUserId );

        for ( Item item : items ) {
            item.setStatus( ItemStatus.PROCESSING );
            itemRepository.save( item );
            jobService.enqueueIndexJob( item.getId() );
        }

        return new ReindexAllResponse( items.size(), String.format( "Reindex jobs enqueued for %d items", items.size() ) );
    }
}
