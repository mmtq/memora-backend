package com.mmtq.memora.ai.service;

import java.util.List;

public interface EmbeddingClient {

    List<float[]> embedDocuments( List<String> texts );

    float[] embedQuery( String text );
}
