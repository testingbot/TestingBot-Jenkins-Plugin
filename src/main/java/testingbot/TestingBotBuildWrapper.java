package testingbot;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.BuildWrapper;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;
import com.testingbot.tunnel.App;
import java.util.Map;

public final class TestingBotBuildWrapper extends BuildWrapper {
    
    private boolean enableSSH;
    
    @DataBoundConstructor
    public TestingBotBuildWrapper(boolean enableSSH) {
        this.enableSSH = enableSSH;
    }
       
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        if (this.enableSSH == true) {
            String apiKey = null;
            String apiSecret = null;
            TestingBotCredential credentials = TestingBotCredentials.getCredentials();

            final App app = new App();
            try {
                if (credentials != null) {
                    apiKey = credentials.getKey();
                    apiSecret = credentials.getSecret();
                }
             } catch (Exception e) { }
            
            if (apiKey == null) {
                listener.getLogger().println("No TestingBot key/secret found while trying to start a TestingBot Tunnel");
                return new TestingBotBuildEnvironment(app);
            }
        
            listener.getLogger().println("Starting TestingBot Tunnel");
            app.setClientKey(apiKey);
            app.setClientSecret(apiSecret);
            try {
                app.boot();
                Thread.sleep(60000);
            } catch (Exception ex) {
                Logger.getLogger(TestingBotBuildWrapper.class.getName()).log(Level.SEVERE, null, ex);
            }
            return new TestingBotBuildEnvironment(app);
        }
        
        return new TestingBotBuildEnvironment(null);
    }

    /**
     * @return the enableSSH
     */
    public boolean isEnableSSH() {
        return enableSSH;
    }

    /**
     * @param enableSSH the enableSSH to set
     */
    public void setEnableSSH(boolean enableSSH) {
        this.enableSSH = enableSSH;
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<BuildWrapper> {
        @Override
        public String getDisplayName() {
            return "TestingBot Tunnel";
        }
    }
    
    private interface EnvVars {
        String TESTINGBOT_KEY = "TESTINGBOT_KEY";
        String TB_KEY = "TB_KEY";
        String TESTINGBOT_SECRET = "TESTINGBOT_SECRET";
        String TB_SECRET = "TB_SECRET";
        String TESTINGBOT_TUNNEL = "TESTINGBOT_TUNNEL";
    }
    
    private class TestingBotBuildEnvironment extends BuildWrapper.Environment {
        private final App app;
        
        public TestingBotBuildEnvironment(App app) {
            this.app = app;
        }
        
        @Override
        public void buildEnvVars(Map<String, String> env) {
            TestingBotCredential credentials = TestingBotCredentials.getCredentials();
            if (credentials != null) {
                env.put(EnvVars.TESTINGBOT_KEY, credentials.getKey());
                env.put(EnvVars.TB_KEY, credentials.getKey());
                env.put(EnvVars.TESTINGBOT_SECRET, credentials.getSecret());
                env.put(EnvVars.TB_SECRET, credentials.getSecret());
            }
            
            if (app != null) {
                env.put(EnvVars.TESTINGBOT_TUNNEL, app != null ? "true" : "false");
            }

            super.buildEnvVars(env);
        }
        
        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
            if (app != null) {
                listener.getLogger().println("Closing TestingBot Tunnel");
                app.stop();
            }
            return true;
        }
    }

}
