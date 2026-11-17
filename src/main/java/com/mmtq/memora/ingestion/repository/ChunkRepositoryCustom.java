package com.mmtq.memora.ingestion.repository;

import com.mmtq.memora.ingestion.entity.Chunk;

import java.util.List;

public interface ChunkRepositoryCustom {

    void insertChunks( List<Chunk> chunks );

    void deleteAllChunks();
}
