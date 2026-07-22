package com.ir.portfolio.api.controller;

import com.ir.portfolio.api.dto.AssetDtos.AssetRequest;
import com.ir.portfolio.api.dto.AssetDtos.AssetResponse;
import com.ir.portfolio.api.mapper.DtoMappers;
import com.ir.portfolio.application.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
@Tag(name = "Assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    @Operation(summary = "List the portfolio's assets")
    public List<AssetResponse> list() {
        return assetService.list().stream().map(DtoMappers::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an asset to the portfolio")
    public AssetResponse create(@Valid @RequestBody AssetRequest request) {
        return DtoMappers.toResponse(assetService.create(
                request.ticker().trim().toUpperCase(),
                request.name().trim(),
                request.market(),
                request.category()));
    }
}
