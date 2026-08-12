package com.mac.usermanagement.repository.impl;

import com.mac.usermanagement.entities.model.Tenant;
import com.mac.usermanagement.repository.TenantRepository;
import com.mac.sdk_util.exception.ResourceNotFoundException;
import com.mac.usermanagement.utils.exception.IdentityConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepositoryImpl implements TenantRepository {

    private static final String SELECT = """
            SELECT id, tenant_key, name, access_token_ttl_seconds, enabled, created_at
            FROM tenant
            """;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TenantRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Tenant insert(Tenant tenant) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO tenant (id, tenant_key, name, access_token_ttl_seconds, enabled, created_at)
                    VALUES (:id, :tenantKey, :name, :ttl, :enabled, :createdAt)
                    """, new MapSqlParameterSource()
                    .addValue("id", tenant.id()).addValue("tenantKey", tenant.tenantKey())
                    .addValue("name", tenant.name()).addValue("ttl", tenant.accessTokenTtlSeconds())
                    .addValue("enabled", tenant.enabled())
                    .addValue("createdAt", tenant.createdAt().atOffset(ZoneOffset.UTC),
                            Types.TIMESTAMP_WITH_TIMEZONE));
            return tenant;
        } catch (DuplicateKeyException exception) {
            throw new IdentityConflictException("Tenant key already exists", exception);
        }
    }

    @Override
    public Optional<Tenant> findById(UUID tenantId) {
        return query(SELECT + " WHERE id = :id", Map.of("id", tenantId));
    }

    @Override
    public Optional<Tenant> findByKey(String tenantKey) {
        return query(SELECT + " WHERE tenant_key = :tenantKey", Map.of("tenantKey", tenantKey));
    }

    @Override
    public List<Tenant> findAll() {
        return jdbcTemplate.query(SELECT + " ORDER BY created_at DESC, tenant_key", Map.of(), this::map);
    }

    @Override
    public List<Tenant> findAll(int limit, int offset) {
        return jdbcTemplate.query(SELECT + """
                 ORDER BY created_at DESC, tenant_key, id
                 LIMIT :limit OFFSET :offset
                """, Map.of("limit", limit, "offset", offset), this::map);
    }

    @Override
    public long count() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tenant", Map.of(), Long.class);
        return total == null ? 0 : total;
    }

    @Override
    public void updateTokenTtl(UUID tenantId, long ttlSeconds) {
        int updated = jdbcTemplate.update("""
                UPDATE tenant SET access_token_ttl_seconds = :ttl WHERE id = :id
                """, Map.of("ttl", ttlSeconds, "id", tenantId));
        if (updated != 1) {
            throw new ResourceNotFoundException("Tenant not found");
        }
    }

    private Optional<Tenant> query(String sql, Map<String, ?> parameters) {
        List<Tenant> tenants = jdbcTemplate.query(sql, parameters, this::map);
        return tenants.stream().findFirst();
    }

    private Tenant map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Tenant(resultSet.getObject("id", UUID.class), resultSet.getString("tenant_key"),
                resultSet.getString("name"), resultSet.getLong("access_token_ttl_seconds"),
                resultSet.getBoolean("enabled"), resultSet.getTimestamp("created_at").toInstant());
    }
}
