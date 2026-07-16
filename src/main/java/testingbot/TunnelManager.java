package testingbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.testingbot.tunnel.Api;
import com.testingbot.tunnel.App;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.security.MasterToSlaveCallable;

/**
 * Shared TestingBot Tunnel lifecycle helper used by both the freestyle
 * {@link TestingBotBuildWrapper} and the pipeline {@code testingbotTunnel} step.
 *
 * <p>Centralizes credential env-var population, tunnel option parsing and the
 * boot/poll/stop logic that used to be duplicated across those call sites.</p>
 */
public final class TunnelManager {

    private static final int POLL_INTERVAL_MS = 3000;
    private static final long READY_TIMEOUT_MS = 300_000L;
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    private TunnelManager() {
    }

    /**
     * Populates the four documented credential environment variables (both the
     * {@code TESTINGBOT_*} and short {@code TB_*} aliases) into {@code env}.
     */
    public static void populateCredentialEnv(Map<String, String> env, TestingBotCredentials credentials) {
        env.put(TestingBotBuildWrapper.TESTINGBOT_KEY, credentials.getKey());
        env.put(TestingBotBuildWrapper.TB_KEY, credentials.getKey());
        env.put(TestingBotBuildWrapper.TESTINGBOT_SECRET, credentials.getDecryptedSecret());
        env.put(TestingBotBuildWrapper.TB_SECRET, credentials.getDecryptedSecret());
    }

    /**
     * Splits a free-form option string into tokens, honoring single/double quotes
     * so that values containing spaces are preserved as a single argument.
     */
    static String[] tokenize(String options) {
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(options);
        while (m.find()) {
            if (m.group(1) != null) {
                tokens.add(m.group(1));
            } else if (m.group(2) != null) {
                tokens.add(m.group(2));
            } else {
                tokens.add(m.group(3));
            }
        }
        return tokens.toArray(new String[0]);
    }

