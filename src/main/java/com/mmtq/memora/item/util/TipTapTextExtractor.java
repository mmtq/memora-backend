package com.mmtq.memora.item.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmtq.memora.common.exception.BadRequestException;

import java.util.ArrayList;
import java.util.List;

public final class TipTapTextExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TipTapTextExtractor() {
    }

    public static String extract( String tipTapJson ) {
        if ( tipTapJson == null || tipTapJson.isBlank() ) {
            return "";
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree( tipTapJson );
            List<String> blocks = new ArrayList<>();
            extractBlocks( root, blocks );
            return String.join( "\n", blocks );
        } catch ( JsonProcessingException e ) {
            throw new BadRequestException( "Invalid TipTap JSON content: " + e.getOriginalMessage(), e );
        }
    }

    private static void extractBlocks( JsonNode node, List<String> blocks ) {
        if ( node == null || !node.isObject() ) {
            return;
        }

        String type = node.path( "type" ).asText( "" );
        JsonNode content = node.path( "content" );

        if ( isStructuralContainer( type ) || hasBlockChildren( content ) ) {
            if ( content.isArray() ) {
                for ( JsonNode child : content ) {
                    extractBlocks( child, blocks );
                }
            }
            return;
        }

        StringBuilder blockText = new StringBuilder();
        collectInlineText( node, blockText );
        String text = blockText.toString().trim();
        if ( !text.isEmpty() ) {
            blocks.add( text );
        }
    }

    private static boolean isStructuralContainer( String type ) {
        return "doc".equalsIgnoreCase( type )
                || "bulletList".equalsIgnoreCase( type )
                || "orderedList".equalsIgnoreCase( type )
                || "table".equalsIgnoreCase( type )
                || "tableRow".equalsIgnoreCase( type );
    }

    private static boolean hasBlockChildren( JsonNode content ) {
        if ( content == null || !content.isArray() ) {
            return false;
        }
        for ( JsonNode child : content ) {
            String childType = child.path( "type" ).asText( "" );
            if ( !"text".equalsIgnoreCase( childType ) && !child.has( "text" ) ) {
                return true;
            }
        }
        return false;
    }

    private static void collectInlineText( JsonNode node, StringBuilder sb ) {
        if ( node == null ) {
            return;
        }

        if ( node.has( "text" ) ) {
            sb.append( node.get( "text" ).asText() );
        }

        JsonNode content = node.path( "content" );
        if ( content.isArray() ) {
            for ( JsonNode child : content ) {
                collectInlineText( child, sb );
            }
        }
    }
}
