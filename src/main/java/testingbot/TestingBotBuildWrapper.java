package testingbot;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.BuildWrapper;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.AncestorInPath;
import com.testingbot.tunnel.App;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Job;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import hudson.security.ACL;
import hudson.util.DescribableList;
import java.util.Map;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.verb.POST;

import edu.umd.cs.findbugs.annotations.Nullable;

public final class TestingBotBuildWrapper extends BuildWrapper {

    public static final String TESTINGBOT_KEY = "TESTINGBOT_KEY";
    public static final String TB_KEY = "TB_KEY";
    public static final String TESTINGBOT_SECRET = "TESTINGBOT_SECRET";
    public static final String TB_SECRET = "TB_SECRET";
    public static final String TESTINGBOT_TUNNEL = "TESTINGBOT_TUNNEL";
    private boolean enableSSH;
    private String credentialsId;

    @DataBoundConstructor
    public TestingBotBuildWrapper(String credentialsId, boolean enableSSH) {
        this.credentialsId = credentialsId;
        this.enableSSH = enableSSH;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final TestingBotCredentials credentials = TestingBotCredentials.getCredentials(build.getProject(), credentialsId);

        TestingBotBuildAction action = build.getAction(TestingBotBuildAction.class);
        if (action == null) {
          action = new TestingBotBuildAction(credentials);
          build.addAction(action);
        }

        if (this.enableSSH) {
            if (credentials == null) {
                listener.getLogger().println("No TestingBot key/secret found while trying to start a TestingBot Tunnel");
                return new TestingBotBuildEnvironment(null, null);
            }

            final App app = new App();
            try {
                TunnelManager.start(app, credentials.getKey(), credentials.getDecryptedSecret(), "", null, listener);
            } catch (Exception ex) {
                Logger.getLogger(TestingBotBuildWrapper.class.getName()).log(Level.SEVERE, "Failed to start TestingBot tunnel", ex);
            }
            return new TestingBotBuildEnvironment(credentials, app);
        }

        return new TestingBotBuildEnvironment(credentials, null);
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
    @Symbol("testingbot")
    public static class DescriptorImpl extends Descriptor<BuildWrapper> {

        @Override
        public String getDisplayName() {
            return "TestingBot";
        }

        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath final Item context) {
            if (context != null && !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }

            return new StandardListBoxModel().withMatching(
                  CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(TestingBotCredentials.class)),
                  CredentialsProvider.lookupCredentialsInItem(TestingBotCredentials.class, context, ACL.SYSTEM2,
                      new ArrayList<>()));
        }
    }

    private class TestingBotBuildEnvironment extends BuildWrapper.Environment {

        private final App app;
        private final TestingBotCredentials credentials;

        public TestingBotBuildEnvironment(TestingBotCredentials credentials, @Nullable App app) {
            this.credentials = credentials;
            this.app = app;
        }

        @Override
        public void buildEnvVars(Map<String, String> env) {
            if (credentials != null) {
                TunnelManager.populateCredentialEnv(env, credentials);
            }

            if (app != null) {
                env.put(TESTINGBOT_TUNNEL,"true");
            }

            super.buildEnvVars(env);
        }

        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
            TunnelManager.stop(app, listener);
            return true;
        }
    }
    
    protected boolean migrateCredentials(AbstractProject project) {
        Logger.getLogger(TestingBotBuildWrapper.class.getName()).log(Level.INFO, "TestingBot Plugin: migrateCredentials: " + this.credentialsId);
            
        if (Util.fixEmpty(this.credentialsId) == null) {
            try {
                TestingBotCredentials.migrate();
                return true;
            } catch (Exception e) {
                Logger.getLogger(TestingBotBuildWrapper.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        
            }
        }
        return false;
    }

    static BuildWrapperItem<TestingBotBuildWrapper> findBuildWrapper(
            final Job<?, ?> job) {
        return findItemWithBuildWrapper(job, TestingBotBuildWrapper.class);
    }

    private static <T extends BuildWrapper> BuildWrapperItem<T> findItemWithBuildWrapper(
            final AbstractItem buildItem, Class<T> buildWrapperClass) {
        if (buildItem == null) {
            return null;
        }

        if (buildItem instanceof BuildableItemWithBuildWrappers) {
            BuildableItemWithBuildWrappers buildWrapper = (BuildableItemWithBuildWrappers) buildItem;
            DescribableList<BuildWrapper, Descriptor<BuildWrapper>> buildWrappersList
                    = buildWrapper.getBuildWrappersList();

            if (buildWrappersList != null && !buildWrappersList.isEmpty()) {
                return new BuildWrapperItem<T>(buildWrappersList.get(buildWrapperClass), buildItem);
            }
        }

        if (buildItem.getParent() instanceof AbstractItem) {
            return findItemWithBuildWrapper((AbstractItem) buildItem.getParent(), buildWrapperClass);
        }

        return null;
    }

    static class BuildWrapperItem<T> {

        final T buildWrapper;
        final AbstractItem buildItem;

        BuildWrapperItem(T buildWrapper, AbstractItem buildItem) {
            this.buildWrapper = buildWrapper;
            this.buildItem = buildItem;
        }
    }
    
    @Extension
    static final public class ItemListenerImpl extends ItemListener {
        public void onLoaded() {
            Jenkins instance = Jenkins.getInstanceOrNull();
            if (instance == null) { return; }
            for (BuildableItemWithBuildWrappers item : instance.getItems(BuildableItemWithBuildWrappers.class))
            {
                AbstractProject p = item.asProject();
                for (TestingBotBuildWrapper bw : ((BuildableItemWithBuildWrappers)p).getBuildWrappersList().getAll(TestingBotBuildWrapper.class))
                {
                    if (bw.migrateCredentials(p)) {
                        try {
                            p.save();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
}
