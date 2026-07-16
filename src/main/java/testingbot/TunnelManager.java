package testingbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.testingbot.tunnel.Api;
import com.testingbot.tunnel.App;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                        app.setBasicAuth(new String[]{tokens[++i]});
                    } else {
                        ignored.add(token + " (missing value)");
                    }
                    break;
                default:
                    ignored.add(token);
            }
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
}
