package com.miniwsa.analytics.mapper;

import com.miniwsa.analytics.api.AlertDefinitionResponse;
import com.miniwsa.analytics.api.AlertEvaluationResponse;
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
@Mapper(componentModel = "spring", imports = {AlertMetric.class, AlertWindow.class})
public interface AlertMapper {

    @Mapping(target = "metric", expression = "java(definition.getMetric().getWireValue())")
    @Mapping(target = "window", expression = "java(definition.getWindow().getWireValue())")
    AlertDefinitionResponse toResponse(AlertDefinition definition);

    @Mapping(target = "metric", expression = "java(AlertMetric.fromWire(entity.getMetric()))")
    @Mapping(target = "window", expression = "java(AlertWindow.fromWire(entity.getWindow()))")
    AlertDefinition toDomain(AlertDefinitionEntity entity);

    @Mapping(target = "metric", expression = "java(definition.getMetric().getWireValue())")
    @Mapping(target = "window", expression = "java(definition.getWindow().getWireValue())")
    @Mapping(target = "ruleKey", expression = "java(definition.ruleKey())")
    @Mapping(target = "createdAt", source = "createdAt")
    AlertDefinitionEntity toEntity(AlertDefinition definition, Instant createdAt);

    @Mapping(target = "alertId", source = "definition.id")
    @Mapping(target = "name", expression = "java(definition.displayName())")
    @Mapping(target = "firing", expression = "java(value >= definition.getThreshold())")
    @Mapping(target = "threshold", source = "definition.threshold")
    @Mapping(target = "metric", expression = "java(definition.getMetric().getWireValue())")
    @Mapping(target = "window", expression = "java(definition.getWindow().getWireValue())")
    @Mapping(target = "category", source = "definition.category")
    @Mapping(target = "configId", source = "definition.configId")
    AlertEvaluationResponse toEvaluationResponse(AlertDefinition definition, long value);
}
