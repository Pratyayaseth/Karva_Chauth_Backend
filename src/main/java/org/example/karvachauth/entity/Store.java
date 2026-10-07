package org.example.karvachauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mia by Tanishq boutique (store) master.
 * Maps the columns of Mia_Stores_Btq_Master.xlsx (Sheet1, columns A–K).
 */
@Entity
@Table(
        name = "mia_store",
        indexes = {
                @Index(name = "idx_mia_store_city", columnList = "city"),
                @Index(name = "idx_mia_store_state", columnList = "state"),
                @Index(name = "idx_mia_store_abm_email", columnList = "abm_email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Btq Code – e.g. "MVC". Unique per boutique. */
    @NotBlank
    @Size(max = 20)
    @Column(name = "btq_code", nullable = false, unique = true, length = 20)
    private String btqCode;

    /** Btq Name */
    @NotBlank
    @Size(max = 100)
    @Column(name = "btq_name", nullable = false, length = 100)
    private String btqName;

    /** Btq Address – longest value in the sheet is ~225 chars. */
    @Size(max = 500)
    @Column(name = "btq_address", length = 500)
    private String btqAddress;

    @Size(max = 50)
    @Column(name = "city", length = 50)
    private String city;

    @Size(max = 50)
    @Column(name = "state", length = 50)
    private String state;

    /** Pin code – stored as String to keep leading zeros; the sheet has values like "500 014". */
    @Pattern(regexp = "^\\d{6}$", message = "Pin code must be 6 digits")
    @Column(name = "pincode", length = 6)
    private String pincode;

    @Size(max = 50)
    @Column(name = "country", length = 50)
    @Builder.Default
    private String country = "India";

    /** Btq Email id */
    @Email
    @Size(max = 100)
    @Column(name = "btq_email", length = 100)
    private String btqEmail;

    /** ABM (Area Business Manager) Email id */
    @Email
    @Size(max = 100)
    @Column(name = "abm_email", length = 100)
    private String abmEmail;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /** Rows like "Closed" in the source sheet → set active = false. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        normalize();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        normalize();
    }

    /** Cleans up the inconsistencies found in the Excel data. */
    private void normalize() {
        if (btqCode != null) btqCode = btqCode.trim().toUpperCase();
        if (btqName != null) btqName = btqName.trim().replaceAll("\\s+", " ");
        if (pincode != null) pincode = pincode.replaceAll("\\s", "");
        if (btqEmail != null) btqEmail = btqEmail.trim().toLowerCase();
        if (abmEmail != null) abmEmail = abmEmail.trim().toLowerCase();
    }
}