    /**
     * Parses {@code rawOptions} and applies the subset of tunnel options that the embedded
     * {@link App} supports: {@code --debug}, {@code --tunnel-identifier}, {@code --proxy},
     * {@code --proxy-userpwd}, {@code --metrics-port} and {@code --auth}. Any other option is not
     * silently ignored — it is reported to the build log.
     *
     * <p>Parsing is done by hand rather than via commons-cli because the TestingBot tunnel jar
     * bundles its own (older) copy of commons-cli, which shadows the plugin's and breaks the
     * modern parser API at runtime.</p>
     */
    static void applyOptions(App app, String rawOptions, TaskListener listener) {
        String trimmed = rawOptions == null ? "" : rawOptions.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String[] tokens = tokenize(trimmed);
        List<String> ignored = new ArrayList<>();
        List<String> auth = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            switch (token) {
                case "-d":
                case "--debug":
                    app.setDebugMode(true);
                    break;
                case "-i":
                case "--tunnel-identifier":
                    if (hasValue(tokens, i)) {
                        app.setTunnelIdentifier(tokens[++i]);
                    } else {
                        ignored.add(token + " (missing value)");
                    }
                    break;
                case "-Y":
                case "--proxy":
                    if (hasValue(tokens, i)) {
                        app.setProxy(tokens[++i]);
                    } else {
                        ignored.add(token + " (missing value)");
                    }
                    break;
                case "-z":
                case "--proxy-userpwd":
                    if (hasValue(tokens, i)) {
                        app.setProxyAuth(tokens[++i]);
                    } else {
                        ignored.add(token + " (missing value)");
                    }
                    break;
                case "--metrics-port":
                    if (hasValue(tokens, i)) {
                        String value = tokens[++i];
                        try {
                            app.setMetricsPort(Integer.parseInt(value));
                        } catch (NumberFormatException nfe) {
                            ignored.add(token + " " + value + " (invalid value)");
                        }
                    } else {
                        ignored.add(token + " (missing value)");
                    }
                    break;
                case "-a":
                case "--auth":
                    if (hasValue(tokens, i)) {
                        auth.add(tokens[++i]);
                    } else {
                        ignored.add(token + " (missing value)");
                    }
                    break;
                default:
                    ignored.add(token);
            }
        }
        if (!auth.isEmpty()) {
            // --auth may be repeated for multiple hosts; apply them all at once.
            app.setBasicAuth(auth.toArray(new String[0]));
        }
        if (!ignored.isEmpty()) {
            listener.getLogger().println("[TestingBot] Ignoring invalid or unsupported tunnel option(s): "
                    + String.join(", ", ignored));
        }
    }

    private static boolean hasValue(String[] tokens, int index) {
        return index + 1 < tokens.length && !tokens[index + 1].startsWith("-");
    }

    /**
     * Returns the value of a user-supplied {@code --tunnel-identifier} / {@code -i} option, or
     * {@code null} if the options don't specify one.
     */
    public static String extractTunnelIdentifier(String rawOptions) {
        if (rawOptions == null) {
            return null;
        }
        String[] tokens = tokenize(rawOptions.trim());
        for (int i = 0; i < tokens.length; i++) {
            if (("-i".equals(tokens[i]) || "--tunnel-identifier".equals(tokens[i])) && hasValue(tokens, i)) {
                return tokens[i + 1];
            }
        }
        return null;
    }

    /**
     * Configures and boots the given {@link App}, blocking until the tunnel
     * reports READY (or a poll error occurs). Credentials are passed as already
     * decrypted strings so this can run safely on an agent.
     */
    public static void start(App app, String key, String secret, String options,
            String tunnelIdentifier, TaskListener listener) throws Exception {
        app.setClientKey(key);
        app.setClientSecret(secret);
        applyOptions(app, options, listener);
        if (tunnelIdentifier != null && !tunnelIdentifier.isEmpty()) {
            app.setTunnelIdentifier(tunnelIdentifier);
        }
        listener.getLogger().println("Starting TestingBot Tunnel");
        app.boot();
        Api api = app.getApi();
        String tunnelID = Integer.toString(app.getTunnelID());
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (true) {
            // Let polling failures propagate: startup must never report success after an error.
            JsonNode response = api.pollTunnel(tunnelID);
            JsonNode state = response.get("state");
            if (state != null && "READY".equals(state.asText())) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IOException("Timed out waiting for TestingBot tunnel " + tunnelID + " to become ready");
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    /**
     * Stops the given tunnel, tolerating a {@code null} handle (already stopped
     * or never started).
     */
    public static void stop(App app, TaskListener listener) {
        if (app != null) {
            listener.getLogger().println("Stopping TestingBot Tunnel");
            app.stop();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Remote (agent) tunnel lifecycle.
    //
    // The tunnel must boot on the same node where the tests run, so that a test connecting to
    // {@code localhost} reaches the tunnel. Both the freestyle build wrapper and the pipeline step
    // therefore run the tunnel inside a MasterToSlaveCallable on the build node's channel. The live
    // App is kept in a per-JVM static registry (co-located with the tunnel on the agent) so teardown
    // can find and stop it again without serializing the handle back across the channel.
    // ------------------------------------------------------------------------------------------

    /** Tunnels running in this JVM, keyed by tunnel identifier. Lives in the agent's static space. */
    private static final ConcurrentHashMap<String, App> RUNNING_TUNNELS = new ConcurrentHashMap<>();

    /** Monotonic suffix so multiple generated identifiers within one run stay distinct. */
    private static final AtomicInteger TUNNEL_SEQ = new AtomicInteger();

    /**
     * Generates a tunnel identifier unique to a build (and to each tunnel within it), so that
     * parallel tunnels on the same node stay isolated.
     */
    public static String generateTunnelIdentifier(String jobFullName, int buildNumber) {
        return "jenkins-" + jobFullName.replace('/', '-') + "-" + buildNumber + "-" + TUNNEL_SEQ.incrementAndGet();
    }

    /**
     * Boots a tunnel on the given channel's node and registers it under {@code tunnelIdentifier}.
     * Credentials are passed as already-decrypted strings (only plaintext crosses the encrypted
     * remoting link, never the credential object).
     */
    public static void startOnChannel(VirtualChannel channel, String key, String secret, String options,
            String tunnelIdentifier, TaskListener listener) throws Exception {
        channel.call(new StartTunnelHandler(key, secret, options, tunnelIdentifier, listener));
    }

    /**
     * Stops the tunnel registered under {@code tunnelIdentifier} on the given channel's node.
     * Best-effort: a {@code null} channel (offline agent) or a remoting failure is tolerated so a
     * teardown never fails the build; an offline agent is reported to the log.
     */
    public static void stopOnChannel(VirtualChannel channel, String tunnelIdentifier, TaskListener listener) {
        if (channel == null) {
            RUNNING_TUNNELS.remove(tunnelIdentifier);
            if (listener != null) {
                listener.getLogger().println("[TestingBot] Agent offline: remote teardown of tunnel "
                        + tunnelIdentifier + " could not be performed. It may still be running on the agent "
                        + "and should stop when the agent process exits (best-effort cleanup).");
            }
            return;
        }
        try {
            channel.call(new StopTunnelHandler(tunnelIdentifier, listener));
        } catch (InterruptedException ie) {
            // Restore the interrupt status; teardown stays best-effort and does not rethrow.
            Thread.currentThread().interrupt();
            if (listener != null) {
                listener.getLogger().println("[TestingBot] Interrupted while stopping tunnel " + tunnelIdentifier);
            }
        } catch (Exception e) {
            // Best-effort, but do not silently discard the failure — record why teardown could not complete.
            if (listener != null) {
                listener.getLogger().println("[TestingBot] Failed to stop tunnel " + tunnelIdentifier + ": " + e);
            }
        }
    }

    private static final class StartTunnelHandler extends MasterToSlaveCallable<Void, Exception> {

        private static final long serialVersionUID = 1L;
        private final String key;
        private final String secret;
        private final String tunnelOptions;
        private final String tunnelIdentifier;
        private final TaskListener listener;

        StartTunnelHandler(String key, String secret, String tunnelOptions, String tunnelIdentifier, TaskListener listener) {
            this.key = key;
            this.secret = secret;
            this.tunnelOptions = tunnelOptions;
            this.tunnelIdentifier = tunnelIdentifier;
            this.listener = listener;
        }

        @Override
        public Void call() throws Exception {
            App app = new App();
            // Reserve the identifier before booting: a duplicate identifier is rejected rather than
            // silently overwriting (and leaking) an existing tunnel, and teardown can find and stop
            // the App even while it is still booting.
            App previous = RUNNING_TUNNELS.putIfAbsent(tunnelIdentifier, app);
            if (previous != null) {
                try {
                    app.stop();
                } catch (Exception ignored) {
                    // best effort
                }
                throw new IllegalStateException(
                        "A TestingBot tunnel with identifier '" + tunnelIdentifier + "' is already running");
            }
            try {
                start(app, key, secret, tunnelOptions, tunnelIdentifier, listener);
            } catch (Exception e) {
                // Startup failed: remove only our own registry entry (leaving any concurrent owner
                // untouched) and stop the App so we don't leak a half-booted tunnel.
                RUNNING_TUNNELS.remove(tunnelIdentifier, app);
                try {
                    app.stop();
                } catch (Exception ignored) {
                    // best effort
                }
                throw e;
            }
            return null;
        }
    }

    private static final class StopTunnelHandler extends MasterToSlaveCallable<Void, Exception> {

        private static final long serialVersionUID = 1L;
        private final String tunnelIdentifier;
        private final TaskListener listener;

        StopTunnelHandler(String tunnelIdentifier, TaskListener listener) {
            this.tunnelIdentifier = tunnelIdentifier;
            this.listener = listener;
        }

        @Override
        public Void call() {
            App app = RUNNING_TUNNELS.remove(tunnelIdentifier);
            stop(app, listener);
            return null;
        }
    }
}
