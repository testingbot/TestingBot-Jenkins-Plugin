/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package testingbot;

/**
 *
 * @author jochen
 */
import hudson.model.AbstractBuild;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestAction;
import java.util.Collections;
import java.util.List;

/**
 * Show videos for the tests.
 *
 */
public class TestingBotReport extends TestAction {
    public final CaseResult parent;
    /**
     * Session IDs.
     */
    private final List<String> ids;

    public TestingBotReport(CaseResult parent, List<String> ids) {
        this.parent = parent;
        this.ids = ids;
    }

    public AbstractBuild<?, ?> getBuild() {
        return parent.getOwner();
    }

    public List<String> getIDs() {
        return Collections.unmodifiableList(ids);
    }

    public String getId() {
        return ids.get(0);
    }

    public String getIconFileName() {
        return "/plugin/testingbot/images/24x24/logo.jpg";
    }

    public String getDisplayName() {
        return "TestingBot Report";
    }

    public String getUrlName() {
        return "testingbot";
    }
}