package testingbot;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestObject;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction.Data;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author testingbot.com
 */
public class TestReporter extends TestDataPublisher {
    
    @DataBoundConstructor
    public TestReporter() {
    }

    @Override
    public Data getTestData(AbstractBuild<?, ?> ab, Launcher lnchr, BuildListener bl, TestResult tr) throws IOException, InterruptedException {
        bl.getLogger().println("Scanning for test data...");
        boolean foundSession = false;
        List<String> sessionIDs = null;
        
        for (SuiteResult sr : tr.getSuites()) {
            for (CaseResult cr : sr.getCases()) {
                sessionIDs = TestingBotReportFactory.findSessionIDs(cr);
                if (!sessionIDs.isEmpty()) {
                    String errorDetails = cr.getErrorDetails();
                    if (errorDetails == null) {
                        errorDetails = "";
                    }
                    TestingBotAPI api = new TestingBotAPI();
                    Map<String, String> data = new HashMap<String, String>();
                    data.put("success", cr.isPassed() ? "1" : "0");
                    data.put("status_message", errorDetails);
                    data.put("name", cr.getFullName());
                    api.updateTest(sessionIDs.get(0), data);
            
                    foundSession = true;
                }
            }
        }
        
        if (!foundSession) {
            bl.getLogger().println("No TestingBot sessionIDs found in test output.");
            return null;
        } else {
            return TestingBotReportFactory.INSTANCE;
        }
    }
        
    @Extension
    public static class DescriptorImpl extends Descriptor<TestDataPublisher> {
        @Override
        public String getDisplayName() {
            return "Embed TestingBot reports";
        }
    }
        
}
