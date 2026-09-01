// by Jeremy Posada
package com.jposada.anaquel.domain.book;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "books")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 300)
    private String author;

    /** ISBN normalizado (sin guiones ni espacios). Unico en base de datos. */
    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookStatus status = BookStatus.DISPONIBLE;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "book_subjects", joinColumns = @JoinColumn(name = "book_id"))
    @Column(name = "subject", length = 200)
    @Builder.Default
    private List<String> subjects = new ArrayList<>();

    /** true cuando titulo/autor/anio/portada se completaron desde Open Library. */
    @Column(name = "enriched_from_external", nullable = false)
    @Builder.Default
    private boolean enrichedFromExternal = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bloqueo optimista: evita que dos prestamos simultaneos tomen el mismo libro. */
    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isAvailable() {
        return this.status == BookStatus.DISPONIBLE;
    }
}
