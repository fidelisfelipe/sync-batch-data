package com.fidelis.syncbatch.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "CLIENT",
    indexes = {
        @Index(name = "idx_client_last_updated", columnList = "last_updated"),
        @Index(name = "idx_client_source",       columnList = "source")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Email
    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 50)
    private String document;

    /**
     * Managed externally — NOT driven by @UpdateTimestamp so external source timestamps
     * are preserved during sync and the conflict-resolution logic in the processor works correctly.
     */
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    /** Identifies which datasource owns this record (e.g. "local", "source1"). */
    @Column(name = "source", nullable = false, length = 100)
    @Builder.Default
    private String source = "local";
}
