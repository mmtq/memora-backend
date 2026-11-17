package com.mmtq.memora.ingestion.repository;

import com.mmtq.memora.ingestion.entity.Chunk;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class ChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChunkRepository( JdbcTemplate jdbcTemplate ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void deleteByItemId( UUID itemId ) {
        jdbcTemplate.update( "DELETE FROM chunks WHERE item_id = ?", itemId );
    }

    public void insertChunks( List<Chunk> chunks ) {
        if ( chunks == null || chunks.isEmpty() ) {
            return;
        }

        String sql = """
                INSERT INTO chunks (id, item_id, user_id, chunk_index, text, embedding, embedding_model, created_at)
                VALUES (?, ?, ?, ?, ?, ?::vector, ?, ?)
                """;

        jdbcTemplate.batchUpdate( sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues( PreparedStatement ps, int i ) throws SQLException {
                Chunk chunk = chunks.get( i );
                ps.setObject( 1, chunk.id() );
                ps.setObject( 2, chunk.itemId() );
                ps.setObject( 3, chunk.userId() );
                ps.setInt( 4, chunk.chunkIndex() );
                ps.setString( 5, chunk.text() );
                ps.setString( 6, formatVector( chunk.embedding() ) );
                ps.setString( 7, chunk.embeddingModel() );
                ps.setTimestamp( 8, Timestamp.from( chunk.createdAt() ) );
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        } );
    }

    public int countByItemId( UUID itemId ) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE item_id = ?",
                Integer.class,
                itemId
        );
        return count != null ? count : 0;
    }

    public static String formatVector( float[] embedding ) {
        if ( embedding == null || embedding.length == 0 ) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder( embedding.length * 8 + 2 );
        sb.append( '[' );
        for ( int i = 0; i < embedding.length; i++ ) {
            if ( i > 0 ) {
                sb.append( ',' );
            }
            sb.append( embedding[i] );
        }
        sb.append( ']' );
        return sb.toString();
    }
}
