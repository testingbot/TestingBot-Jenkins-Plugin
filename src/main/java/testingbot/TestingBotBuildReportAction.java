package testingbot;

import hudson.model.Run;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import jenkins.model.RunAction2;

/**
 * Adds a "TestingBot Build" page to a build that embeds the TestingBot build report (all TestingBot
 * sessions grouped under this build) in an iframe.
 *
 * <p>Tests associate themselves with the build by using the {@code TESTINGBOT_BUILD} environment
 * variable (exposed by the {@code testingbot}/{@code testingbotTunnel} steps and the freestyle build
 * wrapper) as their {@code build} desired capability. The embedded report lives at
 * {@code /mini/builds/<clientKey>/<buildId>} and is authenticated with a plain
 * {@code MD5(clientKey:clientSecret:buildId)} token — computed once here so the raw secret is never
 * stored on the build.</p>
 */
public class TestingBotBuildReportAction implements RunAction2 {

    /**
     * Env var holding the build identifier. Tests should pass its value as their {@code build}
     * desired capability so their sessions are grouped under this Jenkins build's report — the
     * TestingBot analog of Sauce Labs' {@code SAUCE_BUILD_NAME}.
     */
    public static final String TESTINGBOT_BUILD = "TESTINGBOT_BUILD";

    private transient Run<?, ?> run;
    private final String clientKey;
    private final String buildId;
    private final String authHash;

    private TestingBotBuildReportAction(String clientKey, String buildId, String authHash) {
        this.clientKey = clientKey;
        this.buildId = buildId;
        this.authHash = authHash;
    }

    /**
     * Builds the action for a run, computing the embed auth token from the credentials.
     */
    public static TestingBotBuildReportAction create(Run<?, ?> run, TestingBotCredentials credentials) {
        String buildId = buildIdFor(run);
        String auth = md5(credentials.getKey() + ":" + credentials.getDecryptedSecret() + ":" + buildId);
        return new TestingBotBuildReportAction(credentials.getKey(), buildId, auth);
    }

    /**
     * The value tests should use as their {@code build} capability to appear in this build's report.
     * Sanitized so it is safe both as a capability value and as a URL path segment.
     */
    public static String buildIdFor(Run<?, ?> run) {
        return (run.getParent().getFullName() + "_" + run.getNumber()).replaceAll("[^A-Za-z0-9._-]", "-");
    }

    /**
     * Adds the report action to the run (once) and returns the build identifier to expose as
     * {@link #TESTINGBOT_BUILD}. Callers that only need the identifier — e.g. a run without
     * resolvable credentials — can use {@link #buildIdFor(Run)} directly.
     */
    public static String attach(Run<?, ?> run, TestingBotCredentials credentials) {
        if (run.getAction(TestingBotBuildReportAction.class) == null) {
            run.addAction(create(run, credentials));
        }
        return buildIdFor(run);
    }

    public String getReportUrl() {
        return "https://testingbot.com/mini/builds/" + clientKey + "/" + buildId + "?auth=" + authHash;
    }

    public String getBuildId() {
        return buildId;
    }

    public Run<?, ?> getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/testingbot/images/logo.svg";
    }

    @Override
    public String getDisplayName() {
        return "TestingBot Build";
    }

    @Override
    public String getUrlName() {
        return "testingbot-build";
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
