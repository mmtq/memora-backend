package com.mmtq.memora.common.specification;

import org.springframework.data.jpa.domain.Specification;

public abstract class CommonSpecification<T> {

    public Specification<T> filterByBoolean( String fieldName, Boolean value ) {
        if ( value == null ) return ( root, query, cb ) -> null;
        return ( root, query, cb ) -> cb.equal( root.get( fieldName ), value );
    }

    public Specification<T> isNull( String fieldName ) {
        return ( root, query, cb ) -> cb.isNull( root.get( fieldName ) );
    }

    public Specification<T> isFalse( String fieldName ) {
        return ( root, query, cb ) -> cb.isFalse( root.get( fieldName ) );
    }

    public Specification<T> isTrue( String fieldName ) {
        return ( root, query, cb ) -> cb.isTrue( root.get( fieldName ) );
    }
}
