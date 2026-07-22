package com.ir.portfolio.api.controller;

import com.ir.portfolio.api.dto.OrderDtos.OrderPatchRequest;
import com.ir.portfolio.api.dto.OrderDtos.OrderRequest;
import com.ir.portfolio.api.dto.OrderDtos.OrderResponse;
import com.ir.portfolio.api.mapper.DtoMappers;
import com.ir.portfolio.application.AssetService;
import com.ir.portfolio.application.OrderService;
import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.infrastructure.persistence.entity.OrderEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;
    private final AssetService assetService;

    public OrderController(OrderService orderService, AssetService assetService) {
        this.orderService = orderService;
        this.assetService = assetService;
    }

    @GetMapping
    @Operation(summary = "List orders, optionally filtered by ticker, sorted by trade date")
    public List<OrderResponse> list(@RequestParam(required = false) String ticker) {
        Map<UUID, String> tickersByAssetId = assetService.list().stream()
                .collect(Collectors.toMap(Asset::id, Asset::ticker));
        return orderService.list(ticker).stream()
                .map(order -> DtoMappers.toResponse(order, tickersByAssetId.get(order.getAssetId())))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new order")
    public OrderResponse create(@Valid @RequestBody OrderRequest request) {
        OrderEntity order = orderService.create(
                request.ticker().trim().toUpperCase(),
                request.orderType(),
                request.tradeDate(),
                request.quantity(),
                request.orderValue());
        return DtoMappers.toResponse(order, request.ticker().trim().toUpperCase());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update an order (full history is revalidated)")
    public OrderResponse update(@PathVariable UUID id, @Valid @RequestBody OrderPatchRequest request) {
        OrderEntity order = orderService.update(
                id, request.orderType(), request.tradeDate(), request.quantity(), request.orderValue());
        return DtoMappers.toResponse(order, tickerOf(order));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an order (full history is revalidated)")
    public void delete(@PathVariable UUID id) {
        orderService.delete(id);
    }

    private String tickerOf(OrderEntity order) {
        return assetService.list().stream()
                .filter(asset -> asset.id().equals(order.getAssetId()))
                .map(Asset::ticker)
                .findFirst()
                .orElse(null);
    }
}
