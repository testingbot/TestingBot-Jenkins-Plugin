/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
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
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author jochen
 */
public class TestReporter extends TestDataPublisher {
    
    @DataBoundConstructor
    public TestReporter() {
    }

    @Override
    public Data getTestData(AbstractBuild<?, ?> ab, Launcher lnchr, BuildListener bl, TestResult tr) throws IOException, InterruptedException {
        bl.getLogger().println("Scanning for test data...");
        boolean foundSession = false;
        
        for (SuiteResult sr : tr.getSuites()) {
            for (CaseResult cr : sr.getCases()) {
                List<String> sessionIDs = TestingBotReportFactory.findSessionIDs(cr);
                if (!sessionIDs.isEmpty()) {
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
