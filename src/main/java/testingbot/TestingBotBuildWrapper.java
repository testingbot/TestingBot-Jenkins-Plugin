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
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.util.DescribableList;
import testingbot.pipeline.TestingBotTunnelStep;
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
    private boolean useTunnel;
    private String credentialsId;

    /**
     * Legacy name of {@link #useTunnel}. Retained (nullable) so job configs saved by older versions,
     * which persisted this flag as {@code <enableSSH>}, still load; migrated in {@link #readResolve()}.
     *
     * @deprecated use {@link #useTunnel}
     */
    @Deprecated
    private Boolean enableSSH;

    @DataBoundConstructor
    public TestingBotBuildWrapper(String credentialsId, boolean useTunnel) {
        this.credentialsId = credentialsId;
        this.useTunnel = useTunnel;
    }

    protected Object readResolve() {
        if (enableSSH != null) {
            useTunnel = enableSSH;
            enableSSH = null;
        }
        return this;
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

        // Expose the build id and add the embeddable build-report page so all sessions using
        // build=$TESTINGBOT_BUILD are grouped and viewable inside Jenkins.
        final String buildId = credentials != null ? TestingBotBuildReportAction.attach(build, credentials) : null;

        if (this.useTunnel) {
            if (credentials == null) {
                listener.getLogger().println("No TestingBot key/secret found while trying to start a TestingBot Tunnel");
                return new TestingBotBuildEnvironment(null, buildId, null, null, null);
            }

            // Run the tunnel on the build's node (not the controller) so that a test connecting to
            // localhost reaches the tunnel. The identifier keeps parallel builds on one node isolated.
            String tunnelIdentifier = TunnelManager.generateTunnelIdentifier(build.getParent().getFullName(), build.getNumber());
            String key = credentials.getKey();
            String secret = credentials.getDecryptedSecret();
            VirtualChannel channel = launcher.getChannel();

            if (channel != null) {
                try {
                    TunnelManager.startOnChannel(channel, key, secret, "", tunnelIdentifier, listener);
                } catch (InterruptedException ie) {
                    TunnelManager.stopOnChannel(channel, tunnelIdentifier, listener);
                    throw ie;
                } catch (Exception ex) {
                    // Do not continue the build with a broken tunnel: clean up and fail.
                    TunnelManager.stopOnChannel(channel, tunnelIdentifier, listener);
                    throw new IOException("Failed to start TestingBot tunnel", ex);
                }
                return new TestingBotBuildEnvironment(credentials, buildId, tunnelIdentifier, channel, null);
            }

            // No agent channel available (unusual launcher); fall back to a controller-local tunnel.
            final App app = new App();
            try {
                TunnelManager.start(app, key, secret, "", tunnelIdentifier, listener);
            } catch (InterruptedException ie) {
                TunnelManager.stop(app, listener);
                throw ie;
            } catch (Exception ex) {
                TunnelManager.stop(app, listener);
                throw new IOException("Failed to start TestingBot tunnel", ex);
            }
            return new TestingBotBuildEnvironment(credentials, buildId, tunnelIdentifier, null, app);
        }

        return new TestingBotBuildEnvironment(credentials, buildId, null, null, null);
    }

    /**
     * @return whether the TestingBot Tunnel should be started around the build
     */
    public boolean isUseTunnel() {
        return useTunnel;
    }

    /**
     * @param useTunnel whether the TestingBot Tunnel should be started around the build
     */
    public void setUseTunnel(boolean useTunnel) {
        this.useTunnel = useTunnel;
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
            if (context == null ? !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    : !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }

            return new StandardListBoxModel().withMatching(
                  CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(TestingBotCredentials.class)),
                  CredentialsProvider.lookupCredentialsInItem(TestingBotCredentials.class, context, ACL.SYSTEM2,
                      new ArrayList<>()));
        }
    }

    private class TestingBotBuildEnvironment extends BuildWrapper.Environment {

        private final TestingBotCredentials credentials;
        private final String buildId;
        /** Non-null when a tunnel was started for this build. */
        private final String tunnelIdentifier;
        /** Agent channel the tunnel runs on; null when the tunnel runs controller-locally (see {@link #localApp}). */
        private final VirtualChannel channel;
        /** Controller-local tunnel handle, used only in the no-channel fallback. */
        private final App localApp;

        public TestingBotBuildEnvironment(TestingBotCredentials credentials, @Nullable String buildId,
                @Nullable String tunnelIdentifier, @Nullable VirtualChannel channel, @Nullable App localApp) {
            this.credentials = credentials;
            this.buildId = buildId;
            this.tunnelIdentifier = tunnelIdentifier;
            this.channel = channel;
            this.localApp = localApp;
        }

        @Override
        public void buildEnvVars(Map<String, String> env) {
            if (credentials != null) {
                TunnelManager.populateCredentialEnv(env, credentials);
            }

            if (buildId != null) {
                env.put(TestingBotBuildReportAction.TESTINGBOT_BUILD, buildId);
            }

            if (tunnelIdentifier != null) {
                env.put(TESTINGBOT_TUNNEL, "true");
                env.put(TestingBotTunnelStep.TESTINGBOT_TUNNEL_IDENTIFIER, tunnelIdentifier);
            }

            super.buildEnvVars(env);
        }

        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
            if (tunnelIdentifier != null) {
                if (channel != null) {
                    TunnelManager.stopOnChannel(channel, tunnelIdentifier, listener);
                } else {
                    TunnelManager.stop(localApp, listener);
                }
            }
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
