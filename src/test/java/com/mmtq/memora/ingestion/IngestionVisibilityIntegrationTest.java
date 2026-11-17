package com.mmtq.memora.ingestion;

import com.mmtq.memora.ai.service.EmbeddingClient;
import com.mmtq.memora.ai.util.VectorNormalizer;
import com.mmtq.memora.common.currentuser.CurrentUserProvider;
import com.mmtq.memora.common.exception.BadRequestException;
import com.mmtq.memora.ingestion.controller.IngestionController;
import com.mmtq.memora.ingestion.dto.IngestionStatsResponse;
import com.mmtq.memora.ingestion.dto.ItemIndexingStatusResponse;
import com.mmtq.memora.ingestion.dto.JobResponse;
import com.mmtq.memora.ingestion.dto.RetryFailedResponse;
import com.mmtq.memora.ingestion.entity.Job;
import com.mmtq.memora.ingestion.enums.JobStatus;
import com.mmtq.memora.ingestion.enums.JobType;
import com.mmtq.memora.ingestion.repository.JobRepository;
import com.mmtq.memora.ingestion.service.JobService;
import com.mmtq.memora.ingestion.service.JobWorker;
import com.mmtq.memora.item.dto.CreateNoteRequest;
import com.mmtq.memora.item.dto.ItemResponse;
import com.mmtq.memora.item.dto.PageResponse;
import com.mmtq.memora.item.dto.UpdateItemRequest;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.model.Item;
import com.mmtq.memora.item.repository.ItemRepository;
import com.mmtq.memora.item.service.ItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class IngestionVisibilityIntegrationTest {

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
        registry.add( "app.jobs.poll-interval-ms", () -> "600000" );
    }

    @TestConfiguration
    static class TestEmbeddingConfig {
        @Bean
        @Primary
        public TestEmbeddingClient testEmbeddingClient() {
            return new TestEmbeddingClient();
        }
    }

    static class TestEmbeddingClient implements EmbeddingClient {
        private boolean fail = false;

        public void setFail( boolean fail ) {
            this.fail = fail;
        }

        @Override
        public List<float[]> embedDocuments( List<String> texts ) {
            if ( fail ) {
                throw new RuntimeException( "Simulated failure" );
            }
            List<float[]> res = new ArrayList<>();
            for ( String text : texts ) {
                float[] vec = new float[768];
                vec[0] = 1.0f;
                res.add( VectorNormalizer.normalize( vec ) );
            }
            return res;
        }

        @Override
        public float[] embedQuery( String text ) {
            if ( fail ) {
                throw new RuntimeException( "Simulated failure" );
            }
            float[] vec = new float[768];
            vec[0] = 1.0f;
            return VectorNormalizer.normalize( vec );
        }
    }

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private com.mmtq.memora.ingestion.repository.ChunkRepository chunkRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobWorker jobWorker;

    @Autowired
    private IngestionController ingestionController;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private TestEmbeddingClient testEmbeddingClient;

    @BeforeEach
    void setup() {
        testEmbeddingClient.setFail( false );
        jobRepository.deleteAll();
        itemRepository.findAll().forEach( item -> chunkRepository.deleteByItemId( item.getId() ) );
        itemRepository.deleteAll();
    }

    @Test
    @DisplayName( "Stats counts after creating a few jobs in different states" )
    void testStatsCounts() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        // Create 2 items
        ItemResponse item1 = itemService.createNote( new CreateNoteRequest( "Stats Note 1", "Content 1" ) );
        ItemResponse item2 = itemService.createNote( new CreateNoteRequest( "Stats Note 2", "Content 2" ) );

        // Item 1: process job to DONE
        jobWorker.pollAndProcessJob();

        // Create a manual RUNNING job and FAILED job for testing stats
        Job runningJob = new Job( JobType.INDEX_ITEM.name(), item1.id(), currentUserId );
        runningJob.setStatus( JobStatus.RUNNING );
        runningJob.setStartedAt( Instant.now() );
        jobRepository.save( runningJob );

        Job failedJob = new Job( JobType.INDEX_ITEM.name(), item2.id(), currentUserId );
        failedJob.setStatus( JobStatus.FAILED );
        failedJob.setLastError( "Mock error" );
        failedJob.setFinishedAt( Instant.now() );
        jobRepository.save( failedJob );

        // Call GET /ingestion/stats
        ResponseEntity<IngestionStatsResponse> statsEntity = ingestionController.getStats();
        IngestionStatsResponse stats = statsEntity.getBody();

        assertNotNull( stats );
        assertTrue( stats.jobs().done() >= 1 );
        assertTrue( stats.jobs().running() >= 1 );
        assertTrue( stats.jobs().failed() >= 1 );
        assertNotNull( stats.items() );
        assertTrue( stats.totalChunks() > 0 );
        assertNotNull( stats.worker() );
        assertNotNull( stats.worker().lastPollAt() );

        // Call GET /ingestion/jobs
        ResponseEntity<PageResponse<JobResponse>> jobsEntity = ingestionController.getJobs( null, 0, 10 );
        PageResponse<JobResponse> page = jobsEntity.getBody();
        assertNotNull( page );
        assertFalse( page.content().isEmpty() );
        JobResponse firstJob = page.content().get( 0 );
        assertNotNull( firstJob.id() );
        assertNotNull( firstJob.type() );
        assertNotNull( firstJob.status() );
    }

    @Test
    @DisplayName( "/items/{id}/indexing lifecycle: before indexing, after indexing, after edit, and after failure" )
    void testItemIndexingStatusLifecycle() {
        // 1. Before indexing:
        CreateNoteRequest createReq = new CreateNoteRequest( "Indexing Note", "Plain text to index." );
        ItemResponse note = itemService.createNote( createReq );
        UUID noteId = note.id();

        ResponseEntity<ItemIndexingStatusResponse> beforeEntity = ingestionController.getItemIndexingStatus( noteId );
        ItemIndexingStatusResponse before = beforeEntity.getBody();

        assertNotNull( before );
        assertEquals( ItemStatus.PROCESSING, before.itemStatus() );
        assertEquals( 0, before.chunkCount() );
        assertNull( before.lastIndexedAt() );
        assertFalse( before.upToDate() );
        assertNotNull( before.latestJob() );
        assertEquals( JobStatus.PENDING, before.latestJob().status() );
        assertNotNull( before.latestJob().nextRetryAt() );

        // 2. After indexing:
        jobWorker.pollAndProcessJob();

        ResponseEntity<ItemIndexingStatusResponse> afterEntity = ingestionController.getItemIndexingStatus( noteId );
        ItemIndexingStatusResponse after = afterEntity.getBody();

        assertNotNull( after );
        assertEquals( ItemStatus.READY, after.itemStatus() );
        assertTrue( after.chunkCount() > 0 );
        assertNotNull( after.lastIndexedAt() );
        assertTrue( after.upToDate() );
        assertNotNull( after.embeddingModel() );
        assertNotNull( after.latestJob() );
        assertEquals( JobStatus.DONE, after.latestJob().status() );

        // 3. After an edit:
        // Sleep 5ms so updated_at is guaranteed strictly later than lastIndexedAt
        try { Thread.sleep( 10 ); } catch ( InterruptedException ignored ) {}
        itemService.updateItem( noteId, new UpdateItemRequest( "Updated Title", "Updated plain text content." ) );

        ResponseEntity<ItemIndexingStatusResponse> afterEditEntity = ingestionController.getItemIndexingStatus( noteId );
        ItemIndexingStatusResponse afterEdit = afterEditEntity.getBody();

        assertNotNull( afterEdit );
        assertEquals( ItemStatus.PROCESSING, afterEdit.itemStatus() );
        assertFalse( afterEdit.upToDate(), "Item should be out of date right after update" );

        // Worker runs again
        jobWorker.pollAndProcessJob();

        ResponseEntity<ItemIndexingStatusResponse> reindexedEntity = ingestionController.getItemIndexingStatus( noteId );
        ItemIndexingStatusResponse reindexed = reindexedEntity.getBody();
        assertNotNull( reindexed );
        assertEquals( ItemStatus.READY, reindexed.itemStatus() );
        assertTrue( reindexed.upToDate(), "Item should be up to date after reindexing completes" );

        // 4. After a failure:
        testEmbeddingClient.setFail( true );
        itemService.updateItem( noteId, new UpdateItemRequest( "Failing Title", "Fails embedding." ) );

        // Run worker until it fails permanently
        Job job = jobRepository.findAllByUserIdAndStatus( currentUserProvider.getCurrentUserId(), JobStatus.PENDING ).get( 0 );
        job.setAttempts( 2 ); // next attempt is 3rd -> permanent failure
        job.setRunAfter( Instant.now().minusSeconds( 1 ) );
        jobRepository.save( job );

        jobWorker.pollAndProcessJob();

        ResponseEntity<ItemIndexingStatusResponse> failEntity = ingestionController.getItemIndexingStatus( noteId );
        ItemIndexingStatusResponse failStatus = failEntity.getBody();

        assertNotNull( failStatus );
        assertEquals( ItemStatus.FAILED, failStatus.itemStatus() );
        assertNotNull( failStatus.latestJob() );
        assertEquals( JobStatus.FAILED, failStatus.latestJob().status() );
        assertNotNull( failStatus.latestJob().lastError() );
    }

    @Test
    @DisplayName( "Retry of a FAILED job works and retry of a DONE job returns 400" )
    void testRetryJob() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        ItemResponse note = itemService.createNote( new CreateNoteRequest( "Retry Note", "Text" ) );

        // Create a FAILED job
        Job failedJob = new Job( JobType.INDEX_ITEM.name(), note.id(), currentUserId );
        failedJob.setStatus( JobStatus.FAILED );
        failedJob.setAttempts( 3 );
        failedJob.setLastError( "Permanent error" );
        failedJob = jobRepository.save( failedJob );

        // Retry the failed job
        ResponseEntity<JobResponse> retryEntity = ingestionController.retryJob( failedJob.getId() );
        JobResponse retried = retryEntity.getBody();

        assertNotNull( retried );
        assertEquals( JobStatus.PENDING, retried.status() );
        assertEquals( 0, retried.attempts() );
        assertNull( retried.lastError() );

        Item reloadedItem = itemRepository.findById( note.id() ).orElseThrow();
        assertEquals( ItemStatus.PROCESSING, reloadedItem.getStatus() );

        // Retry of a DONE job returns 400
        Job doneJob = new Job( JobType.INDEX_ITEM.name(), note.id(), currentUserId );
        doneJob.setStatus( JobStatus.DONE );
        doneJob = jobRepository.save( doneJob );

        final UUID doneJobId = doneJob.getId();
        assertThrows( BadRequestException.class, () -> ingestionController.retryJob( doneJobId ) );

        // Test retry all failed
        Job anotherFailed = new Job( JobType.INDEX_ITEM.name(), note.id(), currentUserId );
        anotherFailed.setStatus( JobStatus.FAILED );
        anotherFailed.setAttempts( 3 );
        jobRepository.save( anotherFailed );

        ResponseEntity<RetryFailedResponse> retryAllEntity = ingestionController.retryAllFailedJobs();
        RetryFailedResponse retryAll = retryAllEntity.getBody();
        assertNotNull( retryAll );
        assertTrue( retryAll.retriedCount() >= 1 );
    }

    @Test
    @DisplayName( "Cleanup deletes only old DONE jobs" )
    void testCleanupDeletesOnlyOldDoneJobs() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        ItemResponse note = itemService.createNote( new CreateNoteRequest( "Cleanup Note", "Text" ) );
        UUID noteId = note.id();

        // 1. Old DONE job (finished 10 days ago) -> SHOULD BE DELETED
        Job oldDone = new Job( JobType.INDEX_ITEM.name(), noteId, currentUserId );
        oldDone.setStatus( JobStatus.DONE );
        oldDone.setFinishedAt( Instant.now().minus( 10, ChronoUnit.DAYS ) );
        oldDone = jobRepository.save( oldDone );

        // 2. Recent DONE job (finished 2 days ago) -> SHOULD NOT BE DELETED
        Job recentDone = new Job( JobType.INDEX_ITEM.name(), noteId, currentUserId );
        recentDone.setStatus( JobStatus.DONE );
        recentDone.setFinishedAt( Instant.now().minus( 2, ChronoUnit.DAYS ) );
        recentDone = jobRepository.save( recentDone );

        // 3. Old FAILED job (finished 10 days ago) -> SHOULD NOT BE DELETED
        Job oldFailed = new Job( JobType.INDEX_ITEM.name(), noteId, currentUserId );
        oldFailed.setStatus( JobStatus.FAILED );
        oldFailed.setFinishedAt( Instant.now().minus( 10, ChronoUnit.DAYS ) );
        oldFailed = jobRepository.save( oldFailed );

        // 4. Old PENDING job (created 10 days ago) -> SHOULD NOT BE DELETED
        Job oldPending = new Job( JobType.INDEX_ITEM.name(), noteId, currentUserId );
        oldPending.setStatus( JobStatus.PENDING );
        oldPending.setCreatedAt( Instant.now().minus( 10, ChronoUnit.DAYS ) );
        oldPending = jobRepository.save( oldPending );

        // Run cleanup with retention of 7 days
        int deletedCount = jobService.deleteOldDoneJobs( 7 );
        assertTrue( deletedCount >= 1 );

        assertFalse( jobRepository.existsById( oldDone.getId() ), "Old DONE job must be deleted" );
        assertTrue( jobRepository.existsById( recentDone.getId() ), "Recent DONE job must not be deleted" );
        assertTrue( jobRepository.existsById( oldFailed.getId() ), "Old FAILED job must never be deleted by cleanup" );
        assertTrue( jobRepository.existsById( oldPending.getId() ), "Old PENDING job must never be deleted by cleanup" );
    }
}
