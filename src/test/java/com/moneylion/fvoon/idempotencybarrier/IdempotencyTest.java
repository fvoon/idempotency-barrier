package com.moneylion.fvoon.idempotencybarrier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class IdempotencyTest extends DatabaseConfig {
    @Autowired
    private TransferStepRepository transferStepRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    private MockCardClient cardClient;
    @Mock
    private Clock clock;
    private static final String ID = "blabla123";

    @BeforeEach
    void setup() {
        transferStepRepository = new TransferStepRepository(jdbcTemplate, clock);
        when(clock.instant()).thenReturn(Instant.parse("2025-03-30T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        transferStepRepository.save(ID, TransferStepStatus.INITIATED);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("truncate table transfer_steps restart identity cascade", Collections.emptyMap());
    }

    @Test
    void shouldPreventConcurrentProcessing() throws InterruptedException {
        int numOfThreads = 5;

        try (ExecutorService executorService = Executors.newFixedThreadPool(numOfThreads)) {
            CountDownLatch latch = new CountDownLatch(numOfThreads);

            for (int i = 0; i < numOfThreads; i++) {
                executorService.execute(() -> {
                    try {
                        new MockCardTask(transferStepRepository, cardClient).run();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();
        }

        TransferStep step = transferStepRepository.findById(ID).orElseThrow();
        assertEquals(TransferStepStatus.SETTLED, step.getStatus());
    }

    @Test
    void shouldNotAllowProcessingWithinLockTimeout() {
        Instant initialTime = Instant.parse("2025-03-30T12:00:00Z");
        when(clock.instant()).thenReturn(initialTime);

        new MockCardTask(transferStepRepository, cardClient, ScenarioId.MOCK_CARD_CLIENT_CLIENT_ERROR).run();

        TransferStep step = transferStepRepository.findById(ID).orElseThrow();
        assertEquals(TransferStepStatus.PRE_SUBMISSION, step.getStatus());

        Instant withinTimeout = initialTime.plusSeconds(30);
        when(clock.instant()).thenReturn(withinTimeout);

        new MockCardTask(transferStepRepository, cardClient).run();

        TransferStep step2 = transferStepRepository.findById(ID).orElseThrow();
        assertEquals(TransferStepStatus.PRE_SUBMISSION, step2.getStatus());
        assertEquals(step2.getVersion(), step.getVersion());
    }

    @Test
    void shouldAllowProcessingAfterLockTimeout() {
        Instant initialTime = Instant.parse("2025-03-30T12:00:00Z");
        when(clock.instant()).thenReturn(initialTime);

        new MockCardTask(transferStepRepository, cardClient, ScenarioId.MOCK_CARD_CLIENT_CLIENT_ERROR).run();

        TransferStep step = transferStepRepository.findById(ID).orElseThrow();
        assertEquals(TransferStepStatus.PRE_SUBMISSION, step.getStatus());

        Instant afterTimeout = initialTime.plusSeconds(61);
        when(clock.instant()).thenReturn(afterTimeout);

        new MockCardTask(transferStepRepository, cardClient).run();

        TransferStep step2 = transferStepRepository.findById(ID).orElseThrow();
        assertEquals(TransferStepStatus.SETTLED, step2.getStatus());
        assertTrue(step2.getVersion() > step.getVersion());
    }
}
