package com.mmtq.memora.search.repository;

import com.mmtq.memora.item.model.Item;
import com.mmtq.memora.search.dto.SearchChunkProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SearchRepository extends JpaRepository<Item, UUID> {

    @Query( value = """
            SELECT 
                c.item_id AS itemId,
                i.type AS type,
                i.title AS title,
                i.description AS description,
                i.status AS status,
                i.created_at AS createdAt,
                i.updated_at AS updatedAt,
                c.text AS chunkText,
                (1.0 - (c.embedding <=> cast(:queryVector as vector))) AS score
            FROM chunks c
            JOIN items i ON c.item_id = i.id
            WHERE c.user_id = :userId
              AND i.status = 'READY'
              AND (cast(:typeFilter as varchar) IS NULL OR i.type = cast(:typeFilter as varchar))
            ORDER BY c.embedding <=> cast(:queryVector as vector) ASC
            LIMIT :fetchLimit
            """, nativeQuery = true )
    List<SearchChunkProjection> searchChunks(
            @Param( "userId" ) UUID userId,
            @Param( "queryVector" ) String queryVector,
            @Param( "typeFilter" ) String typeFilter,
            @Param( "fetchLimit" ) int fetchLimit
    );
}
