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
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
/**
 *
 * @author testingbot.com
 */
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

            try {
               FileInputStream fstream = new FileInputStream(System.getProperty("user.home") + "/.testingbot");
               // Get the object of DataInputStream
               DataInputStream in = new DataInputStream(fstream);
               BufferedReader br = new BufferedReader(new InputStreamReader(in));
               String strLine = br.readLine();
               String[] data = strLine.split(":");
               apiKey = data[0];
               apiSecret = data[1];
             } catch (Exception e) { }
        
            listener.getLogger().println("Starting TestingBot Tunnel");
            final App app = new App();
            app.setClientKey(apiKey);
            app.setClientSecret(apiSecret);
            try {
                app.boot();
                Thread.sleep(60000);
            } catch (Exception ex) {
                Logger.getLogger(TestingBotBuildWrapper.class.getName()).log(Level.SEVERE, null, ex);
            }
            return new Environment() {

                @Override
                public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                    listener.getLogger().println("Closing TestingBot Tunnel");
                    app.stop();
                    return true;
                }
            };
        } else {
            return new Environment() {};
        }
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
}
