package testingbot.pipeline;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.console.ConsoleLogFilter;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.kohsuke.stapler.verb.POST;
import testingbot.TestingBotBuildAction;
import testingbot.TestingBotBuildReportAction;
import testingbot.TestingBotCredentials;
import testingbot.TunnelManager;

/**
 * {@code testingbot(credentialsId) { ... }} — injects the TestingBot credential environment
 * variables (and provides the credentials to the block) around a Pipeline body.
 */
public class TestingBotStep extends Step {

    private final String credentialsId;

    @DataBoundConstructor
    public TestingBotStep(String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(context, this);
    }

    private static final class Execution extends StepExecution {

        private static final long serialVersionUID = 1L;

        private final transient TestingBotStep step;
        private transient BodyExecution body;

        Execution(StepContext context, TestingBotStep step) {
            super(context);
            this.step = step;
        }

        @Override
        public boolean start() throws Exception {
            StepContext context = getContext();
            Run<?, ?> run = context.get(Run.class);
            Job<?, ?> job = run.getParent();
            if (!(job instanceof TopLevelItem)) {
                throw new Exception(job + " must be a top-level job");
            }

            final TestingBotCredentials credentials = TestingBotCredentials.getCredentials(job, step.getCredentialsId());
            if (credentials == null) {
                throw new Exception("no credentials provided");
            }
            CredentialsProvider.track(run, credentials);

            HashMap<String, String> env = new HashMap<>();
            TunnelManager.populateCredentialEnv(env, credentials);
            if (run.getAction(TestingBotBuildAction.class) == null) {
                run.addAction(new TestingBotBuildAction(credentials));
            }
            // Expose the build id and add the embeddable build-report page so all sessions using
            // build=$TESTINGBOT_BUILD are grouped and viewable inside Jenkins.
            env.put(TestingBotBuildReportAction.TESTINGBOT_BUILD, TestingBotBuildReportAction.attach(run, credentials));

            body = context.newBodyInvoker()
                    .withContext(credentials)
                    .withContext(EnvironmentExpander.merge(context.get(EnvironmentExpander.class), new ExpanderImpl(env)))
                    .withContext(BodyInvoker.mergeConsoleLogFilters(context.get(ConsoleLogFilter.class), new SecretMaskingConsoleLogFilter(credentials.getDecryptedSecret())))
                    .withCallback(BodyExecutionCallback.wrap(context))
                    .start();
            return false;
        }

        @Override
        public void stop(@NonNull Throwable cause) throws Exception {
            if (body != null) {
                body.cancel(cause);
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "testingbot";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "TestingBot";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return Set.of(Run.class);
        }

        @Override
        public Set<Class<?>> getProvidedContext() {
            return Set.of(TestingBotCredentials.class);
        }

        @POST
        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(final @AncestorInPath Item context) {
            if (context == null ? !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    : !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }
            return new StandardListBoxModel().withMatching(
                    CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(TestingBotCredentials.class)),
                    CredentialsProvider.lookupCredentialsInItem(TestingBotCredentials.class, context, ACL.SYSTEM2,
                            new ArrayList<DomainRequirement>()));
        }
    }
}
