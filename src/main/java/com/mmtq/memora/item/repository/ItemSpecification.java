package com.mmtq.memora.item.repository;

import com.mmtq.memora.common.specification.CommonSpecification;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.item.model.Item;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ItemSpecification extends CommonSpecification<Item> {

    public Specification<Item> withFilters( UUID userId, ItemType type ) {
        Specification<Item> spec = Specification.where( filterByUserId( userId ) );
        if ( type != null ) {
            spec = spec.and( filterByType( type ) );
        }
        return spec;
    }

    public Specification<Item> withFilters( UUID userId, ItemType type, ItemStatus status, String search ) {
        Specification<Item> spec = Specification.where( filterByUserId( userId ) );
        if ( type != null ) {
            spec = spec.and( filterByType( type ) );
        }
        if ( status != null ) {
            spec = spec.and( filterByStatus( status ) );
        }
        if ( search != null && !search.isBlank() ) {
            spec = spec.and( filterBySearch( search ) );
        }
        return spec;
    }

    public Specification<Item> filterByUserId( UUID userId ) {
        if ( userId == null ) return ( root, query, cb ) -> null;
        return ( root, query, cb ) -> cb.equal( root.get( "userId" ), userId );
    }

    public Specification<Item> filterByType( ItemType type ) {
        if ( type == null ) return ( root, query, cb ) -> null;
        return ( root, query, cb ) -> cb.equal( root.get( "type" ), type );
    }

    public Specification<Item> filterByStatus( ItemStatus status ) {
        if ( status == null ) return ( root, query, cb ) -> null;
        return ( root, query, cb ) -> cb.equal( root.get( "status" ), status );
    }

    public Specification<Item> filterBySearch( String search ) {
        if ( search == null || search.isBlank() ) return ( root, query, cb ) -> null;
        return ( root, query, cb ) -> {
            String likePattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like( cb.lower( root.get( "title" ) ), likePattern ),
                    cb.like( cb.lower( root.get( "description" ) ), likePattern )
            );
        };
    }
}
