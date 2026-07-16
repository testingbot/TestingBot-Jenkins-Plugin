package testingbot;

import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestObject;
import hudson.tasks.junit.TestResultAction.Data;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author testingbot.com
 */
public class TestingBotReportFactory extends Data {
    private static final Logger logger = Logger.getLogger(TestingBotReportFactory.class.getName());

    TestingBotCredentials credentials;
    public TestingBotReportFactory(TestingBotCredentials credentials) {
        super();
        this.credentials = credentials;
    }
    
    private static final Pattern SESSION_PATTERN = Pattern.compile("TestingBotSessionID=([A-Za-z0-9]+)");

    public static List<String> findSessionIDs(CaseResult testResult) {
        List<String> sessions = new ArrayList<>();
        collectSessionIDs(testResult.getStdout(), sessions);
        collectSessionIDs(testResult.getStderr(), sessions);
        collectSessionIDs(testResult.getClassName(), sessions);
        collectSessionIDs(testResult.getFullName(), sessions);
        return sessions;
    }

    static void collectSessionIDs(String text, List<String> sessions) {
        if (text == null) {
            return;
        }
        Matcher matcher = SESSION_PATTERN.matcher(text);
        while (matcher.find()) {
            sessions.add(matcher.group(1));
        }
    }

    @Override
    public List<TestingBotReport> getTestAction(TestObject to) {
        if (to instanceof CaseResult) {
            CaseResult cr = (CaseResult) to;
            List<String> ids = findSessionIDs(cr);
            if (!ids.isEmpty()) {
                return Collections.singletonList(new TestingBotReport(credentials, cr, ids));
            }
        }
        // Fires for the majority of test-tree nodes (any case without a TestingBotSessionID); keep it quiet.
        logger.log(Level.FINE, "No TestingBot sessions for this test object");
        return Collections.emptyList();
    }
    
}
