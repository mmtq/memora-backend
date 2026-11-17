package com.mmtq.memora.ai.util;

import java.util.ArrayList;
import java.util.List;

public final class VectorNormalizer {

    private VectorNormalizer() {
    }

    public static float[] normalize( float[] vector ) {
        if ( vector == null ) {
            return null;
        }
        double sumSquares = 0.0;
        for ( float val : vector ) {
            sumSquares += ( ( double ) val * ( double ) val );
        }
        double norm = Math.sqrt( sumSquares );
        if ( norm < 1e-9 ) {
            return vector.clone();
        }
        float[] normalized = new float[vector.length];
        for ( int i = 0; i < vector.length; i++ ) {
            normalized[i] = ( float ) ( ( ( double ) vector[i] ) / norm );
        }
        return normalized;
    }

    public static List<float[]> normalizeAll( List<float[]> vectors ) {
        if ( vectors == null ) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>( vectors.size() );
        for ( float[] vec : vectors ) {
            result.add( normalize( vec ) );
        }
        return result;
    }
}
