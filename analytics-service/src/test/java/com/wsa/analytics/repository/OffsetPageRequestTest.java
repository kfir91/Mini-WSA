package com.wsa.analytics.repository;

import com.wsa.contracts.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffsetPageRequestTest {

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(ApiException.class, () -> new OffsetPageRequest(0, 0, Sort.unsorted()));
    }

    @Test
    void clampsNegativeOffsetAndNullSort() {
        OffsetPageRequest page = new OffsetPageRequest(-5, 10, null);
        assertEquals(0, page.getOffset());
        assertEquals(Sort.unsorted(), page.getSort());
        assertEquals(0, page.getPageNumber());
        assertEquals(10, page.getPageSize());
        assertFalse(page.hasPrevious());
    }

    @Test
    void navigationHelpers() {
        OffsetPageRequest page = new OffsetPageRequest(20, 10, Sort.by("eventId"));
        assertTrue(page.hasPrevious());
        assertEquals(2, page.getPageNumber());

        Pageable next = page.next();
        assertEquals(30, next.getOffset());

        Pageable prev = page.previousOrFirst();
        assertEquals(10, prev.getOffset());

        Pageable first = page.first();
        assertEquals(0, first.getOffset());

        Pageable withPage = page.withPage(4);
        assertEquals(40, withPage.getOffset());

        OffsetPageRequest early = new OffsetPageRequest(5, 10, Sort.unsorted());
        assertEquals(0, early.previousOrFirst().getOffset());
    }
}
