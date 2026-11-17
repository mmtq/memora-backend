package com.mmtq.memora.item.repository;

import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.item.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {

    @EntityGraph( attributePaths = { "document" } )
    Optional<Item> findByIdAndUserId( UUID id, UUID userId );

    List<Item> findAllByUserId( UUID userId );

    @EntityGraph( attributePaths = { "document" } )
    Page<Item> findAllByUserId( UUID userId, Pageable pageable );

    @EntityGraph( attributePaths = { "document" } )
    Page<Item> findAllByUserIdAndType( UUID userId, ItemType type, Pageable pageable );
}
