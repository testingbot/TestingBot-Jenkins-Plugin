package testingbot;

public class TestingBotBuildObject {

    private String sessionId;
    private String className;
    private String testName;
    private boolean isPassed;

    public TestingBotBuildObject(String sessionId, String className, String testName, boolean isPassed) {
      this.sessionId = sessionId;
      this.className = className;
      this.testName = testName;
      this.isPassed = isPassed;
    }

    /**
     * @return the sessionId
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * @param sessionId the sessionId to set
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * @return the className
     */
    public String getClassName() {
        return className;
    }

    /**
     * @param className the className to set
     */
    public void setClassName(String className) {
        this.className = className;
    }

    /**
     * @return the testName
     */
    public String getTestName() {
        return testName;
    }

    /**
     * @param testName the testName to set
     */
    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getIsPassed() {
        return isPassed ? "Yes" : "No";
    }
}