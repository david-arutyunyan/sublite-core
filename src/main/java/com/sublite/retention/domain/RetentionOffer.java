package com.sublite.retention.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * "parameters" is a JSONB column mapped straight to a Map - the first real
 * use of JSONB in this project. Each offer type needs a different shape
 * (discount: percent + periods, loyalty: a point amount, pause: nothing at
 * all), and there's no single set of typed columns that fits all three
 * without most rows being mostly NULL. The trade-off: the DB can't
 * validate what's inside the JSON, so PAUSE_SUBSCRIPTION.type governs
 * which keys RetentionFlowService expects to find.
 */
@Entity
@Table(name = "retention_offers", schema = "retention")
public class RetentionOffer {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RetentionOfferType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RetentionOffer() {
        // JPA
    }

    public RetentionOffer(UUID id, String code, RetentionOfferType type, Map<String, Object> parameters, Instant createdAt) {
        this.id = id;
        this.code = code;
        this.type = type;
        this.parameters = parameters;
        this.active = true;
        this.createdAt = createdAt;
    }

    public int intParameter(String key, int defaultValue) {
        Object value = parameters.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public RetentionOfferType getType() {
        return type;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
