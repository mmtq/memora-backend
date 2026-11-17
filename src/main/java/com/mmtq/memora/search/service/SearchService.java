package com.mmtq.memora.search.service;

import com.mmtq.memora.ai.service.EmbeddingClient;
import com.mmtq.memora.common.currentuser.CurrentUserProvider;
import com.mmtq.memora.common.exception.BadRequestException;
import com.mmtq.memora.ingestion.repository.ChunkRepositoryImpl;
import com.mmtq.memora.ingestion.service.JobService;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.item.model.Item;
import com.mmtq.memora.item.repository.ItemRepository;
import com.mmtq.memora.search.dto.ReindexAllResponse;
import com.mmtq.memora.search.dto.SearchChunkProjection;
import com.mmtq.memora.search.dto.SearchResultItem;
import com.mmtq.memora.search.repository.SearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional( readOnly = true )
public class SearchService {

    private final SearchRepository searchRepository;
    private final EmbeddingClient embeddingClient;
    private final CurrentUserProvider currentUserProvider;
    private final ItemRepository itemRepository;
    private final JobService jobService;

    @Value( "${search.min-similarity:0.6}" )
    private double minSimilarity = 0.6;

    public List<SearchResultItem> search( String query, ItemType typeFilter, int limit ) {
        return search( query, typeFilter, limit, this.minSimilarity );
    }

    public List<SearchResultItem> search( String query, ItemType typeFilter, int limit, double threshold ) {
        if ( query == null || query.trim().isEmpty() ) {
            throw new BadRequestException( "Search query cannot be empty" );
        }

        UUID currentUserId = currentUserProvider.getCurrentUserId();
        int resolvedLimit = limit > 0 ? Math.min( limit, 100 ) : 10;

        float[] queryEmbedding = embeddingClient.embedQuery( query.trim() );
        if ( queryEmbedding == null || queryEmbedding.length == 0 ) {
            return Collections.emptyList();
        }

        String vectorStr = ChunkRepositoryImpl.formatVector( queryEmbedding );
        int fetchLimit = Math.max( resolvedLimit * 5, 50 );
        String typeFilterStr = typeFilter != null ? typeFilter.name() : null;

        List<SearchChunkProjection> candidates = searchRepository.searchChunks(
                currentUserId,
                vectorStr,
                typeFilterStr,
                fetchLimit
        );

        List<Double> topScores = candidates.stream()
                .limit( 5 )
                .map( SearchChunkProjection::getScore )
                .toList();
        log.debug( "Top {} raw similarities for search query (threshold={}): {}", topScores.size(), threshold, topScores );

        List<SearchChunkProjection> passingCandidates = candidates.stream()
                .filter( c -> c.getScore() != null && c.getScore() >= threshold )
                .toList();

        if ( passingCandidates.isEmpty() ) {
            return Collections.emptyList();
        }

        Map<UUID, SearchResultItem> uniqueResults = new LinkedHashMap<>();
        for ( SearchChunkProjection candidate : passingCandidates ) {
            if ( !uniqueResults.containsKey( candidate.getItemId() ) ) {
                String snippet = candidate.getChunkText();
                if ( snippet != null && snippet.length() > 300 ) {
                    snippet = snippet.substring( 0, 300 );
                }
                ItemType type = ItemType.valueOf( candidate.getType() );
                ItemStatus status = candidate.getStatus() != null ? ItemStatus.valueOf( candidate.getStatus() ) : ItemStatus.READY;
                uniqueResults.put( candidate.getItemId(), new SearchResultItem(
                        candidate.getItemId(),
                        type,
                        candidate.getTitle(),
                        candidate.getDescription(),
                        status,
                        candidate.getCreatedAt(),
                        candidate.getUpdatedAt(),
                        snippet,
                        candidate.getScore()
                ) );

                if ( uniqueResults.size() >= resolvedLimit ) {
                    break;
                }
            }
        }

        return new ArrayList<>( uniqueResults.values() );
    }

    @Transactional
    public ReindexAllResponse reindexAll() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        List<Item> items = itemRepository.findAllByUserId( currentUserId );

        for ( Item item : items ) {
            itemRepository.updateStatus( item.getId(), ItemStatus.PROCESSING );
            jobService.enqueueIndexJob( item.getId(), item.getUserId() );
        }

        return new ReindexAllResponse( items.size(), String.format( "Reindex jobs enqueued for %d items", items.size() ) );
    }
}
