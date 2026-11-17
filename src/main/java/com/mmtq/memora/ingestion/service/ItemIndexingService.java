package com.mmtq.memora.ingestion.service;

import com.mmtq.memora.ai.service.EmbeddingClient;
import com.mmtq.memora.ingestion.entity.Chunk;
import com.mmtq.memora.ingestion.repository.ChunkRepository;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.model.Item;
import com.mmtq.memora.item.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ItemIndexingService {

    private static final Logger log = LoggerFactory.getLogger( ItemIndexingService.class );

    private final ItemRepository itemRepository;
    private final ChunkRepository chunkRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingClient embeddingClient;
    private final String embeddingModel;

    public ItemIndexingService(
            ItemRepository itemRepository,
            ChunkRepository chunkRepository,
            ChunkingService chunkingService,
            EmbeddingClient embeddingClient,
            @Value("${gemini.model:gemini-embedding-001}") String embeddingModel ) {
        this.itemRepository = itemRepository;
        this.chunkRepository = chunkRepository;
        this.chunkingService = chunkingService;
        this.embeddingClient = embeddingClient;
        this.embeddingModel = embeddingModel != null && !embeddingModel.isBlank() ? embeddingModel.trim() : "gemini-embedding-001";
    }

    public boolean processIndexItem( UUID itemId ) {
        // 1. Load item data (outside of any long-lived transaction)
        Optional<ItemSnapshot> snapshotOpt = loadItemSnapshot( itemId );
        if ( snapshotOpt.isEmpty() ) {
            log.warn( "Item {} no longer exists, skipping index job", itemId );
            return true;
        }

        ItemSnapshot snapshot = snapshotOpt.get();

        // 2. If title and plain_text are both blank: delete chunks and set READY
        boolean hasTitle = snapshot.title() != null && !snapshot.title().isBlank();
        boolean hasPlainText = snapshot.plainText() != null && !snapshot.plainText().isBlank();

        if ( !hasTitle && !hasPlainText ) {
            clearChunksAndSetReady( itemId );
            return true;
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
            return true;
        }

        // 4. Build text to embed: item title (if any) + "\n" + chunk text
        List<String> textsToEmbed = new ArrayList<>( chunkTexts.size() );
        for ( String chunkText : chunkTexts ) {
            String toEmbed = hasTitle ? snapshot.title().trim() + "\n" + chunkText : chunkText;
            textsToEmbed.add( toEmbed );
        }

        // 5. Call Gemini API OUTSIDE of DB transaction
        log.debug( "Embedding {} chunks for item {}", textsToEmbed.size(), itemId );
        List<float[]> embeddings = embeddingClient.embedDocuments( textsToEmbed );

        if ( embeddings.size() != chunkTexts.size() ) {
            throw new IllegalStateException( String.format( "Embedding count mismatch: expected %d, got %d", chunkTexts.size(), embeddings.size() ) );
        }

        // 6. Replace chunks and set item READY in a short transaction
        replaceChunksAndSetReady( itemId, snapshot.userId(), chunkTexts, embeddings );
        return true;
    }

    @Transactional( readOnly = true )
    protected Optional<ItemSnapshot> loadItemSnapshot( UUID itemId ) {
        return itemRepository.findById( itemId )
                .map( item -> new ItemSnapshot( item.getId(), item.getUserId(), item.getTitle(), item.getPlainText() ) );
    }

    @Transactional
    protected void clearChunksAndSetReady( UUID itemId ) {
        chunkRepository.deleteByItemId( itemId );
        itemRepository.findById( itemId ).ifPresent( item -> {
            item.setStatus( ItemStatus.READY );
            itemRepository.save( item );
        } );
    }

    @Transactional
    protected void replaceChunksAndSetReady( UUID itemId, UUID userId, List<String> chunkTexts, List<float[]> embeddings ) {
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

        itemRepository.findById( itemId ).ifPresent( item -> {
            item.setStatus( ItemStatus.READY );
            itemRepository.save( item );
        } );

        log.info( "Successfully indexed item {} with {} chunks", itemId, chunksToInsert.size() );
    }

    public record ItemSnapshot( UUID id, UUID userId, String title, String plainText ) {}
}
