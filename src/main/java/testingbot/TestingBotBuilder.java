package testingbot;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import java.io.*;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * @author testingbot.com
 */
public class TestingBotBuilder extends Builder {

    private final String name;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public TestingBotBuilder(String name) {
        this.name = name;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getName() {
        return name;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        return true;
    }
    
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        
        private String apiKey;
        private String apiSecret;
        
        public String getApiKey() {
            return apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }
        
        public DescriptorImpl() {
            super();
            try {
              FileInputStream fstream = new FileInputStream(System.getProperty("user.home") + "/.testingbot");
              // Get the object of DataInputStream
              DataInputStream in = new DataInputStream(fstream);
              BufferedReader br = new BufferedReader(new InputStreamReader(in));
              String strLine = br.readLine();
              String[] data = strLine.split(":");
              this.apiKey = data[0];
              this.apiSecret = data[1];
            } catch (Exception e) {}
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "TestingBot";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            this.apiKey = formData.getString("apiKey");
            this.apiSecret = formData.getString("apiSecret");
            // ^Can also use req.bindJSON(this, formData);
            // save in ~/.testingbot
            
            try {
                FileWriter fstream = new FileWriter(System.getProperty("user.home") + "/.testingbot");
                BufferedWriter out = new BufferedWriter(fstream);
                out.write(this.apiKey + ":" + this.apiSecret);
                out.close();
            } catch (Exception e){}
            save();
            return super.configure(req,formData);
        }
    }
}

