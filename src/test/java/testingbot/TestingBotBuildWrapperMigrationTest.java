package testingbot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;

/**
 * Verifies the backward-compatible migration of the legacy {@code enableSSH} flag (persisted by
 * older versions in job config.xml) to the renamed {@code useTunnel} field. Pure unit test — no
 * Jenkins harness required.
 */
public class TestingBotBuildWrapperMigrationTest {

    private static Object invokeReadResolve(TestingBotBuildWrapper w) throws Exception {
        Method readResolve = TestingBotBuildWrapper.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        return readResolve.invoke(w);
    }

    @Test
    public void migratesLegacyEnableSshTrueToUseTunnel() throws Exception {
        TestingBotBuildWrapper w = new TestingBotBuildWrapper("cred", false);
        // Simulate an old config.xml where the flag was persisted under the legacy field name.
        Field legacy = TestingBotBuildWrapper.class.getDeclaredField("enableSSH");
        legacy.setAccessible(true);
        legacy.set(w, Boolean.TRUE);

        invokeReadResolve(w);

        assertThat(w.isUseTunnel()).isTrue();
        // Legacy field is cleared so it is not re-serialized.
        assertThat(legacy.get(w)).isNull();
    }

    @Test
    public void newConfigWithoutLegacyFieldIsUnchanged() throws Exception {
        TestingBotBuildWrapper w = new TestingBotBuildWrapper("cred", true);
        invokeReadResolve(w);
        assertThat(w.isUseTunnel()).isTrue();
    }
}
