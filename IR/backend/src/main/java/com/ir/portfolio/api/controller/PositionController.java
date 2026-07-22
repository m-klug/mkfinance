package com.ir.portfolio.api.controller;

import com.ir.portfolio.api.dto.PositionDtos.PositionResponse;
import com.ir.portfolio.api.mapper.DtoMappers;
import com.ir.portfolio.application.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/positions")
@Tag(name = "Positions")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping
    @Operation(summary = "Consolidated position of one asset, optionally at a cutoff date (inclusive)")
    public PositionResponse position(@RequestParam String ticker,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return DtoMappers.toResponse(positionService.position(ticker.trim().toUpperCase(), date));
    }
}
