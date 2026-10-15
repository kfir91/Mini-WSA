package com.miniwsa.ingest.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * HTTP contract for ingest. Mirrors {@code open-api/ingest-api.yaml}.
 * Body is {@link JsonNode} so oneOf (single event or array) can be parsed at runtime;
 * OpenAPI Generator cannot map that oneOf to a usable Spring type.
 */
public interface EventsApi {

    @RequestMapping(
            method = RequestMethod.POST,
            value = "/v1/events/ingest",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Void> ingestEvents(@RequestBody(required = false) JsonNode body) throws Exception;
}
