package com.mmtq.memora.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiEmbeddingClientTest {

    private MockRestServiceServer mockServer;
    private GeminiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo( restClientBuilder ).build();
        ObjectMapper mapper = new ObjectMapper();
        client = new GeminiEmbeddingClient(
                restClientBuilder,
                mapper,
                "test-api-key",
                "gemini-embedding-001",
                768,
                50
        );
    }

    @Test
    @DisplayName("embedQuery sends valid JSON and parses embedding")
    void testEmbedQuery() {
        String mockResponse = """
                {
                  "embedding": {
                    "values": [0.1, 0.2, 0.3]
                  }
                }
                """;

        mockServer.expect( requestTo( "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=test-api-key" ) )
                .andExpect( method( HttpMethod.POST ) )
                .andExpect( content().contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( jsonPath( "$.model" ).value( "models/gemini-embedding-001" ) )
                .andExpect( jsonPath( "$.content.parts[0].text" ).value( "test query" ) )
                .andExpect( jsonPath( "$.taskType" ).value( "RETRIEVAL_QUERY" ) )
                .andExpect( jsonPath( "$.outputDimensionality" ).value( 768 ) )
                .andRespond( withSuccess( mockResponse, MediaType.APPLICATION_JSON ) );

        float[] embedding = client.embedQuery( "test query" );

        mockServer.verify();
        assertThat( embedding ).isNotNull();
        assertThat( embedding.length ).isEqualTo( 3 );
    }

    @Test
    @DisplayName("embedDocuments sends valid batch JSON and parses embeddings")
    void testEmbedDocuments() {
        String mockResponse = """
                {
                  "embeddings": [
                    { "values": [0.1, 0.2, 0.3] },
                    { "values": [0.4, 0.5, 0.6] }
                  ]
                }
                """;

        mockServer.expect( requestTo( "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents?key=test-api-key" ) )
                .andExpect( method( HttpMethod.POST ) )
                .andExpect( content().contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( jsonPath( "$.requests[0].model" ).value( "models/gemini-embedding-001" ) )
                .andExpect( jsonPath( "$.requests[0].content.parts[0].text" ).value( "text one" ) )
                .andExpect( jsonPath( "$.requests[1].content.parts[0].text" ).value( "text two" ) )
                .andRespond( withSuccess( mockResponse, MediaType.APPLICATION_JSON ) );

        List<float[]> embeddings = client.embedDocuments( List.of( "text one", "text two" ) );

        mockServer.verify();
        assertThat( embeddings ).hasSize( 2 );
        assertThat( embeddings.get( 0 ).length ).isEqualTo( 3 );
        assertThat( embeddings.get( 1 ).length ).isEqualTo( 3 );
    }
}
