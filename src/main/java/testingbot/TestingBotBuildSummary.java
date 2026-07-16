package testingbot;

import com.testingbot.models.TestingbotTest;
import com.testingbot.testingbotrest.TestingbotREST;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.InvisibleAction;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.AbstractTestResultAction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestingBotBuildSummary extends InvisibleAction implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<TestingBotBuildObject> sessionIds;

    public TestingBotBuildSummary(AbstractBuild<?,?> build, List<TestingBotBuildObject> sessionIds) {
        this.sessionIds = sessionIds;
    }

    @Override
    public String getUrlName() {
        return "testingbot";
    }

    public List<TestingBotBuildObject> getSessionIds() {
        return this.sessionIds;
    }

    @Extension
    public static final class RunListenerImpl extends RunListener<AbstractBuild<?,?>> {

        @Override
        public void onCompleted(AbstractBuild<?, ?> r, TaskListener listener) {
            if (r == null) {
                return;
            }

            // Only act on builds that are actually configured to use TestingBot.
            TestingBotBuildAction buildAction = r.getAction(TestingBotBuildAction.class);
            TestingBotCredentials credentials = null;
            if (buildAction != null) {
                credentials = buildAction.getCredentials();
            } else {
                TestingBotBuildWrapper.BuildWrapperItem<TestingBotBuildWrapper> wrapperItem =
                        TestingBotBuildWrapper.findBuildWrapper(r.getParent());
                if (wrapperItem == null || wrapperItem.buildWrapper == null) {
                    return;
                }
                credentials = TestingBotCredentials.getCredentials(wrapperItem.buildItem,
                        wrapperItem.buildWrapper.getCredentialsId());
            }
            if (credentials == null) {
                return;
            }

            AbstractTestResultAction<?> testResultAction = r.getAction(AbstractTestResultAction.class);
            if (testResultAction == null) {
                return;
            }
            Object result = testResultAction.getResult();
            if (!(result instanceof TestResult)) {
                return;
            }
            TestResult testResult = (TestResult) result;

            TestingbotREST apiClient = new TestingbotREST(credentials.getKey(), credentials.getDecryptedSecret());
            List<TestingBotBuildObject> ids = new ArrayList<>();
            for (SuiteResult sr : testResult.getSuites()) {
                for (CaseResult cr : sr.getCases()) {
                    List<String> sessionIds = TestingBotReportFactory.findSessionIDs(cr);
                    if (sessionIds.isEmpty()) {
                        continue;
                    }
                    String sessionId = sessionIds.get(0);
                    try {
                        TestingbotTest test = apiClient.getTest(sessionId);
                        TestingBotBuildObject tbo = new TestingBotBuildObject(sessionId, cr.getClassName(), cr.getName(), cr.isPassed(), apiClient.getAuthenticationHash(sessionId), test);
                        ids.add(tbo);
                    } catch (RuntimeException e) {
                        // A single bad session id (typo, wrong account, expired/purged session) must not
                        // drop the report for the other valid sessions in this build. Log at WARNING so
                        // the failed lookup is visible (unlike the normal "no session" case).
                        Logger.getLogger(TestingBotBuildSummary.class.getName())
                                .log(Level.WARNING, "Skipping TestingBot session " + sessionId, e);
                    }
                }
            }
            if (ids.isEmpty()) {
                return;
            }
            r.addAction(new TestingBotBuildSummary(r, ids));
            r.addAction(new TestingBotTestEmbed(ids));
        }
    }
}