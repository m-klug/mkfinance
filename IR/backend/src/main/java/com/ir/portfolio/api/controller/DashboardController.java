package com.ir.portfolio.api.controller;

import com.ir.portfolio.api.dto.PositionDtos.DashboardPositionResponse;
import com.ir.portfolio.api.dto.PositionDtos.DashboardResponse;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final PositionService positionService;

    public DashboardController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping
    @Operation(summary = "Per-asset positions today and at a reference date, plus BRL portfolio totals")
    public DashboardResponse dashboard(@RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        PositionService.DashboardResult result = positionService.dashboard(referenceDate);

        List<DashboardPositionResponse> positions = result.entries().stream()
                .map(entry -> new DashboardPositionResponse(
                        DtoMappers.toResponse(entry.asset()),
                        DtoMappers.toValues(entry.current()),
                        DtoMappers.toValues(entry.atReference())))
                .toList();

        return new DashboardResponse(
                result.referenceDate(),
                positions,
                DtoMappers.money(result.totalCostBrazilBrl()),
                DtoMappers.money(result.totalCostAbroadBrl()));
    }
}
