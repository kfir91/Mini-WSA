package com.miniwsa.analytics.mapper;

import com.miniwsa.analytics.api.SampleEvent;
import com.miniwsa.analytics.entity.SecurityEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps persisted events to the samples API response model.
 */
@Mapper(componentModel = "spring")
public interface SampleEventMapper {

    @Mapping(target = "timestamp", source = "eventTimestamp")
    SampleEvent toApi(SecurityEventEntity entity);
}
