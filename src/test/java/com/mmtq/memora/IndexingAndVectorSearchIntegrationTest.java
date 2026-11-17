package com.mmtq.memora;

import com.mmtq.memora.ai.service.EmbeddingClient;
import com.mmtq.memora.ai.util.VectorNormalizer;
import com.mmtq.memora.ingestion.entity.Job;
import com.mmtq.memora.ingestion.enums.JobStatus;
import com.mmtq.memora.ingestion.repository.ChunkRepository;
import com.mmtq.memora.ingestion.repository.JobRepository;
import com.mmtq.memora.ingestion.service.JobWorker;
import com.mmtq.memora.item.dto.CreateNoteRequest;
import com.mmtq.memora.item.dto.ItemResponse;
import com.mmtq.memora.item.dto.UpdateItemRequest;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.model.Item;
import com.mmtq.memora.item.repository.ItemRepository;
import com.mmtq.memora.item.service.ItemService;
import com.mmtq.memora.search.dto.SearchResultItem;
import com.mmtq.memora.search.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class IndexingAndVectorSearchIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>( "pgvector/pgvector:pg16" )
            .withDatabaseName( "memora_test" )
            .withUsername( "test" )
            .withPassword( "test" );

    @DynamicPropertySource
    static void configureProperties( DynamicPropertyRegistry registry ) {
        registry.add( "spring.datasource.url", postgres::getJdbcUrl );
        registry.add( "spring.datasource.username", postgres::getUsername );
        registry.add( "spring.datasource.password", postgres::getPassword );
        registry.add( "spring.flyway.enabled", () -> "true" );
        // Set high poll interval so test manually triggers the worker
        registry.add( "app.jobs.poll-interval-ms", () -> "600000" );
    }

    @TestConfiguration
    static class TestEmbeddingConfig {
        @Bean
        @Primary
        public FakeEmbeddingClient fakeEmbeddingClient() {
            return new FakeEmbeddingClient();
        }
    }

    static class FakeEmbeddingClient implements EmbeddingClient {
        private boolean shouldFail = false;

        public void setShouldFail( boolean shouldFail ) {
            this.shouldFail = shouldFail;
        }

        @Override
        public List<float[]> embedDocuments( List<String> texts ) {
            if ( shouldFail ) {
                throw new RuntimeException( "Simulated Gemini embedding API failure" );
            }
            List<float[]> embeddings = new ArrayList<>( texts.size() );
            for ( String text : texts ) {
                embeddings.add( generateDeterministicVector( text ) );
            }
            return embeddings;
        }

        @Override
        public float[] embedQuery( String text ) {
            if ( shouldFail ) {
                throw new RuntimeException( "Simulated Gemini embedding API failure" );
            }
            return generateDeterministicVector( text );
        }

        private float[] generateDeterministicVector( String text ) {
            float[] vec = new float[768];
            int hash = text != null ? text.hashCode() : 0;
            for ( int i = 0; i < 768; i++ ) {
                vec[i] = ( float ) Math.sin( hash + i );
            }
            return VectorNormalizer.normalize( vec );
        }
    }

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobWorker jobWorker;

    @Autowired
    private SearchService searchService;

    @Autowired
    private FakeEmbeddingClient fakeEmbeddingClient;

    @Test
    @DisplayName( "Full pipeline: create note -> index chunks -> search -> edit -> delete -> failure retry" )
    void testFullIndexingAndSearchLifecycle() {
        fakeEmbeddingClient.setShouldFail( false );

        // 1. Create a note
        CreateNoteRequest createReq = new CreateNoteRequest(
                "Kubernetes Cluster",
                "Kubernetes manages containerized workloads and services across multiple nodes."
        );
        ItemResponse createdItem = itemService.createNote( createReq );
        UUID itemId = createdItem.id();

        assertEquals( ItemStatus.PROCESSING, createdItem.status() );

        // 2. Run the worker
        jobWorker.pollAndProcessJob();

        Item processedItem = itemRepository.findById( itemId ).orElseThrow();
        assertEquals( ItemStatus.READY, processedItem.getStatus() );

        int chunkCount = chunkRepository.countByItemId( itemId );
        assertTrue( chunkCount > 0, "Chunks should exist for processed item" );

        // 3. Search: searching for the exact text gives high cosine similarity
        List<SearchResultItem> searchResults = searchService.search(
                "Kubernetes Cluster\nKubernetes manages containerized workloads and services across multiple nodes.",
                null,
                10
        );
        assertFalse( searchResults.isEmpty() );
        SearchResultItem topResult = searchResults.get( 0 );
        assertEquals( itemId, topResult.itemId() );
        assertTrue( topResult.score() > 0.9, "Score should be close to 1.0 for matching text" );
        assertNotNull( topResult.snippet() );

        // 4. Edit item: re-indexes and replaces chunks
        UpdateItemRequest updateReq = new UpdateItemRequest(
                "Kubernetes Production",
                "Production Kubernetes requires high availability and monitoring with Prometheus."
        );
        ItemResponse updatedItem = itemService.updateItem( itemId, updateReq );
        assertEquals( ItemStatus.PROCESSING, updatedItem.status() );

        jobWorker.pollAndProcessJob();

        Item reindexedItem = itemRepository.findById( itemId ).orElseThrow();
        assertEquals( ItemStatus.READY, reindexedItem.getStatus() );
        assertTrue( chunkRepository.countByItemId( itemId ) > 0 );

        // 5. Delete item: cascades and removes chunks
        itemService.deleteItem( itemId );
        assertFalse( itemRepository.existsById( itemId ) );
        assertEquals( 0, chunkRepository.countByItemId( itemId ) );

        // 6. Test failing embedding client leads to retries and then FAILED
        CreateNoteRequest failReq = new CreateNoteRequest(
                "Faulty Note",
                "This note will fail embedding"
        );
        ItemResponse failItem = itemService.createNote( failReq );
        UUID failItemId = failItem.id();

        fakeEmbeddingClient.setShouldFail( true );

        // Attempt 1
        jobWorker.pollAndProcessJob();
        Job jobAfter1 = findJobForTarget( failItemId );
        assertEquals( 1, jobAfter1.getAttempts() );
        assertEquals( JobStatus.PENDING, jobAfter1.getStatus() );

        // Force run_after to past so worker claims it on next poll
        jobAfter1.setRunAfter( Instant.now().minusSeconds( 1 ) );
        jobRepository.save( jobAfter1 );

        // Attempt 2
        jobWorker.pollAndProcessJob();
        Job jobAfter2 = findJobForTarget( failItemId );
        assertEquals( 2, jobAfter2.getAttempts() );
        assertEquals( JobStatus.PENDING, jobAfter2.getStatus() );

        // Force run_after to past
        jobAfter2.setRunAfter( Instant.now().minusSeconds( 1 ) );
        jobRepository.save( jobAfter2 );

        // Attempt 3 (max_attempts = 3 reached -> FAILED)
        jobWorker.pollAndProcessJob();
        Job jobAfter3 = findJobForTarget( failItemId );
        assertEquals( 3, jobAfter3.getAttempts() );
        assertEquals( JobStatus.FAILED, jobAfter3.getStatus() );

        Item failedDbItem = itemRepository.findById( failItemId ).orElseThrow();
        assertEquals( ItemStatus.FAILED, failedDbItem.getStatus() );

        fakeEmbeddingClient.setShouldFail( false );
    }

    private Job findJobForTarget( UUID targetId ) {
        return jobRepository.findAll().stream()
                .filter( j -> j.getTargetId().equals( targetId ) )
                .findFirst()
                .orElseThrow();
    }
}
