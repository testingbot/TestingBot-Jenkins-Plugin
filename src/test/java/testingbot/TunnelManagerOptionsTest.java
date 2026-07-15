package testingbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingbot.tunnel.App;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Pure unit tests for {@link TunnelManager#applyOptions} — verifies that supported options are
 * applied to the embedded {@link App} and that unsupported options are rejected rather than
 * silently ignored. No Jenkins harness is booted, so the fat-jar Jetty clash does not apply here.
 */
public class TunnelManagerOptionsTest {

    @Test
    public void appliesDebug() {
        App app = new App();
        TunnelManager.applyOptions(app, "--debug", TaskListener.NULL);
        assertThat(app.isDebugMode()).isTrue();
    }

    @Test
    public void appliesIdentifierAndProxy() {
        App app = new App();
        TunnelManager.applyOptions(app, "-i my-id --proxy host:8080 --proxy-userpwd user:pw", TaskListener.NULL);
        assertThat(app.getTunnelIdentifier()).isEqualTo("my-id");
        assertThat(app.getProxy()).isEqualTo("host:8080");
        assertThat(app.getProxyAuth()).isEqualTo("user:pw");
    }

    @Test
    public void appliesMetricsPort() {
        App app = new App();
        TunnelManager.applyOptions(app, "--metrics-port 9000", TaskListener.NULL);
        assertThat(app.getMetricsPort()).isEqualTo(9000);
    }

    @Test
    public void appliesKnownOptionsAndReportsUnsupported() throws Exception {
        App app = new App();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TaskListener listener = new StreamTaskListener(out, StandardCharsets.UTF_8);
        // --se-port is not supported: the known --debug still applies, and the rest is reported, not silently dropped.
        TunnelManager.applyOptions(app, "--debug --se-port 4446", listener);
        assertThat(app.isDebugMode()).isTrue();
        assertThat(out.toString("UTF-8")).contains("se-port");
    }

    @Test
    public void ignoresBlankOptions() {
        App app = new App();
        TunnelManager.applyOptions(app, "   ", TaskListener.NULL);
        assertThat(app.isDebugMode()).isFalse();
    }
}
