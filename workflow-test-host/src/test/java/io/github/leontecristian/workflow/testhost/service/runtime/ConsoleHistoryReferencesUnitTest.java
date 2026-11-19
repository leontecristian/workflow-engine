package io.github.leontecristian.workflow.testhost.service.runtime;

import static io.github.leontecristian.workflow.testhost.service.runtime.ConsoleHistoryReferences.Kind.EVENT;
import static io.github.leontecristian.workflow.testhost.service.runtime.ConsoleHistoryReferences.Kind.PAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import org.junit.jupiter.api.Test;

class ConsoleHistoryReferencesUnitTest {
    @Test
    void resolvesOnlyInTheSameActorInstanceAndPurpose() {
        var references = new ConsoleHistoryReferences(10);
        long sequence = 9_007_199_254_740_993L;
        String event = references.issue(EVENT, "reviewer", 42, sequence);

        assertThat(references.resolve(EVENT, "reviewer", 42, event)).isEqualTo(sequence);
        assertThat(references.issue(EVENT, "reviewer", 42, sequence)).isEqualTo(event);
        assertThat(references.issue(PAGE, "reviewer", 42, sequence)).isNotEqualTo(event);
        assertThatThrownBy(() -> references.resolve(PAGE, "reviewer", 42, event)).isInstanceOf(ConsoleException.class);
        assertThatThrownBy(() -> references.resolve(EVENT, "other", 42, event)).isInstanceOf(ConsoleException.class);
        assertThatThrownBy(() -> references.resolve(EVENT, "reviewer", 43, event)).isInstanceOf(ConsoleException.class);
    }

    @Test
    void evictsLeastRecentlyUsedEntriesAndDoesNotRecoverThemInANewLookup() {
        var references = new ConsoleHistoryReferences(2);
        String retained = references.issue(EVENT, "reviewer", 42, 1);
        String evicted = references.issue(PAGE, "reviewer", 42, 2);
        references.resolve(EVENT, "reviewer", 42, retained);
        references.issue(EVENT, "reviewer", 42, 3);

        assertThat(references.resolve(EVENT, "reviewer", 42, retained)).isEqualTo(1);
        assertThatThrownBy(() -> references.resolve(PAGE, "reviewer", 42, evicted)).isInstanceOf(ConsoleException.class);
        var restarted = new ConsoleHistoryReferences(2);
        assertThatThrownBy(() -> restarted.resolve(EVENT, "reviewer", 42, retained)).isInstanceOf(ConsoleException.class);
        assertThat(restarted.issue(EVENT, "reviewer", 42, 1)).isNotEqualTo(retained);
    }
}
