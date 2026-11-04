package com.br.spbemestar.dto.open311;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceRequest(
        @JsonProperty("service_request_id") String serviceRequestId,
        String status,
        @JsonProperty("status_notes") String statusNotes,
        @JsonProperty("service_name") String serviceName,
        @JsonProperty("service_code") String serviceCode,
        String description,
        @JsonProperty("agency_responsible") String agencyResponsible,
        @JsonProperty("requested_datetime") String requestedDatetime,
        @JsonProperty("updated_datetime") String updatedDatetime,
        String address,
        double lat,
        @JsonProperty("long") double longitude,
        @JsonProperty("media_url") String mediaUrl
) {
}
