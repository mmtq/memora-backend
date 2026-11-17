package com.mmtq.memora.item.model;

import com.mmtq.memora.common.model.AuditableEntity;
import com.mmtq.memora.document.model.Document;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.enums.ItemType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table( name = "items" )
@Getter
@Setter
@NoArgsConstructor
public class Item extends AuditableEntity {

    @Id
    @GeneratedValue( strategy = GenerationType.UUID )
    @Column( name = "id", updatable = false, nullable = false )
    private UUID id;

    @Column( name = "user_id", nullable = false )
    private UUID userId;

    @Enumerated( EnumType.STRING )
    @Column( name = "type", nullable = false, length = 50 )
    private ItemType type;

    @Column( name = "title", length = 500 )
    private String title;

    @Column( name = "description", columnDefinition = "TEXT" )
    private String description;

    @Column( name = "plain_text", columnDefinition = "TEXT" )
    private String plainText;

    @Enumerated( EnumType.STRING )
    @Column( name = "status", nullable = false, length = 50 )
    private ItemStatus status;

    @OneToOne( mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY )
    private Document document;

    @Override
    public boolean equals( Object o ) {
        if ( this == o ) return true;
        if ( o == null || Hibernate.getClass( this ) != Hibernate.getClass( o ) ) return false;
        Item that = ( Item ) o;
        return id != null && Objects.equals( id, that.id );
    }

    @Override
    public int hashCode() {
        if ( this.id == null ) return System.identityHashCode( this );
        return Objects.hash( this.id );
    }
}
