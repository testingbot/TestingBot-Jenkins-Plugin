package testingbot;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author testingbot.com
 */
public final class TestingBotBuildWrapper extends BuildWrapper {
    
    private boolean enableSSH;
    private String sshCommand;
    
    @DataBoundConstructor
    public TestingBotBuildWrapper(boolean enableSSH, String sshCommand) {
        this.enableSSH = enableSSH;
        this.sshCommand = sshCommand;
    }
       
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        if (this.enableSSH == true) {
            listener.getLogger().println("Starting TestingBot SSH Tunnel");
            final Process p = Runtime.getRuntime().exec(this.sshCommand);
            return new Environment() {

                @Override
                public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                    listener.getLogger().println("Closing SSH Tunnel");
                    p.destroy();
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

    /**
     * @return the sshCommand
     */
    public String getSshCommand() {
        return sshCommand;
    }

    /**
     * @param ssh the sshCommand to set
     */
    public void setSshCommand(String sshComand) {
        this.sshCommand = sshComand;
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<BuildWrapper> {
        @Override
        public String getDisplayName() {
            return "TestingBot SSH";
        }
    }
}
