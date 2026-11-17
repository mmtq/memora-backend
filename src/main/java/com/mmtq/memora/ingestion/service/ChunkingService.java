package com.mmtq.memora.ingestion.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class ChunkingService {

    private final int maxWords;
    private final int overlapWords;

    public ChunkingService(
            @Value("${app.chunking.max-words:300}") int maxWords,
            @Value("${app.chunking.overlap-words:40}") int overlapWords ) {
        this.maxWords = maxWords > 0 ? maxWords : 300;
        this.overlapWords = ( overlapWords >= 0 && overlapWords < this.maxWords ) ? overlapWords : Math.max( 0, this.maxWords - 1 );
    }

    public List<String> chunk( String text ) {
        if ( text == null || text.isBlank() ) {
            return Collections.emptyList();
        }

        String trimmed = text.trim();
        List<String> allWords = splitIntoWords( trimmed );
        if ( allWords.size() <= maxWords ) {
            return List.of( trimmed );
        }

        // Split text into paragraphs first
        String[] paragraphs = trimmed.split( "\\r?\\n+" );
        List<String> segments = new ArrayList<>();

        for ( String para : paragraphs ) {
            String p = para.trim();
            if ( p.isEmpty() ) {
                continue;
            }

            List<String> paraWords = splitIntoWords( p );
            if ( paraWords.size() <= maxWords ) {
                segments.add( p );
            } else {
                // Split overly long paragraphs by sentences
                String[] sentences = p.split( "(?<=[.!?])\\s+" );
                for ( String sentence : sentences ) {
                    String s = sentence.trim();
                    if ( s.isEmpty() ) {
                        continue;
                    }
                    List<String> sWords = splitIntoWords( s );
                    if ( sWords.size() <= maxWords ) {
                        segments.add( s );
                    } else {
                        // Sentence itself is larger than maxWords: break by word chunks
                        for ( int i = 0; i < sWords.size(); i += maxWords ) {
                            int end = Math.min( i + maxWords, sWords.size() );
                            segments.add( String.join( " ", sWords.subList( i, end ) ) );
                        }
                    }
                }
            }
        }

        // Merge segments into chunks with overlap
        List<String> chunks = new ArrayList<>();
        List<String> currentChunkWords = new ArrayList<>();

        for ( String segment : segments ) {
            List<String> segmentWords = splitIntoWords( segment );
            if ( segmentWords.isEmpty() ) {
                continue;
            }

            int wordIdx = 0;
            while ( wordIdx < segmentWords.size() ) {
                int available = maxWords - currentChunkWords.size();
                if ( available <= 0 ) {
                    chunks.add( String.join( " ", currentChunkWords ) );
                    int overlapCount = Math.min( overlapWords, currentChunkWords.size() );
                    currentChunkWords = new ArrayList<>(
                            currentChunkWords.subList( currentChunkWords.size() - overlapCount, currentChunkWords.size() )
                    );
                    available = maxWords - currentChunkWords.size();
                }

                int toTake = Math.min( available, segmentWords.size() - wordIdx );
                currentChunkWords.addAll( segmentWords.subList( wordIdx, wordIdx + toTake ) );
                wordIdx += toTake;
            }
        }

        if ( !currentChunkWords.isEmpty() ) {
            String lastChunk = String.join( " ", currentChunkWords );
            if ( chunks.isEmpty() || !chunks.get( chunks.size() - 1 ).equals( lastChunk ) ) {
                chunks.add( lastChunk );
            }
        }

        return chunks;
    }

    private List<String> splitIntoWords( String s ) {
        if ( s == null || s.isBlank() ) {
            return Collections.emptyList();
        }
        return Arrays.stream( s.trim().split( "\\s+" ) )
                .filter( w -> !w.isEmpty() )
                .toList();
    }
}
