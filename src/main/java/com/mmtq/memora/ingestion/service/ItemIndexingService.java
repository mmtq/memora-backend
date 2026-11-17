package com.mmtq.memora.ingestion.service;

import com.mmtq.memora.ai.service.EmbeddingClient;
import com.mmtq.memora.ingestion.entity.Chunk;
import com.mmtq.memora.ingestion.repository.ChunkRepository;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemIndexingService {

    private final ItemRepository itemRepository;
    private final ChunkRepository chunkRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingClient embeddingClient;
    private final TransactionTemplate transactionTemplate;

    @Value( "${gemini.model:gemini-embedding-001}" )
    private String embeddingModel = "gemini-embedding-001";

    public int processIndexItem( UUID itemId ) {
        // 1. Load item data (outside of any long-lived transaction)
        Optional<ItemSnapshot> snapshotOpt = loadItemSnapshot( itemId );
        if ( snapshotOpt.isEmpty() ) {
            log.warn( "Item {} no longer exists, skipping index job", itemId );
            return 0;
        }

        ItemSnapshot snapshot = snapshotOpt.get();

        // 2. If title and plain_text are both blank: delete chunks and set READY
        boolean hasTitle = snapshot.title() != null && !snapshot.title().isBlank();
        boolean hasPlainText = snapshot.plainText() != null && !snapshot.plainText().isBlank();

        if ( !hasTitle && !hasPlainText ) {
            clearChunksAndSetReady( itemId );
            return 0;
        }

        // 3. Cut plain_text with ChunkingService
        List<String> chunkTexts = hasPlainText
                ? chunkingService.chunk( snapshot.plainText() )
                : Collections.emptyList();

        if ( chunkTexts.isEmpty() && hasTitle ) {
            chunkTexts = List.of( snapshot.title().trim() );
        }

        if ( chunkTexts.isEmpty() ) {
            clearChunksAndSetReady( itemId );
            return 0;
        }

        // 4. Call Gemini Embedding API for all chunks (outside transaction)
        List<float[]> embeddings = embeddingClient.embedDocuments( chunkTexts );
        if ( embeddings.size() != chunkTexts.size() ) {
            throw new IllegalStateException( String.format(
                    "Embedding count mismatch for item %s: expected %d, got %d",
                    itemId, chunkTexts.size(), embeddings.size()
            ) );
        }

        // 5. Replace existing chunks and set READY atomically in a transaction
        replaceChunksAndSetReady( itemId, snapshot.userId(), chunkTexts, embeddings );
        return chunkTexts.size();
    }

    protected Optional<ItemSnapshot> loadItemSnapshot( UUID itemId ) {
        return itemRepository.findById( itemId )
                .map( item -> new ItemSnapshot( item.getId(), item.getUserId(), item.getTitle(), item.getPlainText() ) );
    }

    protected void clearChunksAndSetReady( UUID itemId ) {
        transactionTemplate.executeWithoutResult( status -> {
            chunkRepository.deleteByItemId( itemId );
            itemRepository.updateStatus( itemId, ItemStatus.READY );
        } );
    }

    protected void replaceChunksAndSetReady( UUID itemId, UUID userId, List<String> chunkTexts, List<float[]> embeddings ) {
        transactionTemplate.executeWithoutResult( status -> {
            chunkRepository.deleteByItemId( itemId );

            List<Chunk> chunksToInsert = new ArrayList<>( chunkTexts.size() );
            for ( int i = 0; i < chunkTexts.size(); i++ ) {
                chunksToInsert.add( new Chunk(
                        itemId,
                        userId,
                        i,
                        chunkTexts.get( i ),
                        embeddings.get( i ),
                        embeddingModel
                ) );
            }

            chunkRepository.insertChunks( chunksToInsert );
            itemRepository.updateStatus( itemId, ItemStatus.READY );

            log.info( "Successfully indexed item {} with {} chunks", itemId, chunksToInsert.size() );
        } );
    }

    public record ItemSnapshot( UUID id, UUID userId, String title, String plainText ) {}
}
