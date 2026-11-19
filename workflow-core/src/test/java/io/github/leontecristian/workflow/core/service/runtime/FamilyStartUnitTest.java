package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.HostStartIntent;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowCommandReceiptEntity;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowActivityRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowCommandReceiptRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowInstanceRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionRuntimeReader;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Isolated command/replay/error checks; no persistence provider or transaction is started. */
class FamilyStartUnitTest {
    private static final UUID ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final WorkflowCommandContext context = new WorkflowCommandContext(
            ActorKind.USER, "f007.admin", Instant.parse("2026-01-01T00:00:00Z"), "first-attempt");
    private final RuntimeJson json = new RuntimeJson();

    @Test
    void acceptsOnlyOneValidSelectorWithoutNormalizingIt() {
        for (var command : List.of(command("Booking.review_1-v2", null, null),
                command(null, Long.MAX_VALUE, null), command(null, null, Long.MAX_VALUE))) {
            assertDoesNotThrow(() -> RuntimeInputs.validateStartCommand(command, context));
        }
        for (var command : List.of(command(null, null, null), command("key", 1L, null),
                command("key", null, 1L), command(null, 1L, 1L), command("key", 1L, 1L),
                command(null, 0L, null), command(null, -1L, null),
                command(null, null, 0L), command(null, null, -1L))) {
            invalid(command);
        }
        for (String key : List.of("", " ", " key", "key ", "1key", "a/b", "a".repeat(101), "ékey")) {
            invalid(command(key, null, null));
        }
        assertDoesNotThrow(() -> RuntimeInputs.validateStartCommand(command("a".repeat(100), null, null), context));
    }

    @Test
    void fingerprintsBindOriginalSelectorAndIdentityButExcludeAttemptMetadata() {
        var selectors = List.of(command("key", null, null), command("Key", null, null),
                command("other", null, null), command(null, 42L, null), command(null, 43L, null),
                command(null, null, 42L), command(null, null, 43L));
        var fingerprints = selectors.stream().map(c -> HexFormat.of().formatHex(json.fingerprint(c, context))).toList();
        assertEquals(selectors.size(), fingerprints.stream().distinct().count());
        var later = new WorkflowCommandContext(ActorKind.USER, "f007.admin", Instant.parse("2026-11-14T12:00:00Z"), "recovery");
        var inputs = new StartInputs(Map.of("quantity", new TextLiteral("12.00")));
        for (var command : selectors) {
            assertArrayEquals(json.fingerprint(command, inputs, context), json.fingerprint(command, inputs, later));
            assertNotEquals(HexFormat.of().formatHex(json.fingerprint(command, context)),
                    HexFormat.of().formatHex(json.fingerprint(command, inputs, context)));
        }
        assertEquals("wf-runtime-family-start-v1", json.fingerprintVersion(selectors.get(0)));
        assertEquals("wf-runtime-request-v3", json.fingerprintVersion(selectors.get(5)));
        assertEquals("7464bc6cbced3aea47ace2c0059a23513f91ba6f2d0a12b85d8785460e5129c0", fingerprints.get(5));
        var original = selectors.get(0);
        var intent = new HostStartIntent("host-v1", "ab".repeat(32));
        assertDifferent(original, new StartWorkflow(ID, "key", null, null, null, null, intent));
        assertDifferent(original, new StartWorkflow(ID, "key", null, null, "BOOKING", "123", null));
        assertNotEquals(HexFormat.of().formatHex(json.fingerprint(original, context)),
                HexFormat.of().formatHex(json.fingerprint(original,
                        new WorkflowCommandContext(ActorKind.USER, "other", context.occurredAt(), "first-attempt"))));
    }

