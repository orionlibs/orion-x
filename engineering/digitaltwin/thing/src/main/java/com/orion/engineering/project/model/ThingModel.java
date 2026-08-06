package com.orion.engineering.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "things")
@Getter
public class ThingModel
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "description", nullable = false)
    private String description;
    @Column(name = "thing_type", nullable = false)
    private String thingType;
    @Column(name = "parent_id")
    private UUID parentId;
    @Column(name = "uns_topic", nullable = false, unique = true)
    private String unsTopic;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "registration_method", nullable = false)
    private String registrationMethod;
    @Column(name = "mqtt_client_id", unique = true)
    private String mqttClientId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private Map<String, Object> metadata;
    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


    public void setName(String name)
    {
        this.name = name;
    }


    public void setDescription(String description)
    {
        this.description = description;
    }


    public void setThingType(String thingType)
    {
        this.thingType = thingType;
    }


    public void setParentId(UUID parentId)
    {
        this.parentId = parentId;
    }


    public void setUnsTopic(String unsTopic)
    {
        this.unsTopic = unsTopic;
    }


    public void setStatus(String status)
    {
        this.status = status;
    }


    public void setRegistrationMethod(String registrationMethod)
    {
        this.registrationMethod = registrationMethod;
    }


    public void setMqttClientId(String mqttClientId)
    {
        this.mqttClientId = mqttClientId;
    }


    public void setMetadata(Map<String, Object> metadata)
    {
        this.metadata = metadata;
    }


    public void setLastSeenAt(OffsetDateTime lastSeenAt)
    {
        this.lastSeenAt = lastSeenAt;
    }
}
