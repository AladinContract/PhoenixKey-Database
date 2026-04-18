package com.magiclamp.phoenixkey_db.domain;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * [V1.5] Composite Primary Key cho ActivityLog.
 *
 * Partitioned table yêu cầu PK bao gồm partition key (created_at).
 * Dùng @IdClass annotation trên ActivityLog entity.
 */
public class ActivityLogId implements Serializable {

    private UUID id;
    private OffsetDateTime createdAt;

    public ActivityLogId() {}

    public ActivityLogId(UUID id, OffsetDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActivityLogId that = (ActivityLogId) o;
        return id.equals(that.id) && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + createdAt.hashCode();
    }
}