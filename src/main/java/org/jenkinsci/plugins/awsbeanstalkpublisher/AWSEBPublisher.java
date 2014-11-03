package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import hudson.Extension;
import hudson.Launcher;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.BuildListener;
import hudson.model.Items;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationDescription;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.google.common.base.Joiner;

/**
 * AWS Elastic Beanstalk Deployment
 */
public class AWSEBPublisher extends Recorder implements AWSEBProvider {
    
    @Initializer(before=InitMilestone.PLUGINS_STARTED)
    public static void addAlias() {
        Items.XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBDeploymentPublisher", AWSEBPublisher.class);
    }
    
    @DataBoundConstructor
    public AWSEBPublisher(Regions awsRegion, 
            String applicationName, String environmentList, String bucketName, 
            String keyPrefix, String versionLabelFormat, String rootObject,
            String includes, String excludes, String credentials, Boolean overwriteExistingFile, Boolean failOnError) {
        super();
        this.awsRegion = awsRegion;
        this.applicationName = applicationName;
        this.environments = new ArrayList<String>();
        for (String next : environmentList.split("\n")) {
            this.environments.add(next);
        }
        this.bucketName = bucketName;
        this.keyPrefix = keyPrefix;
        this.versionLabelFormat = versionLabelFormat;
        this.rootObject = rootObject;
        this.includes = includes;
        this.excludes = excludes;
        this.credentials = AWSEBCredentials.getCredentialsByString(credentials);
        this.overwriteExistingFile = overwriteExistingFile;
        this.failOnError = failOnError;
    }
    
    
    private final Boolean failOnError;
    
    public boolean getFailOnError() {
        return failOnError == null ? false : failOnError;
    }

    /**
     * AWS Region
     */
    private final Regions awsRegion;

    public Regions getAwsRegion() {
        return awsRegion;
    }

    /**
     * Application Name
     */
    private final String applicationName;

    public String getApplicationName() {
        return applicationName;
    }

    /**
     * Environment Name
     */
    private final List<String> environments;

    public List<String> getEnvironments() {
        return environments;
    }

    public String getEnvironmentList() {
        return Joiner.on("\n").join(environments);
    }

    /**
     * Bucket Name
     */
    private final String bucketName;

    public String getBucketName() {
        return bucketName;
    }

    /**
     * Key Format
     */
    private final String keyPrefix;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    private final String versionLabelFormat;

    public String getVersionLabelFormat() {
        return versionLabelFormat;
    }

    private final String rootObject;

    public String getRootObject() {
        return rootObject;
    }

    private final String includes;

    public String getIncludes() {
        return includes;
    }

    private final String excludes;

    public String getExcludes() {
        return excludes;
    }

    private final Boolean overwriteExistingFile;

    @Override
    public boolean isOverwriteExistingFile() {
        return (overwriteExistingFile == null ? false : overwriteExistingFile);
    }

    /**
     * Credentials Name from the global config
     */
    private final AWSEBCredentials credentials;

    public AWSEBCredentials getCredentials() {
        return credentials;
    }
    

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        if (build.getResult().isWorseThan(Result.SUCCESS)) {
            return true;
        }
        try {
            AWSEBDeployer deployer = new AWSEBDeployer(this, build, launcher, listener);

            deployer.perform();

            return true;
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link HelloWorldBuilder}. Used as a singleton. The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt> for the actual HTML fragment for the configuration screen.
     */
    @Extension
    // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private Set<AWSEBCredentials> credentials;
        private final Regions awsRegion = Regions.DEFAULT_REGION;
        
        public Regions getAwsRegion() {
            return awsRegion;
        }

        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Deploy to AWS Elastic Beanstalk";
        }

        public DescriptorImpl() {
            load();
            if (credentials != null) {
                AWSEBCredentials.configureCredentials(credentials);
            } else if (AWSEBCredentials.getCredentials() != null) {
                credentials = AWSEBCredentials.getCredentials();
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            AWSEBCredentials.configureCredentials(req.bindParametersToList(AWSEBCredentials.class, "credential."));
            credentials = AWSEBCredentials.getCredentials();
            save();
            return super.configure(req, json);
        }
        
        public Set<AWSEBCredentials> getCredentials() {
            return credentials;
        }
        
        public ListBoxModel doFillCredentialsItems(@QueryParameter String credentials) {
            ListBoxModel items = new ListBoxModel();
            for (AWSEBCredentials creds : AWSEBCredentials.getCredentials()) {

                items.add(creds, creds.toString());
                if (creds.toString().equals(credentials)) {
                    items.get(items.size()-1).selected = true;
                } 
            }

            return items;
        }

        public FormValidation doLoadApplications(@QueryParameter("credentials") String credentialsString, @QueryParameter("awsRegion") String regionString) {
            AWSEBCredentials credentials = AWSEBCredentials.getCredentialsByString(credentialsString);
            if (credentials == null) {
                return FormValidation.error("Missing valid credentials");
            }
            Regions region = Enum.valueOf(Regions.class, regionString);
            if (region == null) {
                return FormValidation.error("Missing valid Region");
            }
            
            List<ApplicationDescription> apps = AWSEBDeployer.getApplications(credentials.getAwsCredentials(), region);
            
            
            StringBuilder sb = new StringBuilder();
            for (ApplicationDescription app : apps) {
                sb.append(app.getApplicationName());
                sb.append("\n");
            }
            return FormValidation.ok(sb.toString());
        }
        
        public FormValidation doLoadApplicationsGlobal(@QueryParameter("credential.awsAccessKeyId") String accessKey, @QueryParameter("credential.awsSecretSharedKey") String secretKey, @QueryParameter("awsRegion") String regionString) {
            if (accessKey == null || secretKey == null) {
                return FormValidation.error("Access key and Secret key cannot be empty");
            }
            AWSEBCredentials credentials = new AWSEBCredentials("", accessKey, secretKey);
            Regions region = Enum.valueOf(Regions.class, regionString);
            if (region == null) {
                return FormValidation.error("Missing valid Region");
            }
            
            List<ApplicationDescription> apps = AWSEBDeployer.getApplications(credentials.getAwsCredentials(), region);
            
            
            StringBuilder sb = new StringBuilder();
            for (ApplicationDescription app : apps) {
                sb.append(app.getApplicationName());
                sb.append("\n");
            }
            return FormValidation.ok(sb.toString());
        }
        
        public FormValidation doLoadEnvironments(@QueryParameter("credentials") String credentialsString, @QueryParameter("awsRegion") String regionString, @QueryParameter("applicationName") String appName) {
            AWSEBCredentials credentials = AWSEBCredentials.getCredentialsByString(credentialsString);
            if (credentials == null) {
                return FormValidation.error("Missing valid credentials");
            }
            Regions region = Enum.valueOf(Regions.class, regionString);
            if (region == null) {
                return FormValidation.error("Missing valid Region");
            }
            
            if (appName == null) {
                return FormValidation.error("Missing an application name");
            }
            
            List<EnvironmentDescription> environments = AWSEBDeployer.getEnvironments(credentials.getAwsCredentials(), region, appName);
            StringBuilder sb = new StringBuilder();
            for (EnvironmentDescription env : environments) {
                sb.append(env.getEnvironmentName());
                sb.append("\n");
            }
            return FormValidation.ok(sb.toString());
        }

    }

}
