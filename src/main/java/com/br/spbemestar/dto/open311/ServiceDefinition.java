package com.br.spbemestar.dto.open311;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceDefinition(
        @JsonProperty("service_code") String serviceCode,
        @JsonProperty("service_name") String serviceName,
        String description,
        boolean metadata,
        String type,
        String keywords,
        String group
) {
}
