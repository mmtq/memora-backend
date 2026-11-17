package com.mmtq.memora.item.repository;

import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.item.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

    @EntityGraph( attributePaths = { "document" } )
    Optional<Item> findByIdAndUserId( UUID id, UUID userId );

    List<Item> findAllByUserId( UUID userId );

    long countByUserIdAndStatus( UUID userId, ItemStatus status );

    @Override
    @EntityGraph( attributePaths = { "document" } )
    Page<Item> findAll( Specification<Item> spec, Pageable pageable );

    @EntityGraph( attributePaths = { "document" } )
    Page<Item> findAllByUserId( UUID userId, Pageable pageable );

    @EntityGraph( attributePaths = { "document" } )
    Page<Item> findAllByUserIdAndType( UUID userId, ItemType type, Pageable pageable );

    @Transactional
    @Modifying( clearAutomatically = true )
    @Query( "UPDATE Item i SET i.status = :status WHERE i.id = :id" )
    int updateStatus( @Param( "id" ) UUID id, @Param( "status" ) ItemStatus status );
}
