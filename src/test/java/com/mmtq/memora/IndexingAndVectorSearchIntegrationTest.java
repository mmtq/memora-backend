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
import org.junit.jupiter.api.BeforeEach;
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
        registry.add( "spring.datasource.driver-class-name", () -> "org.postgresql.Driver" );
        registry.add( "spring.jpa.hibernate.ddl-auto", () -> "validate" );
        registry.add( "spring.flyway.enabled", () -> "true" );
        // Set high poll interval so test manually triggers the worker
        registry.add( "app.jobs.poll-interval-ms", () -> "600000" );
        registry.add( "search.min-similarity", () -> "0.6" );
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
            if ( text == null || text.isBlank() ) {
                return vec;
            }
            String[] words = text.toLowerCase().split( "\\W+" );
            for ( String word : words ) {
                if ( word.isBlank() ) continue;
                int idx = Math.abs( word.hashCode() ) % 768;
                vec[idx] += 1.0f;
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

    @BeforeEach
    void setUp() {
        fakeEmbeddingClient.setShouldFail( false );
        jobRepository.deleteAll();
        chunkRepository.deleteAll();
        itemRepository.deleteAll();
    }

    @Test
    @DisplayName( "Event flow: Item creation publishes ItemChangedEvent and enqueues INDEX_ITEM job" )
    void testItemEventToJobEnqueueFlow() {
        CreateNoteRequest createReq = new CreateNoteRequest(
                "Event Flow Note",
                "Checking transactional event listener enqueues job."
        );
        ItemResponse createdItem = itemService.createNote( createReq );
        assertNotNull( createdItem.id() );
        assertEquals( ItemStatus.PROCESSING, createdItem.status() );

        List<Job> jobs = jobRepository.findAll().stream()
                .filter( j -> j.getTargetId().equals( createdItem.id() ) )
                .toList();

        assertEquals( 1, jobs.size() );
        Job enqueuedJob = jobs.get( 0 );
        assertEquals( "INDEX_ITEM", enqueuedJob.getType() );
        assertEquals( JobStatus.PENDING, enqueuedJob.getStatus() );
        assertEquals( 0, enqueuedJob.getAttempts() );
    }

    @Test
    @DisplayName( "Full pipeline: create note -> index chunks -> search -> edit -> delete -> failure retry" )
    void testFullIndexingAndSearchLifecycle() {
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

    @Test
    @DisplayName( "A query unrelated to all items returns an empty list" )
    void testUnrelatedQueryReturnsEmptyList() {
        // 1. Create and index an item about Quantum Computing
        CreateNoteRequest createReq = new CreateNoteRequest(
                "Quantum Computing Qubits",
                "Superconducting qubits and quantum error correction principles."
        );
        ItemResponse createdItem = itemService.createNote( createReq );
        jobWorker.pollAndProcessJob();

        Item processedItem = itemRepository.findById( createdItem.id() ).orElseThrow();
        assertEquals( ItemStatus.READY, processedItem.getStatus() );

        // 2. Search for an unrelated query with no shared vocabulary
        // Cosine similarity will be below search.min-similarity (0.6)
        List<SearchResultItem> results = searchService.search(
                "Baking Artisan Bread Sourdough Flour",
                null,
                10
        );

        // 3. Verify that chunks below threshold are discarded and an empty list is returned
        assertTrue( results.isEmpty(), "Unrelated query should return empty list when similarity is below threshold" );
    }

    @Test
    @DisplayName( "A status change leaves updated_at unchanged" )
    void testStatusChangeLeavesUpdatedAtUnchanged() throws Exception {
        CreateNoteRequest createReq = new CreateNoteRequest(
                "Timestamp Status Test",
                "Testing that status changes do not modify updated_at"
        );
        ItemResponse created = itemService.createNote( createReq );
        UUID itemId = created.id();

        Item initialItem = itemRepository.findById( itemId ).orElseThrow();
        Instant initialUpdatedAt = initialItem.getUpdatedAt();
        assertNotNull( initialUpdatedAt );

        Thread.sleep( 100 );

        // 1. Process job via worker -> sets status to READY via updateStatus
        jobWorker.pollAndProcessJob();

        Item readyItem = itemRepository.findById( itemId ).orElseThrow();
        assertEquals( ItemStatus.READY, readyItem.getStatus() );
        assertEquals( initialUpdatedAt, readyItem.getUpdatedAt(), "updated_at must NOT change when status changes to READY" );

        Thread.sleep( 100 );

        // 2. Change status to FAILED via itemRepository.updateStatus
        itemRepository.updateStatus( itemId, ItemStatus.FAILED );

        Item failedItem = itemRepository.findById( itemId ).orElseThrow();
        assertEquals( ItemStatus.FAILED, failedItem.getStatus() );
        assertEquals( initialUpdatedAt, failedItem.getUpdatedAt(), "updated_at must NOT change when status changes to FAILED" );

        Thread.sleep( 100 );

        // 3. Reindex marks status PROCESSING
        itemService.reindexItem( itemId );

        Item processingItem = itemRepository.findById( itemId ).orElseThrow();
        assertEquals( ItemStatus.PROCESSING, processingItem.getStatus() );
        assertEquals( initialUpdatedAt, processingItem.getUpdatedAt(), "updated_at must NOT change when reindex marks status PROCESSING" );
    }

    @Test
    @DisplayName( "Editing the description changes updated_at" )
    void testEditingDescriptionChangesUpdatedAt() throws Exception {
        CreateNoteRequest createReq = new CreateNoteRequest(
                "Original Title",
                "Original Description"
        );
        ItemResponse created = itemService.createNote( createReq );
        UUID itemId = created.id();

        Item initialItem = itemRepository.findById( itemId ).orElseThrow();
        Instant initialUpdatedAt = initialItem.getUpdatedAt();
        assertNotNull( initialUpdatedAt );

        Thread.sleep( 100 );

        // Edit description
        UpdateItemRequest updateReq = new UpdateItemRequest(
                "Original Title",
                "Updated Description with new details"
        );
        itemService.updateItem( itemId, updateReq );

        Item updatedItem = itemRepository.findById( itemId ).orElseThrow();
        assertEquals( "Updated Description with new details", updatedItem.getDescription() );
        assertTrue( updatedItem.getUpdatedAt().isAfter( initialUpdatedAt ), "updated_at MUST change when user edits description" );
    }

    private Job findJobForTarget( UUID targetId ) {
        return jobRepository.findAll().stream()
                .filter( j -> j.getTargetId().equals( targetId ) )
                .findFirst()
                .orElseThrow();
    }
}
