package com.wsa.analytics.mapper;

import com.wsa.analytics.api.SampleEvent;
import com.wsa.analytics.entity.SecurityEventEntity;
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
