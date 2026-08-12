package com.mac.usermanagement.controller;

import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.entities.dto.PagingDTO;
import com.mac.sdk_util.entities.constant.Role;
import com.mac.sdk_util.helper.ResponseHelper;
import com.mac.sdk_util.helper.ResponsePagingHelper;
import com.mac.usermanagement.entities.dto.RegisterTenantRequest;
import com.mac.usermanagement.entities.dto.TenantListResponse;
import com.mac.usermanagement.entities.dto.TenantResponse;
import com.mac.usermanagement.entities.dto.UpdateTokenPolicyRequest;
import com.mac.usermanagement.service.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<ResponseDTO<TenantResponse>> register(
            @Valid @RequestBody RegisterTenantRequest request) {
        TenantResponse response = tenantService.register(request);
        return ResponseHelper.httpCreated(response, URI.create("/api/v1/tenants/" + response.tenantId()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_tenant:view')")
    public ResponseEntity<ResponseDTO<List<TenantListResponse>>> findAll(
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ResponsePagingHelper.httpOK(tenantService.findAll(limit, offset),
                new PagingDTO(limit, offset, tenantService.count()));
    }

    @PatchMapping("/{tenantId}/token-policy")
    @PreAuthorize(Role.TENANT_UPDATE)
    public ResponseEntity<ResponseDTO<Map<String, Object>>> updateTokenPolicy(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTokenPolicyRequest request) {
        tenantService.updateTokenPolicy(tenantId, request.accessTokenTtlSeconds());
        return ResponseHelper.httpOK(Map.of(
                "tenantId", tenantId,
                "accessTokenTtlSeconds", request.accessTokenTtlSeconds()));
    }
}