    @Test
    void realReplayUsesRetainedSnapshotBeforeAnyResolutionAndRejectsChangedSelectors() throws Exception {
        var receipts = mock(WorkflowCommandReceiptRepository.class);
        var definitions = mock(WorkflowDefinitionRuntimeReader.class);
        var host = mock(RuntimeHostSupport.class);
        var em = mock(EntityManager.class);
        var service = service(receipts, definitions, host, em);
        String retained;
        try (var source = getClass().getResourceAsStream("/f007/extended-start.json")) {
            retained = new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
        var exact = command(null, null, 42L);
        var key = command("sandbox.serviceReview", null, null);
        var family = command(null, 40L, null);
        var later = new WorkflowCommandContext(context.actorKind(), context.actorRef(),
                Instant.parse("2026-11-14T12:00:00Z"), "recovery");
        for (var original : List.of(exact, key, family)) {
            byte[] fingerprint = original == exact
                    ? HexFormat.of().parseHex("7464bc6cbced3aea47ace2c0059a23513f91ba6f2d0a12b85d8785460e5129c0")
                    : json.fingerprint(original, context);
            var receipt = new WorkflowCommandReceiptEntity(ID, 1, WorkflowOperation.START_WORKFLOW.name(),
                    ActorKind.USER, context.actorRef(), fingerprint, json.fingerprintVersion(original),
                    LocalDateTime.parse("2026-01-01T00:00:00"), retained, (short) 3);
            when(receipts.findById(ID)).thenReturn(Optional.of(receipt));
            var replay = service.findReplay(original, later).orElseThrow();
            assertTrue(replay.replayed());
            assertEquals(42L, replay.execution().instance().definitionVersionId());
            assertEquals(replay, service.start(original, later));
            for (var changed : List.of(exact, key, family, command("other", null, null), command(null, 41L, null))) {
                if (changed.equals(original)) continue;
                assertEquals(Category.COMMAND_ID_CONFLICT, assertThrows(WorkflowExecutionException.class,
                        () -> service.findReplay(changed, later)).category());
            }
        }
        when(receipts.findById(ID)).thenReturn(Optional.empty());
        assertTrue(service.findReplay(key, later).isEmpty());
        assertTrue(service.findReplay(family, later).isEmpty());
        verifyNoInteractions(definitions, host, em);
    }

    @Test
    void definitionFailuresAndLockConflictsKeepTheirSanitizedCategoriesAndCauses() {
        for (var category : List.of(WorkflowDefinitionException.Category.NOT_FOUND,
                WorkflowDefinitionException.Category.NO_PUBLISHED_VERSION)) {
            var original = new WorkflowDefinitionException(category, List.of());
            var failure = assertThrows(WorkflowExecutionException.class,
                    () -> RuntimePersistenceErrors.call(() -> { throw original; }));
            assertEquals(Category.valueOf(category.name()), failure.category());
            assertSame(original, failure.getCause());
        }
        for (var original : List.of(new jakarta.persistence.PessimisticLockException("private"),
                new jakarta.persistence.LockTimeoutException("private"),
                new org.springframework.dao.PessimisticLockingFailureException("private"),
                new jakarta.persistence.PersistenceException(new SQLException("private", "40001", 1205)),
                new jakarta.persistence.PersistenceException(new SQLException("private", "HYT00", 1222)))) {
            var failure = assertThrows(WorkflowExecutionException.class,
                    () -> RuntimePersistenceErrors.call(() -> { throw original; }));
            assertEquals(Category.CONCURRENT_COMMAND, failure.category());
            assertSame(original, failure.getCause());
        }
    }

    @Test
    void freshStartChecksPinnedCapabilitiesBeforeLinkingOrEnteringAnyStage() {
        var receipts = mock(WorkflowCommandReceiptRepository.class);
        var definitions = mock(WorkflowDefinitionRuntimeReader.class);
        var host = mock(RuntimeHostSupport.class);
        var em = mock(EntityManager.class);
        var link = mock(StartLinkAction.class);
        var service = service(receipts, definitions, host, em);
        var start = new StartWorkflow(ID, null, null, 42L, null, null,
                new HostStartIntent("host-v1", "ab".repeat(32)));
        var definition = mock(ExecutableDefinition.class);
        when(definitions.loadForStart(start)).thenReturn(definition);
        when(host.eventBatchWriter()).thenReturn(mock(WorkflowEventBatchWriter.class));
        doThrow(new WorkflowExecutionException(Category.HOST_CONFIGURATION))
                .when(definitions).requireCapabilities(definition, true);

        assertEquals(Category.HOST_CONFIGURATION, assertThrows(WorkflowExecutionException.class,
                () -> service.start(start, new StartInputs(Map.of()), context, link)).category());
        var order = inOrder(receipts, definitions);
        order.verify(receipts).findById(ID);
        order.verify(definitions).loadForStart(start);
        order.verify(definitions).requireCapabilities(definition, true);
        verifyNoInteractions(link, em);
    }

    private WorkflowExecutionService service(WorkflowCommandReceiptRepository receipts,
            WorkflowDefinitionRuntimeReader definitions, RuntimeHostSupport host, EntityManager em) {
        var beans = new StaticListableBeanFactory();
        return new WorkflowExecutionService(mock(WorkflowInstanceRepository.class), mock(WorkflowActivityRepository.class),
                mock(WorkflowTaskRepository.class), mock(WorkflowHistoryRepository.class), receipts, em, definitions,
                mock(TaskTypeCatalogService.class), json, host, mock(RuntimeDefinitionPins.class),
                mock(RuntimeVariables.class), mock(RuntimeServiceProgression.class), mock(WorkflowTaskEligibilityService.class),
                beans.getBeanProvider(WorkflowTaskSubmissionValidator.class),
                new WorkflowAuthorizationSupport(beans.getBeanProvider(WorkflowAuthorization.class)));
    }

    private StartWorkflow command(String key, Long family, Long version) {
        return new StartWorkflow(ID, key, family, version, null, null, null);
    }

    private void invalid(StartWorkflow command) {
        assertEquals(Category.INVALID_INPUT,
                assertThrows(WorkflowExecutionException.class, () -> RuntimeInputs.validateStartCommand(command, context)).category());
    }

    private void assertDifferent(StartWorkflow first, StartWorkflow second) {
        assertNotEquals(HexFormat.of().formatHex(json.fingerprint(first, context)),
                HexFormat.of().formatHex(json.fingerprint(second, context)));
    }
}
