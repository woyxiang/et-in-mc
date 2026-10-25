package dev.etmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.etmc.config.NetworkProfile;
import dev.etmc.core.EasyTierProcessManager;
import dev.etmc.core.ProcessState;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the state machine against stub commands instead of real EasyTier
 * (Linux-only stubs; real binary integration is covered by P0 scripts).
 */
class ProcessManagerTest {

    private NetworkProfile stubProfile() {
        return new NetworkProfile()
                .role(NetworkProfile.Role.HOST)
                .binaryPath("stub")
                .networkName("n")
                .networkSecret("s")
                .listenPort(21010)
                .rpcPort(25888)
                .hostIp("10.126.126.1");
    }

    @Test
    void startStopHappyPath(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").contains("Linux"));
        var mgr = new EasyTierProcessManager(stubProfile(),
                p -> List.of("/bin/sleep", "60"), tmp, false, 0, 500);
        mgr.start();
        assertEquals(ProcessState.STARTING, mgr.state());
        assertTrue(mgr.pid().isPresent());
        // wait past grace window -> CONNECTING is not reachable without RPC (P2);
        // state stays STARTING but process alive; then stop cleanly.
        mgr.stop();
        assertEquals(ProcessState.STOPPED, mgr.state());
        assertTrue(mgr.pid().isEmpty());
        // idempotent
        mgr.stop();
        assertEquals(ProcessState.STOPPED, mgr.state());
    }

    @Test
    void doubleStartRejected(@TempDir Path tmp) {
        Assumptions.assumeTrue(System.getProperty("os.name").contains("Linux"));
        var mgr = new EasyTierProcessManager(stubProfile(),
                p -> List.of("/bin/sleep", "60"), tmp, false, 0, 500);
        mgr.start();
        assertThrows(IllegalStateException.class, mgr::start);
        mgr.stop();
    }

    @Test
    void quickDeathBecomesErrorNotRestart(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").contains("Linux"));
        // exits with code 1 immediately - simulates bind failure (P0 finding #3)
        Path failingScript = tmp.resolve("failing-core.sh");
        java.nio.file.Files.writeString(failingScript, "#!/bin/sh\nexit 1\n");
        failingScript.toFile().setExecutable(true);
        var mgr = new EasyTierProcessManager(stubProfile(),
                p -> List.of(failingScript.toString()), tmp, true, 5, 3000);
        mgr.start();
        long deadline = System.currentTimeMillis() + 10_000;
        while (mgr.state() != ProcessState.ERROR && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(100);
        }
        assertEquals(ProcessState.ERROR, mgr.state());
    }

    @Test
    void missingBinaryIsError(@TempDir Path tmp) {
        Assumptions.assumeTrue(System.getProperty("os.name").contains("Linux"));
        var mgr = new EasyTierProcessManager(stubProfile(),
                p -> List.of("/nonexistent/et-binary", "--x"), tmp, false, 0, 500);
        assertThrows(IllegalStateException.class, mgr::start);
        assertEquals(ProcessState.ERROR, mgr.state());
    }

    @Test
    void secretNeverInCapturedLog(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").contains("Linux"));
        NetworkProfile p = stubProfile().networkSecret("TOPSECRET");
        // a stub that echoes its args including the secret
        var mgr = new EasyTierProcessManager(p,
                args -> List.of("/bin/sh", "-c",
                        "echo running with " + p.networkSecret() + "; sleep 60"),
                tmp, false, 0, 500);
        try {
            mgr.start();
            Thread.sleep(500);
            assertTrue(mgr.recentLog().stream().noneMatch(l -> l.contains("TOPSECRET")),
                    "secret leaked into log ring: " + mgr.recentLog());
            assertTrue(mgr.recentLog().stream().anyMatch(l -> l.contains("***")));
        } finally {
            mgr.stop();
        }
    }
}
