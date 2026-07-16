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
        TestingBotBuildObject selected = null;
        for (TestingBotBuildObject candidate : this.ids) {
            if (candidate.getSessionId().equals(sessionId)) {
                selected = candidate;
                break;
            }
        }
        // Pass the selection through the request (this action is shared across concurrent requests,
        // so a per-instance field would let requests overwrite each other's selected session).
        req.setAttribute("tbo", selected);
        try {
            req.getView(this, selected != null ? "show.jelly" : "index.jelly").forward(req, rsp);
        } catch (ServletException e) {
            throw new IOException(e);
        }
    }

    public Run getRun() {
        return run;
    }

    public List<TestingBotBuildObject> getSessionIds() {
        return ids;
    }
}
