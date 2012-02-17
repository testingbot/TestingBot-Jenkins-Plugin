/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
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
 * @author jochen
 */
public final class TestingBotBuildWrapper extends BuildWrapper {
    
    private boolean enableSSH;
    private String ssh;
    
    @DataBoundConstructor
    public TestingBotBuildWrapper(boolean enableSSH, String ssh) {
        this.enableSSH = enableSSH;
        this.ssh = ssh;
    }
       
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final PrintStream logger = listener.getLogger();
        logger.println("Setting up SSH stuff");
        listener.getLogger().println("Starting TestingBot SSH Tunnel");
        
        return new Environment() {
           
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                return true;
            }
        };
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
     * @return the ssh
     */
    public String getSsh() {
        return ssh;
    }

    /**
     * @param ssh the ssh to set
     */
    public void setSsh(String ssh) {
        this.ssh = ssh;
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<BuildWrapper> {
        @Override
        public String getDisplayName() {
            return "TestingBot SSH";
        }
    }
}
