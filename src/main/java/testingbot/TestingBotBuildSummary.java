package testingbot;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.InvisibleAction;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.AbstractTestResultAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestingBotBuildSummary extends InvisibleAction {
    private final AbstractBuild<?,?> build;
    public List<TestingBotBuildObject> sessionIds = new ArrayList<TestingBotBuildObject>();
    private static final Map<Integer,List<String>> sessions = new HashMap<Integer,List<String>>();
    
    public TestingBotBuildSummary(AbstractBuild<?,?> build, List<TestingBotBuildObject> sessionIds) {
        this.build = build;
        this.sessionIds = sessionIds;
    }

    public List<TestingBotBuildObject> getSessionIds() {
        return this.sessionIds;
    }

    @Extension
    public static final class RunListenerImpl extends RunListener<AbstractBuild<?,?>> {

        @Override
        public void onCompleted(AbstractBuild<?, ?> r, TaskListener listener) {
            TestResult testResult = (TestResult) r.getAction(AbstractTestResultAction.class).getResult();
            List<TestingBotBuildObject> ids = new ArrayList<TestingBotBuildObject>();
            for (SuiteResult sr : testResult.getSuites()) {
                for (CaseResult cr : sr.getCases()) {
                    List<String> sessionIds = TestingBotReportFactory.findSessionIDs(cr);
                    TestingBotBuildObject tbo = new TestingBotBuildObject(sessionIds.get(0), cr.getClassName(), cr.getName(), cr.isPassed());
                    ids.add(tbo);
                }
            }
            Logger.getLogger(TestingBotBuildSummary.class.getName()).log(Level.SEVERE, null, "try to get ids");
            r.addAction(new TestingBotBuildSummary(r, ids));
        }
    }
}