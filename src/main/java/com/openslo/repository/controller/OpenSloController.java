package com.openslo.repository.controller;

import com.openslo.repository.dto.OpenSloDetailDto;
import com.openslo.repository.dto.OpenSloSummaryDto;
import com.openslo.repository.dto.SaveOpenSloRequest;
import com.openslo.repository.dto.YamlParseRequest;
import com.openslo.repository.service.OpenSloService;
import com.openslo.repository.service.YamlConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class OpenSloController {

    private final OpenSloService openSloService;
    private final YamlConversionService yamlConversionService;

    public OpenSloController(OpenSloService openSloService, YamlConversionService yamlConversionService) {
        this.openSloService = openSloService;
        this.yamlConversionService = yamlConversionService;
    }

    @GetMapping
    public List<OpenSloSummaryDto> listActive() {
        return openSloService.listActive();
    }

    @GetMapping("/{logicalKey}")
    public OpenSloDetailDto getActive(@PathVariable String logicalKey) {
        return openSloService.getActiveByLogicalKey(decodeLogicalKey(logicalKey));
    }

    @GetMapping("/{logicalKey}/versions")
    public List<OpenSloSummaryDto> listVersions(@PathVariable String logicalKey) {
        return openSloService.listVersions(decodeLogicalKey(logicalKey));
    }

    @GetMapping("/id/{id}")
    public OpenSloDetailDto getById(@PathVariable String id) {
        return openSloService.getById(id);
    }

    @GetMapping("/exists/{logicalKey}")
    public Map<String, Boolean> exists(@PathVariable String logicalKey) {
        String key = decodeLogicalKey(logicalKey);
        return Map.of("exists", openSloService.existsActive(key));
    }

    @PostMapping("/validate")
    public Map<String, Object> validate(@Valid @RequestBody SaveOpenSloRequest request) {
        openSloService.validateOnly(request.content());
        return Map.of(
            "valid", true,
            "logicalKey", openSloService.buildLogicalKey(request.content())
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpenSloDetailDto create(@Valid @RequestBody SaveOpenSloRequest request) {
        return openSloService.create(request.content());
    }

    @PutMapping("/{logicalKey}")
    public OpenSloDetailDto update(@PathVariable String logicalKey, @Valid @RequestBody SaveOpenSloRequest request) {
        return openSloService.update(decodeLogicalKey(logicalKey), request.content());
    }

    @PostMapping("/parse-yaml")
    public Map<String, Object> parseYaml(@Valid @RequestBody YamlParseRequest request) {
        return yamlConversionService.parseYaml(request.yaml());
    }

    @PostMapping("/to-yaml")
    public Map<String, String> toYaml(@Valid @RequestBody SaveOpenSloRequest request) {
        return Map.of("yaml", yamlConversionService.toYaml(request.content()));
    }

    private String decodeLogicalKey(String logicalKey) {
        return logicalKey.replace("~", "/");
    }
}
