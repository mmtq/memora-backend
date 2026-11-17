package com.mmtq.memora.ingestion.repository;

import com.mmtq.memora.ingestion.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, UUID>, ChunkRepositoryCustom {

    @Transactional
    @Modifying
    @Query( "DELETE FROM Chunk c WHERE c.itemId = :itemId" )
    void deleteByItemId( @Param( "itemId" ) UUID itemId );

    int countByItemId( UUID itemId );

    long countByUserId( UUID userId );

    @Query( nativeQuery = true, value = "SELECT embedding_model FROM chunks WHERE item_id = :itemId LIMIT 1" )
    String findEmbeddingModelByItemId( @Param( "itemId" ) UUID itemId );
}
