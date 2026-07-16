package testingbot;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.AbstractTestResultAction;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Publishes the TestingBot outcome of a build as a GitHub check (✓/✗ on the commit &amp; PR), via the
 * {@code checks-api} plugin. The check <em>name</em> (its context) and <em>summary</em> (message) are
 * configurable — the modern equivalent of the "custom context + message" that Sauce Labs exposes for
 * upstream GitHub Pull Request Builder jobs.
 *
 * <p>Works in freestyle (post-build action) and Pipeline (the {@code testingbotChecks} step). The
 * actual GitHub delivery is done by the {@code github-checks} plugin; when it is not installed the
 * publish is a safe no-op (a {@code NullChecksPublisher}).</p>
 */
public class TestingBotChecksPublisher extends Recorder implements SimpleBuildStep {

    private static final String DEFAULT_NAME = "TestingBot";

    private String name;
    private String message;

    @DataBoundConstructor
    public TestingBotChecksPublisher() {
    }

    /** The check name — the "context" shown on the commit/PR. Defaults to {@code TestingBot}. */
    public String getName() {
        return Util.fixEmptyAndTrim(name) == null ? DEFAULT_NAME : name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = Util.fixEmptyAndTrim(name);
    }

    /** Optional custom summary/message for the check; a session summary is generated when empty. */
    public String getMessage() {
        return message;
    }

    @DataBoundSetter
    public void setMessage(String message) {
        this.message = Util.fixEmptyAndTrim(message);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull EnvVars env,
            @NonNull Launcher launcher, @NonNull TaskListener listener) {
        int total = 0;
        int passed = 0;
        List<String> rows = new ArrayList<>();

        AbstractTestResultAction<?> testResultAction = run.getAction(AbstractTestResultAction.class);
        if (testResultAction != null && testResultAction.getResult() instanceof TestResult) {
            TestResult testResult = (TestResult) testResultAction.getResult();
            for (SuiteResult sr : testResult.getSuites()) {
                for (CaseResult cr : sr.getCases()) {
                    if (TestingBotReportFactory.findSessionIDs(cr).isEmpty()) {
                        continue;
                    }
                    total++;
                    boolean casePassed = cr.isPassed();
                    if (casePassed) {
                        passed++;
                    }
                    rows.add("| " + cr.getFullName() + " | " + (casePassed ? "✅ passed" : "❌ failed") + " |");
                }
            }
        }

        ChecksConclusion conclusion;
        if (total > 0) {
            conclusion = passed == total ? ChecksConclusion.SUCCESS : ChecksConclusion.FAILURE;
        } else {
            conclusion = fromBuildResult(run.getResult());
        }

        String summary = getMessage() != null ? getMessage() : defaultSummary(total, passed);

        StringBuilder text = new StringBuilder();
        if (total > 0) {
            text.append("| Test | Result |\n| --- | --- |\n");
            for (String row : rows) {
                text.append(row).append('\n');
            }
        }
        TestingBotBuildReportAction reportAction = run.getAction(TestingBotBuildReportAction.class);
        if (reportAction != null) {
            text.append("\n[View the full TestingBot build report](").append(reportAction.getReportUrl()).append(")\n");
        }

        ChecksOutput output = new ChecksOutput.ChecksOutputBuilder()
                .withTitle(getName())
                .withSummary(summary)
                .withText(text.toString())
                .build();

        ChecksDetails details = new ChecksDetails.ChecksDetailsBuilder()
                .withName(getName())
                .withStatus(ChecksStatus.COMPLETED)
                .withConclusion(conclusion)
                .withDetailsURL(detailsUrl(run, reportAction))
                .withStartedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(run.getStartTimeInMillis()), ZoneOffset.UTC))
                .withCompletedAt(LocalDateTime.now(ZoneOffset.UTC))
                .withOutput(output)
                .build();

        // A NullChecksPublisher is returned (and this is a no-op) when no GitHub checks implementation
        // is installed, so this is always safe to call.
        ChecksPublisherFactory.fromRun(run, listener).publish(details);
    }

    private static String detailsUrl(Run<?, ?> run, TestingBotBuildReportAction reportAction) {
        if (reportAction != null) {
            return reportAction.getReportUrl();
        }
        return DisplayURLProvider.get().getRunURL(run);
    }

    private static String defaultSummary(int total, int passed) {
        if (total == 0) {
            return "No TestingBot sessions were found in this build.";
        }
        return passed + " of " + total + " TestingBot session(s) passed.";
    }

    private static ChecksConclusion fromBuildResult(Result result) {
        if (result == null || result == Result.SUCCESS) {
            return ChecksConclusion.SUCCESS;
        }
        if (result == Result.FAILURE || result == Result.UNSTABLE) {
            return ChecksConclusion.FAILURE;
        }
        return ChecksConclusion.NEUTRAL;
    }

    @Extension
    @Symbol("testingbotChecks")
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Publish TestingBot results as a GitHub check";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
