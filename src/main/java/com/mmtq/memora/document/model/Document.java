package com.mmtq.memora.document.model;

import com.mmtq.memora.common.model.AuditableEntity;
import com.mmtq.memora.item.model.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table( name = "documents" )
@Getter
@Setter
@NoArgsConstructor
public class Document extends AuditableEntity {

    @Id
    @GeneratedValue( strategy = GenerationType.UUID )
    @Column( name = "id", updatable = false, nullable = false )
    private UUID id;

    @OneToOne( fetch = FetchType.LAZY )
    @JoinColumn( name = "item_id", nullable = false )
    private Item item;

    @Column( name = "storage_key", nullable = false )
    private String storageKey;

    @Column( name = "original_name", nullable = false, length = 500 )
    private String originalName;

    @Column( name = "mime_type", nullable = false, length = 100 )
    private String mimeType;

    @Column( name = "size_bytes", nullable = false )
    private Long sizeBytes;

    @Column( name = "content_hash", nullable = false, length = 64 )
    private String contentHash;

    @Override
    public boolean equals( Object o ) {
        if ( this == o ) return true;
        if ( o == null || Hibernate.getClass( this ) != Hibernate.getClass( o ) ) return false;
        Document that = ( Document ) o;
        return id != null && Objects.equals( id, that.id );
    }

    @Override
    public int hashCode() {
        if ( this.id == null ) return System.identityHashCode( this );
        return Objects.hash( this.id );
    }
}
