package com.mmtq.memora.ingestion;

import com.mmtq.memora.ingestion.service.ChunkingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkingServiceTest {

    @Test
    @DisplayName( "Empty or blank text returns empty chunk list" )
    void testEmptyText() {
        ChunkingService service = new ChunkingService( 300, 40 );

        assertTrue( service.chunk( null ).isEmpty() );
        assertTrue( service.chunk( "" ).isEmpty() );
        assertTrue( service.chunk( "   \n\t   " ).isEmpty() );
    }

    @Test
    @DisplayName( "Text smaller than chunk size returns exactly one chunk" )
    void testTextSmallerThanChunkSize() {
        ChunkingService service = new ChunkingService( 300, 40 );
        String text = "This is a note smaller than the maximum chunk size.";

        List<String> chunks = service.chunk( text );

        assertEquals( 1, chunks.size() );
        assertEquals( text, chunks.get( 0 ) );
    }

    @Test
    @DisplayName( "Short text returns exactly one chunk" )
    void testShortText() {
        ChunkingService service = new ChunkingService( 300, 40 );
        String text = "This is a short note about Spring Boot and pgvector.";

        List<String> chunks = service.chunk( text );

        assertEquals( 1, chunks.size() );
        assertEquals( text, chunks.get( 0 ) );
    }

    @Test
    @DisplayName( "Long text is broken into multiple chunks" )
    void testLongText() {
        ChunkingService service = new ChunkingService( 10, 3 );

        StringBuilder sb = new StringBuilder();
        for ( int i = 1; i <= 35; i++ ) {
            sb.append( "word" ).append( i ).append( " " );
            if ( i % 7 == 0 ) {
                sb.append( "\n\n" );
            }
        }

        List<String> chunks = service.chunk( sb.toString() );

        assertTrue( chunks.size() >= 3 );
        for ( String chunk : chunks ) {
            assertFalse( chunk.isBlank() );
        }
    }

    @Test
    @DisplayName( "Handles Unicode text cleanly across non-Latin scripts and emoji" )
    void testUnicodeText() {
        ChunkingService service = new ChunkingService( 6, 2 );
        String unicodeText = "Chào mừng bạn đến với Memora! 🚀 Hệ thống tìm kiếm thông minh kết hợp AI và Postgres pgvector.";

        List<String> chunks = service.chunk( unicodeText );

        assertFalse( chunks.isEmpty() );
        for ( String chunk : chunks ) {
            assertFalse( chunk.isBlank() );
        }
    }

    @Test
    @DisplayName( "Handles text with no punctuation without breaking mid-word" )
    void testNoPunctuation() {
        ChunkingService service = new ChunkingService( 5, 2 );
        String textWithoutPunctuation = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu";

        List<String> chunks = service.chunk( textWithoutPunctuation );

        assertTrue( chunks.size() >= 2 );
        for ( String chunk : chunks ) {
            String[] words = chunk.split( "\\s+" );
            assertTrue( words.length <= 5 );
            // Verify words are not severed
            for ( String word : words ) {
                assertTrue( textWithoutPunctuation.contains( word ) );
            }
        }
    }

    @Test
    @DisplayName( "Consecutive chunks have specified word overlap" )
    void testOverlap() {
        ChunkingService service = new ChunkingService( 5, 2 );

        String text = "one two three.\n\nfour five six.\n\nseven eight nine ten eleven twelve.";

        List<String> chunks = service.chunk( text );

        assertTrue( chunks.size() >= 2 );

        String firstChunk = chunks.get( 0 );
        String secondChunk = chunks.get( 1 );

        String[] firstWords = firstChunk.split( "\\s+" );
        String[] secondWords = secondChunk.split( "\\s+" );

        assertTrue( firstWords.length > 0 );
        assertTrue( secondWords.length > 0 );

        assertEquals( firstWords[firstWords.length - 2], secondWords[0] );
        assertEquals( firstWords[firstWords.length - 1], secondWords[1] );
    }
}
