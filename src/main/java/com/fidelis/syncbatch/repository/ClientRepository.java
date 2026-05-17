package com.fidelis.syncbatch.repository;

import com.fidelis.syncbatch.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByEmail(String email);

    Optional<Client> findByDocument(String document);

    List<Client> findByLastUpdatedAfter(LocalDateTime since);

    List<Client> findBySourceAndLastUpdatedAfter(String source, LocalDateTime since);

    boolean existsByEmail(String email);

    @Query("SELECT c FROM Client c WHERE c.lastUpdated > :since ORDER BY c.lastUpdated ASC")
    List<Client> findModifiedSince(@Param("since") LocalDateTime since);

    @Query("SELECT c FROM Client c WHERE c.source = :source AND c.lastUpdated > :since ORDER BY c.lastUpdated ASC")
    List<Client> findModifiedSinceBySource(@Param("source") String source, @Param("since") LocalDateTime since);

    /**
     * Idempotent upsert: update existing record by email, preserving id and creation metadata.
     * Used by the LocalClientWriter to avoid duplicate-key conflicts.
     */
    @Modifying
    @Query("""
            UPDATE Client c
            SET c.name = :#{#client.name},
                c.document = :#{#client.document},
                c.lastUpdated = :#{#client.lastUpdated},
                c.source = :#{#client.source}
            WHERE c.email = :#{#client.email}
            """)
    int updateByEmail(@Param("client") Client client);
}
