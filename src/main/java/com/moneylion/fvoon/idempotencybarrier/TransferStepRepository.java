package com.moneylion.fvoon.idempotencybarrier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

@Repository
public class TransferStepRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public TransferStepRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = Clock.systemUTC();
    }

    public TransferStepRepository(NamedParameterJdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public Optional<TransferStep> findById(String id) {
        String sql = """
                select *
                from transfer_steps
                where id = :id
                """;

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Map.of("id", id), mapRow()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean isProcessingLockAvailable(String id, int currVersion, Duration lockTimeout) {
        String sql = """
                update transfer_steps
                set status = :status, version = version + 1, updated_at = :now,
                    locked_until = :lockedUntil
                where id = :id
                and version = :version
                and (locked_until is null or locked_until < :now)
                """;

        Timestamp now = Timestamp.from(clock.instant());
        Timestamp lockedUntil = Timestamp.from(clock.instant().plus(lockTimeout));
        int rowsUpdated = jdbcTemplate.update(sql, Map.of(
                "id", id,
                "status", TransferStepStatus.PROCESSING.name(),
                "version", currVersion,
                "lockedUntil", lockedUntil,
                "now", now
        ));

        return rowsUpdated > 0;
    }

    public void updateStatus(String id, TransferStepStatus status) {
        if (TransferStepStatus.PROCESSING == status)
            return;

        String sql = """
                update transfer_steps
                set status = :status, version = version + 1, updated_at = :updatedAt
                where id = :id
                """;

        Timestamp now = Timestamp.from(clock.instant());

        jdbcTemplate.update(sql, Map.of(
                "id", id,
                "status", status.name(),
                "updatedAt", now
        ));
    }

    public void save(String id, TransferStepStatus status) {
        String sql = """
                insert into transfer_steps
                (id, status, updated_at) values (:id, :status, :updatedAt)
                """;

        Timestamp now = Timestamp.from(clock.instant());

        jdbcTemplate.update(sql, Map.of(
                "id", id,
                "status", status.name(),
                "updatedAt", now
        ));
    }

    private RowMapper<TransferStep> mapRow() {
        return (rs, rowNum) -> TransferStep.builder()
                .id(rs.getString("id"))
                .amount(rs.getInt("amount"))
                .updatedAt(getZonedDateTime(rs, "updated_at"))
                .lockedUntil(getZonedDateTime(rs, "locked_until"))
                .version(rs.getInt("version"))
                .status(TransferStepStatus.valueOf(rs.getString("status")))
                .build();
    }

    private ZonedDateTime getZonedDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toInstant().atZone(clock.getZone()) : null;
    }
}
