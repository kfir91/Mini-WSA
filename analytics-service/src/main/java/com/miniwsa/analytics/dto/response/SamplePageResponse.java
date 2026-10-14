package com.miniwsa.analytics.dto.response;

import java.util.List;

public class SamplePageResponse {

    private final List<SampleEventDto> items;
    private final int page;
    private final int size;
    private final long total;

    public SamplePageResponse(List<SampleEventDto> items, int page, int size, long total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public List<SampleEventDto> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }
}
