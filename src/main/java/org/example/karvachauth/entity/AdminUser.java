package org.example.karvachauth.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 150, unique = true, nullable = false)
    private String email;

    @Column(length = 255, nullable = false)
    private String passwordHash;

    @Column(length = 20)
    @Builder.Default
    private String role = "ADMIN";

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() { createdAt = LocalDateTime.now(); }
}
