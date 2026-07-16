package testingbot;

import hudson.model.Run;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;

public class TestingBotTestEmbed implements RunAction2 {
    private transient Run run;
    private List<TestingBotBuildObject> ids;
    private TestingBotBuildObject tbo;

    public TestingBotTestEmbed(List<TestingBotBuildObject> ids) {
        this.ids = ids;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/testingbot/images/logo.svg";
    }

    @Override
    public String getDisplayName() {
        return "TestingBot";
    }

    @Override
    public String getUrlName() {
        return "testingbot-embed";
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    /**
     * @param req Standard Request Object
     * @param rsp Standard Response Object
     * @throws IOException Unable to load the view template
     */
    @SuppressWarnings("unused") // used by stapler
    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp)
            throws IOException {
        String sessionId = req.getParameter("sessionId");
        // Reset the selection each request so a subsequent unmatched request does not render a
        // previously-selected session's media (this action is shared across requests for the run).
        TestingBotBuildObject selected = null;
        for (TestingBotBuildObject candidate : this.ids) {
            if (candidate.getSessionId().equals(sessionId)) {
                selected = candidate;
                break;
            }
        }
        this.tbo = selected;
        try {
            req.getView(this, selected != null ? "show.jelly" : "index.jelly").forward(req, rsp);
        } catch (ServletException e) {
            throw new IOException(e);
        }
    }

    public Run getRun() {
        return run;
    }

    public TestingBotBuildObject getTbo() {
        return tbo;
    }

    public List<TestingBotBuildObject> getSessionIds() {
        return ids;
    }
}
