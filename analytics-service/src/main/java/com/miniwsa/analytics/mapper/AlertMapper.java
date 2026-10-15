package com.miniwsa.analytics.mapper;

import com.miniwsa.analytics.api.AlertDefinitionResponse;
import com.miniwsa.analytics.domain.AlertDefinition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps domain alert definitions to HTTP response models.
 */
@Mapper(componentModel = "spring")
public interface AlertMapper {

    @Mapping(target = "metric", expression = "java(definition.getMetric().getWireValue())")
    @Mapping(target = "window", expression = "java(definition.getWindow().getWireValue())")
    AlertDefinitionResponse toResponse(AlertDefinition definition);
}
