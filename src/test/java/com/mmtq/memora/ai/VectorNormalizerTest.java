package com.mmtq.memora.ai;

import com.mmtq.memora.ai.util.VectorNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class VectorNormalizerTest {

    @Test
    @DisplayName( "Null or zero vector handled gracefully" )
    void testNullOrZeroVector() {
        assertNull( VectorNormalizer.normalize( null ) );

        float[] zeroVec = new float[]{ 0.0f, 0.0f, 0.0f };
        float[] result = VectorNormalizer.normalize( zeroVec );
        assertArrayEquals( zeroVec, result );
    }

    @Test
    @DisplayName( "Normalized vector has unit length (L2 norm = 1.0)" )
    void testUnitLength() {
        float[] vector = new float[]{ 3.0f, 4.0f }; // norm = 5, normalized = [0.6, 0.8]
        float[] normalized = VectorNormalizer.normalize( vector );

        assertNotNull( normalized );
        assertEquals( 2, normalized.length );
        assertEquals( 0.6f, normalized[0], 1e-5f );
        assertEquals( 0.8f, normalized[1], 1e-5f );

        double norm = Math.sqrt( normalized[0] * normalized[0] + normalized[1] * normalized[1] );
        assertEquals( 1.0, norm, 1e-5 );
    }

    @Test
    @DisplayName( "Higher dimensional vector normalization" )
    void testHigherDimensionalVector() {
        float[] vector = new float[768];
        for ( int i = 0; i < 768; i++ ) {
            vector[i] = ( float ) ( i + 1 );
        }

        float[] normalized = VectorNormalizer.normalize( vector );

        double sumSquares = 0.0;
        for ( float val : normalized ) {
            sumSquares += ( ( double ) val * ( double ) val );
        }

        assertEquals( 1.0, Math.sqrt( sumSquares ), 1e-5 );
    }
}
