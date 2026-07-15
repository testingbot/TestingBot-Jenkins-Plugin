package testingbot;

import hudson.model.InvisibleAction;

public class TestingBotBuildAction extends InvisibleAction {

    private TestingBotCredentials testingbotCredential;

    public TestingBotBuildAction(TestingBotCredentials testingbotCredentials) {
        this.testingbotCredential = testingbotCredentials;
    }

    public TestingBotCredentials getCredentials() {
        return testingbotCredential;
    }

    public void setCredentials(TestingBotCredentials testingbotCredential) {
        this.testingbotCredential = testingbotCredential;
    }
}
