package org.example.karvachauth.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_error_logs", indexes = {
        @Index(name = "idx_error_logs_created", columnList = "createdAt"),
        @Index(name = "idx_error_logs_level", columnList = "level")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String level;   // ERROR, WARN

    @Column(length = 100)
    private String loggerName;

    @Column(length = 500)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    @Column(length = 20)
    private String phone;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
