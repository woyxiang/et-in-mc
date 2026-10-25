package dev.etmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class EasyTierBinaryManagerTest {
    private static final String CORE = System.getProperty("etmc.core.bin",
            "/home/ecs-user/tools/easytier/easytier-linux-x86_64/easytier-core");

    @Test
    void queryVersionParsesPinnedCore() {
        Assumptions.assumeTrue(Path.of(CORE).toFile().canExecute(), "core not present");
        String v = EasyTierBinaryManager.queryVersion(Path.of(CORE));
        assertTrue(v != null && v.startsWith("2.6"), "unexpected version: " + v);
    }

    @Test
    void validateReportsMissingBinary() {
        var info = EasyTierBinaryManager.validate("/no/such/easytier-core-xyz");
        assertTrue(!info.ok());
        assertTrue(info.problems().get(0).contains("not found"));
    }

    @Test
    void validateOkForPresentBinary() {
        Assumptions.assumeTrue(Path.of(CORE).toFile().canExecute(), "core not present");
        var info = EasyTierBinaryManager.validate(CORE);
        assertTrue(info.version().startsWith(EasyTierBinaryManager.PINNED_VERSION), "got " + info.version());
        assertTrue(info.ok(), "problems: " + info.problems());
    }
}
