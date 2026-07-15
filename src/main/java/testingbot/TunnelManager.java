package testingbot;

import com.testingbot.tunnel.Api;
import com.testingbot.tunnel.App;
import hudson.model.TaskListener;
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
     * The set of tunnel options accepted from the user. Acts as an allow-list:
     * anything not declared here is rejected by the parser.
     */
    public static Options tunnelOptions() {
        Options options = new Options();
        options.addOption("h", "help", false, "Displays help text");
        options.addOption("d", "debug", false, "Enables debug messages");
        options.addOption(Option.builder("f").longOpt("readyfile").hasArg().argName("FILE")
                .desc("This file will be touched when the tunnel is ready for usage").build());
        options.addOption(Option.builder("P").longOpt("se-port").hasArg().argName("PORT")
                .desc("The local port your Selenium test should connect to.").build());
        options.addOption(Option.builder("F").longOpt("fast-fail-regexps").hasArg().argName("OPTIONS")
                .desc("Specify domains you don't want to proxy, comma separated.").build());
        options.addOption(Option.builder().longOpt("metrics-port").hasArg()
                .desc("Use the specified port to access metrics. Default port 8003").build());
        options.addOption(Option.builder("Y").longOpt("proxy").hasArg().argName("PROXYHOST:PROXYPORT")
                .desc("Specify an upstream proxy.").build());
        options.addOption(Option.builder("a").longOpt("auth").hasArgs().argName("host:port:user:passwd")
                .desc("Performs Basic Authentication for specific hosts.").build());
        options.addOption(Option.builder().longOpt("pac").hasArg()
                .desc("Proxy autoconfiguration. Should be a http(s) URL").build());
        options.addOption(Option.builder("z").longOpt("proxy-userpwd").hasArg().argName("user:pwd")
                .desc("Username and password required to access the proxy configured with --proxy.").build());
        options.addOption(Option.builder("l").longOpt("logfile").hasArg().argName("FILE")
                .desc("Write logging to a file.").build());
        options.addOption(Option.builder("i").longOpt("tunnel-identifier").hasArg().argName("id")
                .desc("Add an identifier to this tunnel connection.").build());
        options.addOption(Option.builder("p").longOpt("hubport").hasArg().argName("HUBPORT")
                .desc("Connect to port 80 on the hub instead of the default port 4444").build());
        options.addOption(Option.builder().longOpt("extra-headers").hasArg().argName("JSON")
                .desc("Inject extra headers in the requests the tunnel makes.").build());
        options.addOption(Option.builder("dns").longOpt("dns").hasArg().argName("server")
                .desc("Use a custom DNS server. For example: 8.8.8.8").build());
        options.addOption(Option.builder("w").longOpt("web").hasArg().argName("directory")
                .desc("Point to a directory for testing. Creates a local webserver.").build());
        options.addOption("x", "noproxy", false, "Do not start a local proxy");
        options.addOption("q", "nocache", false, "Bypass the caching proxy running on the tunnel VM.");
        options.addOption("j", "localproxy", true, "The port to launch the local proxy on (default 8087)");
        options.addOption(null, "doctor", false, "Perform checks to detect possible misconfiguration.");
        options.addOption("v", "version", false, "Displays the current version of this program");
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
     * Parses {@code rawOptions} and applies the subset that the embedded
     * {@link App} exposes setters for. Unparseable input is logged and ignored
     * rather than failing the build.
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
        boolean ready = false;
        while (!ready) {
            try {
                JSONObject response = api.pollTunnel(tunnelID);
                ready = "READY".equals(response.getString("state"));
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error while polling TestingBot tunnel status", ex);
                break;
            }
            if (!ready) {
                Thread.sleep(POLL_INTERVAL_MS);
            }
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
