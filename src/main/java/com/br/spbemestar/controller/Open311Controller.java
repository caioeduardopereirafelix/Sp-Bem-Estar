package com.br.spbemestar.controller;

import com.br.spbemestar.dto.open311.Open311Error;
import com.br.spbemestar.dto.open311.ServiceDefinition;
import com.br.spbemestar.dto.open311.ServiceRequest;
import com.br.spbemestar.service.Open311Service;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.List;

@Tag(name = "Open311", description = "Chamados no padrão aberto Open311 GeoReport v2, só leitura")
@RestController
@RequestMapping("/open311")
@CrossOrigin(origins = "*", methods = RequestMethod.GET, maxAge = 86400)
@RequiredArgsConstructor
public class Open311Controller {

    private final Open311Service open311;

    @Operation(summary = "Tipos de problema (services)")
    @GetMapping("/v2/services.json")
    public ResponseEntity<List<ServiceDefinition>> services() {
        return open(open311.services());
    }

    @Operation(summary = "Chamados (service requests), dos últimos 90 dias por padrão e no máximo 1000")
    @GetMapping("/v2/requests.json")
    public ResponseEntity<List<ServiceRequest>> requests(
            @RequestParam(name = "service_request_id", required = false) String serviceRequestId,
            @RequestParam(name = "service_code", required = false) String serviceCode,
            @RequestParam(name = "start_date", required = false) String startDate,
            @RequestParam(name = "end_date", required = false) String endDate,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "jurisdiction_id", required = false) String jurisdictionId) {
        return open(open311.requests(serviceRequestId, serviceCode, startDate, endDate, status, baseUrl()));
    }

    @Operation(summary = "Um chamado (service request)")
    @GetMapping("/v2/requests/{id}.json")
    public ResponseEntity<List<ServiceRequest>> request(@PathVariable long id) {
        return open(open311.request(id, baseUrl()));
    }

    @Hidden
    @RequestMapping("/**")
    public ResponseEntity<List<Open311Error>> other(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(HttpMethod.GET)
                    .body(List.of(new Open311Error(405, "Esta API Open311 é só de leitura: use GET.")));
        }
        if (request.getRequestURI().endsWith(".xml")) {
            return ResponseEntity.badRequest()
                    .body(List.of(new Open311Error(400, "Só o formato JSON é aceito: troque .xml por .json.")));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of(new Open311Error(404,
                "Rota não encontrada. As rotas são /open311/v2/services.json, /open311/v2/requests.json e "
                        + "/open311/v2/requests/{id}.json.")));
    }

    private static <T> ResponseEntity<T> open(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(body);
    }

    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
