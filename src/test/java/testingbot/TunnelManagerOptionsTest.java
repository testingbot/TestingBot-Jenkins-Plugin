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

    private static String applyAndCapture(App app, String options) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TunnelManager.applyOptions(app, options, new StreamTaskListener(out, StandardCharsets.UTF_8));
        return out.toString("UTF-8");
    }

    @Test
    public void reportsMissingValueForEveryValueTakingOptionAtEnd() throws Exception {
        // Each value-taking option at the end of the string has no value: it must be reported, not silently skipped.
        assertThat(applyAndCapture(new App(), "--tunnel-identifier")).contains("tunnel-identifier", "missing value");
        assertThat(applyAndCapture(new App(), "--proxy")).contains("proxy", "missing value");
        assertThat(applyAndCapture(new App(), "--proxy-userpwd")).contains("proxy-userpwd", "missing value");
        assertThat(applyAndCapture(new App(), "--metrics-port")).contains("metrics-port", "missing value");
        assertThat(applyAndCapture(new App(), "--auth")).contains("auth", "missing value");
    }

    @Test
    public void reportsMissingValueWhenFollowedByAnotherFlagAndStillAppliesTheFlag() throws Exception {
        App app = new App();
        // --proxy is missing its value (next token is a flag) → reported; --debug still applies.
        String log = applyAndCapture(app, "--proxy --debug");
        assertThat(app.getProxy()).isNull();
        assertThat(app.isDebugMode()).isTrue();
        assertThat(log).contains("proxy", "missing value");
    }

    @Test
    public void reportsInvalidMetricsPort() throws Exception {
        App app = new App();
        String log = applyAndCapture(app, "--metrics-port notanumber");
        assertThat(log).contains("metrics-port", "invalid value");
    }

    @Test
    public void accumulatesRepeatedAuthOptions() {
        App app = new App();
        TunnelManager.applyOptions(app, "--auth host1:80:u:p --auth host2:80:u:p", TaskListener.NULL);
        assertThat(app.getBasicAuth()).containsExactly("host1:80:u:p", "host2:80:u:p");
    }

    @Test
    public void extractsUserTunnelIdentifier() {
        assertThat(TunnelManager.extractTunnelIdentifier("--debug --tunnel-identifier my-suite")).isEqualTo("my-suite");
        assertThat(TunnelManager.extractTunnelIdentifier("-i short-id")).isEqualTo("short-id");
        assertThat(TunnelManager.extractTunnelIdentifier("--debug --proxy host:8080")).isNull();
        assertThat(TunnelManager.extractTunnelIdentifier(null)).isNull();
    }
}
