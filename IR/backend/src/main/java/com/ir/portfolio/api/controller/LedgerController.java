package com.ir.portfolio.api.controller;

import com.ir.portfolio.api.dto.LedgerDtos.LedgerLineResponse;
import com.ir.portfolio.api.mapper.DtoMappers;
import com.ir.portfolio.application.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    @Operation(summary = "Full computed ledger of one asset (all order effects, dual currency for US assets)")
    public List<LedgerLineResponse> ledger(@RequestParam String ticker) {
        return ledgerService.ledger(ticker.trim().toUpperCase()).lines().stream()
                .map(DtoMappers::toResponse)
                .toList();
    }
}
