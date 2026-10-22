package com.wsa.ingest.service;

import com.wsa.contracts.ApiException;
import com.wsa.contracts.ErrorMessage;
import com.wsa.ingest.api.IngestEventStatus;
import com.wsa.ingest.entity.IngestOutboxEntity;
import com.wsa.ingest.repository.IngestOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only client status projection over the ingest outbox.
 */
@Service
@RequiredArgsConstructor
public class IngestStatusService {

    private final IngestOutboxRepository outboxRepository;

    @Transactional(readOnly = true)
    public IngestEventStatus getStatus(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw ApiException.notFound(ErrorMessage.RESOURCE_NOT_FOUND);
        }
        IngestOutboxEntity row = outboxRepository.findByEventId(eventId.trim())
                .orElseThrow(() -> ApiException.notFound(ErrorMessage.RESOURCE_NOT_FOUND));
        return IngestStatusMapper.toEventStatus(row);
    }
}
