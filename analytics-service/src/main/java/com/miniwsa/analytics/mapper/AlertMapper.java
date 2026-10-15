package com.miniwsa.analytics.mapper;

import com.miniwsa.analytics.api.AlertDefinitionResponse;
import com.miniwsa.analytics.domain.AlertDefinition;
import com.miniwsa.analytics.domain.AlertMetric;
import com.miniwsa.analytics.domain.AlertWindow;
import com.miniwsa.analytics.entity.AlertDefinitionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

/**
 * Maps alert definitions between domain, persistence, and HTTP response models.
 */
@Mapper(componentModel = "spring")
public interface AlertMapper {

    @Mapping(target = "metric", expression = "java(definition.getMetric().getWireValue())")
    @Mapping(target = "window", expression = "java(definition.getWindow().getWireValue())")
    AlertDefinitionResponse toResponse(AlertDefinition definition);

    default AlertDefinition toDomain(AlertDefinitionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AlertDefinition(
                entity.getId(),
                entity.getConfigId(),
                entity.getCategory(),
                AlertMetric.fromWire(entity.getMetric()),
                AlertWindow.fromWire(entity.getWindow()),
                entity.getThreshold());
    }

    default AlertDefinitionEntity toEntity(AlertDefinition definition, Instant createdAt) {
        if (definition == null) {
            return null;
        }
        AlertDefinitionEntity entity = new AlertDefinitionEntity();
        entity.setId(definition.getId());
        entity.setConfigId(definition.getConfigId());
        entity.setCategory(definition.getCategory());
        entity.setMetric(definition.getMetric().getWireValue());
        entity.setWindow(definition.getWindow().getWireValue());
        entity.setThreshold(definition.getThreshold());
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
