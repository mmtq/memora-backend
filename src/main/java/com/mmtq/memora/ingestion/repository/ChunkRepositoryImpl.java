package com.mmtq.memora.ingestion.repository;

import com.mmtq.memora.ingestion.entity.Chunk;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChunkRepositoryImpl implements ChunkRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    @Override
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
                ps.setObject( 1, chunk.getId() );
                ps.setObject( 2, chunk.getItemId() );
                ps.setObject( 3, chunk.getUserId() );
                ps.setInt( 4, chunk.getChunkIndex() );
                ps.setString( 5, chunk.getText() );
                ps.setString( 6, formatVector( chunk.getEmbedding() ) );
                ps.setString( 7, chunk.getEmbeddingModel() );
                ps.setTimestamp( 8, Timestamp.from( chunk.getCreatedAt() ) );
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        } );
    }

    @Override
    public void deleteAllChunks() {
        jdbcTemplate.update( "DELETE FROM chunks" );
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
