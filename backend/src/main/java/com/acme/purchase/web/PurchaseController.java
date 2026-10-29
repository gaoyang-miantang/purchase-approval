package com.acme.purchase.web;

import com.acme.purchase.common.ApiResult;
import com.acme.purchase.common.PageResult;
import com.acme.purchase.security.UserContext;
import com.acme.purchase.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/requests")
public class PurchaseController {

    private final PurchaseService service;

    public PurchaseController(PurchaseService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<PageResult<Dto.RequestView>> list(
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(service.search(UserContext.require(), tab, status, keyword,
                category, minAmount, maxAmount, page, size));
    }

    @GetMapping("/stats")
    public ApiResult<Dto.StatsView> stats() {
        return ApiResult.ok(service.stats(UserContext.require()));
    }

    @GetMapping("/{id}")
    public ApiResult<Dto.DetailView> detail(@PathVariable Long id) {
        return ApiResult.ok(service.detail(id, UserContext.require()));
    }

    @PostMapping
    public ApiResult<Dto.RequestView> create(@Valid @RequestBody Dto.SaveRequest request) {
        return ApiResult.ok(service.create(request, UserContext.require()));
    }

    @PutMapping("/{id}")
    public ApiResult<Dto.RequestView> update(@PathVariable Long id,
                                             @Valid @RequestBody Dto.SaveRequest request) {
        return ApiResult.ok(service.update(id, request, UserContext.require()));
    }

    @PostMapping("/{id}/submit")
    public ApiResult<Dto.RequestView> submit(@PathVariable Long id) {
        return ApiResult.ok(service.submit(id, UserContext.require()));
    }

    @PostMapping("/{id}/approve")
    public ApiResult<Dto.RequestView> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Dto.ApproveRequest request,
            @RequestHeader(value = "X-Demo-Delay-Ms", required = false) Long demoDelayMs) {
        String comment = request == null ? null : request.comment();
        return ApiResult.ok(service.approve(id, UserContext.require(), comment, demoDelayMs));
    }

    @PostMapping("/{id}/reject")
    public ApiResult<Dto.RequestView> reject(
            @PathVariable Long id,
            @Valid @RequestBody Dto.RejectRequest request,
            @RequestHeader(value = "X-Demo-Delay-Ms", required = false) Long demoDelayMs) {
        return ApiResult.ok(service.reject(id, UserContext.require(), request.reason(), demoDelayMs));
    }

    @PostMapping("/{id}/cancel")
    public ApiResult<Dto.RequestView> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Dto.CancelRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResult.ok(service.cancel(id, UserContext.require(), reason));
    }
}
