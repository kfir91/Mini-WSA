package com.miniwsa.analytics.dto.response;

import java.util.List;

public class SamplePageResponse {

    private final List<SampleEventDto> items;
    private final int limit;
    private final int offset;
    private final long total;

    public SamplePageResponse(List<SampleEventDto> items, int limit, int offset, long total) {
        this.items = items;
        this.limit = limit;
        this.offset = offset;
        this.total = total;
    }

    public List<SampleEventDto> getItems() {
        return items;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public long getTotal() {
        return total;
    }
}
