package com.wsa.analytics.mapper;

import com.wsa.analytics.entity.SecurityEventEntity;
import com.wsa.contracts.SecurityEventMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

/**
 * Maps wire contract events into the analytics persistence entity.
 */
@Mapper(componentModel = "spring")
public interface SecurityEventMapper {

    @Mapping(target = "eventTimestamp", source = "message.timestamp")
    @Mapping(target = "receivedAt", expression = "java(receivedAtOrNow(message))")
    @Mapping(target = "ruleId", source = "message.rule.id")
    @Mapping(target = "ruleName", source = "message.rule.name")
    @Mapping(target = "ruleMessage", source = "message.rule.message")
    @Mapping(target = "severity", expression = "java(message.getRule().getSeverity().name())")
    @Mapping(target = "category", expression = "java(message.getRule().getCategory().name())")
    @Mapping(target = "action", expression = "java(message.getAction().name())")
    @Mapping(target = "country", source = "message.geoLocation.country")
    @Mapping(target = "city", source = "message.geoLocation.city")
    @Mapping(target = "attackType", source = "attackType")
    @Mapping(target = "threatScore", source = "threatScore")
    SecurityEventEntity toEntity(SecurityEventMessage message, String attackType, int threatScore);

    default Instant receivedAtOrNow(SecurityEventMessage message) {
        return message.getReceivedAt() != null ? message.getReceivedAt() : Instant.now();
    }
}
