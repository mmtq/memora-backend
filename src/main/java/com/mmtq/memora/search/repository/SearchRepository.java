package com.mmtq.memora.search.repository;

import com.mmtq.memora.ingestion.repository.ChunkRepository;
import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.search.dto.SearchResultItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public SearchRepository( JdbcTemplate jdbcTemplate ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SearchResultItem> findSimilarItems(
            UUID userId,
            float[] queryVector,
            ItemType typeFilter,
            int limit ) {
        String vectorStr = ChunkRepository.formatVector( queryVector );

        String sql = """
                SELECT 
                    c.item_id,
                    i.type,
                    i.title,
                    c.text AS chunk_text,
                    (1.0 - (c.embedding <=> ?::vector)) AS score
                FROM chunks c
                JOIN items i ON c.item_id = i.id
                WHERE c.user_id = ?
                  AND i.status = 'READY'
                  AND (? IS NULL OR i.type = ?)
                ORDER BY c.embedding <=> ?::vector ASC
                LIMIT 30
                """;

        String typeStr = ( typeFilter != null ) ? typeFilter.name() : null;

        List<CandidateChunk> candidates = jdbcTemplate.query(
                sql,
                ( rs, rowNum ) -> new CandidateChunk(
                        ( UUID ) rs.getObject( "item_id" ),
                        rs.getString( "type" ),
                        rs.getString( "title" ),
                        rs.getString( "chunk_text" ),
                        rs.getDouble( "score" )
                ),
                vectorStr,
                userId,
                typeStr,
                typeStr,
                vectorStr
        );

        Map<UUID, SearchResultItem> uniqueResults = new LinkedHashMap<>();
        for ( CandidateChunk candidate : candidates ) {
            if ( !uniqueResults.containsKey( candidate.itemId() ) ) {
                String snippet = candidate.chunkText();
                if ( snippet != null && snippet.length() > 300 ) {
                    snippet = snippet.substring( 0, 300 );
                }
                ItemType type = ItemType.valueOf( candidate.type() );
                uniqueResults.put( candidate.itemId(), new SearchResultItem(
                        candidate.itemId(),
                        type,
                        candidate.title(),
                        snippet,
                        candidate.score()
                ) );

                if ( uniqueResults.size() >= limit ) {
                    break;
                }
            }
        }

        return new ArrayList<>( uniqueResults.values() );
    }

    private record CandidateChunk(
            UUID itemId,
            String type,
            String title,
            String chunkText,
            double score
    ) {}
}
