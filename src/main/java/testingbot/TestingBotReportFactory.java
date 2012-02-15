/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package testingbot;

import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.TestObject;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction.Data;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author jochen
 */
public class TestingBotReportFactory extends Data {
    
    public static final TestingBotReportFactory INSTANCE = new TestingBotReportFactory();
    
    public Object readResolve() {
        return INSTANCE;
    }
    
    public static List<String> findSessionIDs(CaseResult testResult) {
        List<String> sessions = new ArrayList<String>();
        Pattern p = Pattern.compile("TestingBotSessionID=([0-9a-zA-Z]+)");
        Matcher matchOut = p.matcher(testResult.getStdout());
        Matcher matchError = p.matcher(testResult.getStderr());
        
        while (matchOut.find()) {
            String sessionId = matchOut.group(1);
            sessions.add(sessionId);
        }
        
        while (matchError.find()) {
            String sessionId = matchError.group(1);
            sessions.add(sessionId);
        }
        
        return sessions;
    }

    @Override
    public List<? extends TestAction> getTestAction(TestObject to) {
        if (to instanceof CaseResult) {
            CaseResult cr = (CaseResult) to;
            List<String> ids = findSessionIDs(cr);
            if (!ids.isEmpty()) {
                return Collections.singletonList(new TestingBotReport(cr,ids));
            }
        }
        return Collections.emptyList();
    }
    
}
