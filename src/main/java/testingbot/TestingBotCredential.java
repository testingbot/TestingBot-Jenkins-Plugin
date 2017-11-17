package testingbot;

public class TestingBotCredential {
    private String key;
    private String secret;
    
    public TestingBotCredential(String k, String s) {
        this.key = k;
        this.secret = s;
    }
    
    /**
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * @param key the key to set
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * @return the secret
     */
    public String getSecret() {
        return secret;
    }

    /**
     * @param secret the secret to set
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }
}
