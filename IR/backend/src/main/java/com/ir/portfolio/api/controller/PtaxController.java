package com.ir.portfolio.api.controller;

import com.ir.portfolio.api.dto.PtaxDtos.PtaxResponse;
import com.ir.portfolio.api.mapper.DtoMappers;
import com.ir.portfolio.domain.model.PtaxRateType;
import com.ir.portfolio.domain.port.PtaxProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/ptax")
@Tag(name = "PTAX")
public class PtaxController {

    private final PtaxProvider ptaxProvider;

    public PtaxController(PtaxProvider ptaxProvider) {
        this.ptaxProvider = ptaxProvider;
    }

    @GetMapping
    @Operation(summary = "PTAX rate for a date (falls forward to the next business day when needed)")
    public PtaxResponse get(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam PtaxRateType type) {
        return DtoMappers.toResponse(ptaxProvider.get(date, type));
    }
}
