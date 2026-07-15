package testingbot;

import com.testingbot.tunnel.Api;
import com.testingbot.tunnel.App;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.json.JSONObject;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Shared TestingBot Tunnel lifecycle helper used by both the freestyle
 * {@link TestingBotBuildWrapper} and the pipeline {@code testingbotTunnel} step.
 *
 * <p>Centralizes credential env-var population, tunnel option parsing and the
 * boot/poll/stop logic that used to be duplicated across those call sites.</p>
 */
public final class TunnelManager {

    private static final Logger LOGGER = Logger.getLogger(TunnelManager.class.getName());
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
     * The set of tunnel options accepted from the user. Acts as an allow-list limited to the
     * options the embedded {@link App} can actually apply; anything else (e.g. {@code --se-port},
     * {@code --pac}, {@code --logfile}, {@code --noproxy}) is rejected by the parser rather than
     * silently ignored, and reported to the build log by {@link #applyOptions}.
     */
    public static Options tunnelOptions() {
        Options options = new Options();
        options.addOption("d", "debug", false, "Enables debug messages");
        options.addOption(Option.builder("i").longOpt("tunnel-identifier").hasArg().argName("id")
                .desc("Add an identifier to this tunnel connection.").build());
        options.addOption(Option.builder("Y").longOpt("proxy").hasArg().argName("PROXYHOST:PROXYPORT")
                .desc("Specify an upstream proxy.").build());
        options.addOption(Option.builder("z").longOpt("proxy-userpwd").hasArg().argName("user:pwd")
                .desc("Username and password required to access the proxy configured with --proxy.").build());
        options.addOption(Option.builder().longOpt("metrics-port").hasArg()
                .desc("Use the specified port to access metrics. Default port 8003").build());
        options.addOption(Option.builder("a").longOpt("auth").hasArgs().argName("host:port:user:passwd")
                .desc("Performs Basic Authentication for specific hosts.").build());
        return options;
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
     * Parses {@code rawOptions} against the supported allow-list and applies each option to the
     * {@link App}. Unrecognized or unparseable input (including options the embedded tunnel cannot
     * apply) is reported to the build log and ignored rather than failing the build.
     */
    static void applyOptions(App app, String rawOptions, TaskListener listener) {
        String trimmed = rawOptions == null ? "" : rawOptions.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        try {
            CommandLine cmd = new DefaultParser().parse(tunnelOptions(), tokenize(trimmed));
            if (cmd.hasOption("debug")) {
                app.setDebugMode(true);
            }
            if (cmd.hasOption("tunnel-identifier")) {
                app.setTunnelIdentifier(cmd.getOptionValue("tunnel-identifier"));
            }
            if (cmd.hasOption("proxy")) {
                app.setProxy(cmd.getOptionValue("proxy"));
            }
            if (cmd.hasOption("proxy-userpwd")) {
                app.setProxyAuth(cmd.getOptionValue("proxy-userpwd"));
            }
            if (cmd.hasOption("metrics-port")) {
                try {
                    app.setMetricsPort(Integer.parseInt(cmd.getOptionValue("metrics-port")));
                } catch (NumberFormatException nfe) {
                    listener.getLogger().println("[TestingBot] Ignoring invalid --metrics-port value: "
                            + cmd.getOptionValue("metrics-port"));
                }
            }
            if (cmd.hasOption("auth")) {
                app.setBasicAuth(cmd.getOptionValues("auth"));
            }
        } catch (ParseException e) {
            listener.getLogger().println("[TestingBot] Could not parse tunnel options ('" + trimmed
                    + "'): " + e.getMessage() + " - starting tunnel with defaults.");
            LOGGER.log(Level.WARNING, "Failed to parse TestingBot tunnel options", e);
        }
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
            JSONObject response = api.pollTunnel(tunnelID);
            if ("READY".equals(response.getString("state"))) {
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
