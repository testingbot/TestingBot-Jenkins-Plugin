package testingbot;

import com.testingbot.models.TestingbotTest;

import java.io.Serializable;
import java.util.List;

public class TestingBotBuildObject implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private String className;
    private String testName;
    private final boolean isPassed;
    private String authHash;
    private String environmentName;
    private String thumbUrl;

    public TestingBotBuildObject(String sessionId, String className, String testName, boolean isPassed, String authHash, TestingbotTest test) {
      this.sessionId = sessionId;
      this.className = className;
      this.testName = testName;
      this.isPassed = isPassed;
      this.authHash = authHash;
      // TestingbotTest is only used here to derive display fields; it is not stored (it is not
      // marshalable under the JEP-200 class filter, and nothing reads it afterwards). Only the derived
      // Strings are kept, which marshal cleanly.
      if (test == null) {
        this.environmentName = "";
        this.thumbUrl = "";
      } else {
        this.environmentName = test.getBrowser() + " | " + test.getOs();
        List<String> thumbs = test.getThumbs();
        this.thumbUrl = (test.isAssetsAvailable() && thumbs != null && !thumbs.isEmpty()) ? thumbs.get(0) : "";
      }
    }

    /**
     * @return a preview thumbnail URL for the session, or an empty string when no assets are available.
     */
    public String getThumbUrl() {
        return thumbUrl;
    }

    /**
     * @return the environmentName
     */
    public String getEnvironmentName() {
        return environmentName;
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

    
    /**
     * @return the isPassed
     */
    public boolean getIsPassed() {
        return isPassed;
    }

    /**
     * @return the authHash
     */
    public String getAuthHash() {
        return authHash;
    }

    /**
     * @param authHash the authHash to set
     */
    public void setAuthHash(String authHash) {
        this.authHash = authHash;
    }
}