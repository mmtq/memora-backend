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
        this.overlapWords = overlapWords >= 0 ? overlapWords : 40;
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
        List<String> currentWords = new ArrayList<>();

        for ( String segment : segments ) {
            List<String> segmentWords = splitIntoWords( segment );
            if ( segmentWords.isEmpty() ) {
                continue;
            }

            if ( currentWords.isEmpty() ) {
                currentWords.addAll( segmentWords );
            } else if ( currentWords.size() + segmentWords.size() <= maxWords ) {
                currentWords.addAll( segmentWords );
            } else {
                // Finalize previous chunk
                chunks.add( String.join( " ", currentWords ) );

                // Calculate overlap from previous chunk
                int overlapCount = Math.min( overlapWords, currentWords.size() );
                List<String> overlap = new ArrayList<>( currentWords.subList( currentWords.size() - overlapCount, currentWords.size() ) );

                currentWords = new ArrayList<>( overlap );
                currentWords.addAll( segmentWords );
            }
        }

        if ( !currentWords.isEmpty() ) {
            String lastChunk = String.join( " ", currentWords );
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
