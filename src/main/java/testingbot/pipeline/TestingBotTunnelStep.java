package testingbot.pipeline;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.testingbot.tunnel.App;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import testingbot.TestingBotBuildAction;
import testingbot.TestingBotBuildReportAction;
import testingbot.TestingBotCredentials;
import testingbot.TunnelManager;

public class TestingBotTunnelStep extends Step {

    /** Env var exposing the (possibly auto-generated) tunnel identifier to the build. */
    public static final String TESTINGBOT_TUNNEL_IDENTIFIER = "TESTINGBOT_TUNNEL_IDENTIFIER";

    private String options;
    private String credentialsId;

    @DataBoundConstructor
    public TestingBotTunnelStep(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getOptions() {
        return options;
    }

    @DataBoundSetter
    public void setOptions(String options) {
        this.options = options;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new TestingBotTunnelStepExecution(context, this);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getDisplayName() {
            return "TestingBotTunnel";
        }

        @Override
        public String getFunctionName() {
            return "testingbotTunnel";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return Set.of(Run.class, Computer.class, TaskListener.class);
        }

        @POST
        public ListBoxModel doFillCredentialsIdItems(final @AncestorInPath Item context) {
            if (context == null ? !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    : !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }
            return new StandardListBoxModel()
                    .withAll(CredentialsProvider.lookupCredentialsInItem(
                            TestingBotCredentials.class,
                            context,
                            ACL.SYSTEM2,
                            new ArrayList<DomainRequirement>()
                    ));
        }

        public FormValidation doCheckOptions(@QueryParameter String value) {
            List<String> problems = TunnelManager.unsupportedOptions(value);
            if (problems.isEmpty()) {
                return FormValidation.ok();
            }
            // A warning, not an error: unsupported options are ignored at runtime, they don't block the build.
            return FormValidation.warning(
                    "These tunnel option(s) are not recognized and will be ignored: " + String.join(", ", problems));
        }
    }

    public static class TestingBotTunnelStepExecution extends StepExecution {

        private static final long serialVersionUID = 1L;

        private final transient TestingBotTunnelStep step;
        private transient BodyExecution body;
        private String tunnelIdentifier;

        TestingBotTunnelStepExecution(StepContext context, TestingBotTunnelStep step) {
            super(context);
            this.step = step;
        }

        @Override
        public boolean start() throws Exception {
            StepContext context = getContext();
            Run<?, ?> run = context.get(Run.class);
            Computer computer = context.get(Computer.class);
            TaskListener listener = context.get(TaskListener.class);

            Job<?, ?> job = run.getParent();
            if (!(job instanceof TopLevelItem)) {
                throw new Exception(job + " must be a top-level job");
            }
            Node node = computer.getNode();
            if (node == null) {
                throw new Exception("computer does not correspond to a live node");
            }

            String options = step.getOptions() == null ? "" : step.getOptions().trim();

            TestingBotCredentials tbCredentials = context.get(TestingBotCredentials.class);
            if (tbCredentials == null) {
                tbCredentials = TestingBotCredentials.getCredentials(job, step.getCredentialsId());
            }
            if (tbCredentials == null) {
                throw new Exception("No TestingBot credentials found for id '" + step.getCredentialsId() + "'");
            }

            // Ensure the run carries the credentials so the secret can be masked in the log
            // and reports can be associated, even when this step is used without testingbot {}.
            if (run.getAction(TestingBotBuildAction.class) == null) {
                run.addAction(new TestingBotBuildAction(tbCredentials));
            }

            // Respect a user-supplied --tunnel-identifier (their Selenium/Appium capabilities may
            // target it); otherwise generate a unique one so parallel tunnels stay isolated.
            String userIdentifier = TunnelManager.extractTunnelIdentifier(options);
            tunnelIdentifier = (userIdentifier != null && !userIdentifier.isEmpty())
                    ? userIdentifier
                    : TunnelManager.generateTunnelIdentifier(run.getParent().getFullName(), run.getNumber());

            // Resolve secrets on the controller; only plaintext crosses the channel (over the encrypted remoting link).
            String key = tbCredentials.getKey();
            String secret = tbCredentials.getDecryptedSecret();

            HashMap<String, String> env = new HashMap<>();
            TunnelManager.populateCredentialEnv(env, tbCredentials);
            // Expose the build id and add the embeddable build-report page so all sessions using
            // build=$TESTINGBOT_BUILD are grouped and viewable inside Jenkins.
            env.put(TestingBotBuildReportAction.TESTINGBOT_BUILD, TestingBotBuildReportAction.attach(run, tbCredentials));
            env.put(TESTINGBOT_TUNNEL_IDENTIFIER, tunnelIdentifier);
            String hubHost = "localhost";
            String hubPort = Integer.toString(defaultSeleniumPort());
            // Generic host/port to point a Selenium or Appium client at the tunnel.
            env.put("HUB_HOST", hubHost);
            env.put("HUB_PORT", hubPort);
            // Selenium-specific aliases, kept for backwards compatibility.
            env.put("SELENIUM_HOST", hubHost);
            env.put("SELENIUM_PORT", hubPort);

            TunnelManager.startOnChannel(requireChannel(computer), key, secret, options, tunnelIdentifier, listener);

            try {
                body = getContext().newBodyInvoker()
                        .withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(env)))
                        .withContext(BodyInvoker.mergeConsoleLogFilters(getContext().get(ConsoleLogFilter.class), new SecretMaskingConsoleLogFilter(secret)))
                        .withCallback(new Callback(tunnelIdentifier))
                        .withDisplayName("TestingBot Tunnel")
                        .start();
            } catch (Exception e) {
                // The tunnel started but the body could not be invoked; stop it now, otherwise the
                // Callback that would normally stop it never runs and the tunnel leaks.
                stopTunnelQuietly(computer, tunnelIdentifier, listener);
                throw e;
            }

            return false;
        }

        private static VirtualChannel requireChannel(Computer computer) throws Exception {
            VirtualChannel channel = computer == null ? null : computer.getChannel();
            if (channel == null) {
                throw new Exception("The agent is offline; cannot start the TestingBot tunnel");
            }
            return channel;
        }

        /** Best-effort tunnel teardown that tolerates an offline agent (no channel). */
        private static void stopTunnelQuietly(Computer computer, String tunnelIdentifier, TaskListener listener) {
            TunnelManager.stopOnChannel(computer == null ? null : computer.getChannel(), tunnelIdentifier, listener);
        }

        @Override
        public void stop(@NonNull Throwable cause) throws Exception {
            if (body != null) {
                body.cancel(cause);
            }
        }

        private static int defaultSeleniumPort() {
            int port = new App().getSeleniumPort();
            return port > 0 ? port : 4445;
        }

        private static final class Callback extends BodyExecutionCallback.TailCall {

            private final String tunnelIdentifier;

            Callback(String tunnelIdentifier) {
                this.tunnelIdentifier = tunnelIdentifier;
            }

            @Override
            protected void finished(StepContext context) throws Exception {
                TaskListener listener = context.get(TaskListener.class);
                Computer computer = context.get(Computer.class);
                stopTunnelQuietly(computer, tunnelIdentifier, listener);
            }
        }
    }
}
