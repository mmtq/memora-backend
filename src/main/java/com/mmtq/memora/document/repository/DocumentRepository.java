package com.mmtq.memora.document.repository;

import com.mmtq.memora.document.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query( "SELECT d FROM Document d JOIN FETCH d.item i WHERE d.id = :id AND i.userId = :userId" )
    Optional<Document> findByIdAndItemUserId( @Param("id") UUID id, @Param("userId") UUID userId );
}
