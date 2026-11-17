package com.mmtq.memora.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mmtq.memora.ai.util.VectorNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Service
public class GeminiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger( GeminiEmbeddingClient.class );
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final int batchSize;

    public GeminiEmbeddingClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-embedding-001}") String model,
            @Value("${gemini.dimensions:768}") int dimensions,
            @Value("${gemini.batch-size:50}") int batchSize ) {
        RestClient.Builder builder = ( restClientBuilder != null ) ? restClientBuilder : RestClient.builder();
        this.restClient = builder.baseUrl( BASE_URL ).build();
        this.objectMapper = ( objectMapper != null ) ? objectMapper : new ObjectMapper();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "gemini-embedding-001";
        this.dimensions = dimensions > 0 ? dimensions : 768;
        this.batchSize = batchSize > 0 ? batchSize : 50;
    }

    @Override
    public List<float[]> embedDocuments( List<String> texts ) {
        if ( texts == null || texts.isEmpty() ) {
            return Collections.emptyList();
        }
        ensureApiKey();

        List<float[]> allEmbeddings = new ArrayList<>( texts.size() );
        for ( int i = 0; i < texts.size(); i += batchSize ) {
            int end = Math.min( i + batchSize, texts.size() );
            List<String> batch = texts.subList( i, end );
            List<float[]> batchEmbeddings = embedDocumentBatch( batch );
            allEmbeddings.addAll( batchEmbeddings );
        }

        return VectorNormalizer.normalizeAll( allEmbeddings );
    }

    @Override
    public float[] embedQuery( String text ) {
        if ( text == null || text.isBlank() ) {
            return new float[dimensions];
        }
        ensureApiKey();

        String normalizedModel = normalizeModelName( model );
        String endpoint = String.format( "/%s:embedContent?key=%s", normalizedModel, apiKey );

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put( "model", normalizedModel );
        ObjectNode contentNode = requestBody.putObject( "content" );
        ArrayNode partsArray = contentNode.putArray( "parts" );
        partsArray.addObject().put( "text", text );
        requestBody.put( "taskType", "RETRIEVAL_QUERY" );
        requestBody.put( "outputDimensionality", dimensions );

        String requestJson = requestBody.toString();

        String responseBody = executeWithRetry( () ->
                restClient.post()
                        .uri( endpoint )
                        .contentType( MediaType.APPLICATION_JSON )
                        .body( requestJson )
                        .retrieve()
                        .body( String.class )
        );

        if ( responseBody == null || responseBody.isBlank() ) {
            throw new IllegalStateException( "Empty response received from Gemini embedContent API" );
        }

        JsonNode responseNode;
        try {
            responseNode = objectMapper.readTree( responseBody );
        } catch ( Exception e ) {
            throw new IllegalStateException( "Failed to parse Gemini response: " + responseBody, e );
        }

        JsonNode valuesNode = responseNode.path( "embedding" ).path( "values" );
        if ( !valuesNode.isArray() || valuesNode.isEmpty() ) {
            throw new IllegalStateException( "No embedding values in Gemini response: " + responseNode );
        }

        float[] rawVector = new float[valuesNode.size()];
        for ( int i = 0; i < valuesNode.size(); i++ ) {
            rawVector[i] = ( float ) valuesNode.get( i ).asDouble();
        }

        return VectorNormalizer.normalize( rawVector );
    }

    private List<float[]> embedDocumentBatch( List<String> batchTexts ) {
        String normalizedModel = normalizeModelName( model );
        String endpoint = String.format( "/%s:batchEmbedContents?key=%s", normalizedModel, apiKey );

        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode requestsArray = requestBody.putArray( "requests" );

        for ( String text : batchTexts ) {
            ObjectNode req = requestsArray.addObject();
            req.put( "model", normalizedModel );
            ObjectNode content = req.putObject( "content" );
            ArrayNode parts = content.putArray( "parts" );
            parts.addObject().put( "text", text != null ? text : "" );
            req.put( "taskType", "RETRIEVAL_DOCUMENT" );
            req.put( "outputDimensionality", dimensions );
        }

        String requestJson = requestBody.toString();

        String responseBody = executeWithRetry( () ->
                restClient.post()
                        .uri( endpoint )
                        .contentType( MediaType.APPLICATION_JSON )
                        .body( requestJson )
                        .retrieve()
                        .body( String.class )
        );

        if ( responseBody == null || responseBody.isBlank() ) {
            throw new IllegalStateException( "Empty response received from Gemini batchEmbedContents API" );
        }

        JsonNode responseNode;
        try {
            responseNode = objectMapper.readTree( responseBody );
        } catch ( Exception e ) {
            throw new IllegalStateException( "Failed to parse Gemini response: " + responseBody, e );
        }

        JsonNode embeddingsArray = responseNode.path( "embeddings" );
        if ( !embeddingsArray.isArray() ) {
            throw new IllegalStateException( "Expected embeddings array in Gemini response: " + responseNode );
        }

        List<float[]> results = new ArrayList<>( embeddingsArray.size() );
        for ( JsonNode embeddingObj : embeddingsArray ) {
            JsonNode values = embeddingObj.path( "values" );
            float[] vec = new float[values.size()];
            for ( int i = 0; i < values.size(); i++ ) {
                vec[i] = ( float ) values.get( i ).asDouble();
            }
            results.add( vec );
        }

        return results;
    }

    private <T> T executeWithRetry( Supplier<T> action ) {
        int maxRetries = 3;
        long backoffMs = 1000L;

        for ( int attempt = 1; attempt <= maxRetries; attempt++ ) {
            try {
                return action.get();
            } catch ( RestClientResponseException ex ) {
                HttpStatusCode status = ex.getStatusCode();
                boolean isRetryable = status.value() == 429 || status.is5xxServerError();
                if ( !isRetryable || attempt == maxRetries ) {
                    log.error( "Gemini API call failed with status {}: {}", status.value(), ex.getResponseBodyAsString() );
                    throw new RuntimeException( "Gemini API error (" + status.value() + "): " + ex.getResponseBodyAsString(), ex );
                }
                log.warn( "Gemini API returned status {}, retrying in {} ms (attempt {}/{})", status.value(), backoffMs, attempt, maxRetries );
                sleep( backoffMs );
                backoffMs *= 2;
            } catch ( ResourceAccessException ex ) {
                if ( attempt == maxRetries ) {
                    throw new RuntimeException( "Gemini API network timeout after retries: " + ex.getMessage(), ex );
                }
                log.warn( "Gemini API network error, retrying in {} ms (attempt {}/{})", backoffMs, attempt, maxRetries );
                sleep( backoffMs );
                backoffMs *= 2;
            }
        }

        throw new IllegalStateException( "Gemini API retry loop exhausted unexpectedly" );
    }

    private void sleep( long ms ) {
        try {
            Thread.sleep( ms );
        } catch ( InterruptedException e ) {
            Thread.currentThread().interrupt();
            throw new RuntimeException( "Retry sleep interrupted", e );
        }
    }

    private void ensureApiKey() {
        if ( apiKey == null || apiKey.isBlank() ) {
            throw new IllegalStateException( "GEMINI_API_KEY is not set" );
        }
    }

    private String normalizeModelName( String rawModel ) {
        if ( rawModel.startsWith( "models/" ) ) {
            return rawModel;
        }
        return "models/" + rawModel;
    }
}
