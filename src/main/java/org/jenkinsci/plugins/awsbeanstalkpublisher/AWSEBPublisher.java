package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.BuildListener;
import hudson.model.Items;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBElasticBeanstalkSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetupDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationDescription;

/**
 * AWS Elastic Beanstalk Deployment
 */
public class AWSEBPublisher extends AWSEBPublisherBackwardsCompatibility {
    
    @Initializer(before=InitMilestone.PLUGINS_STARTED)
    public static void addAlias() {
        Items.XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBDeploymentPublisher", AWSEBPublisher.class);
    }
    
    @DataBoundConstructor
    public AWSEBPublisher(
            List<AWSEBElasticBeanstalkSetup> extensions) {
        super();
        this.extensions = new DescribableList<AWSEBSetup, AWSEBSetupDescriptor>(
                Saveable.NOOP,Util.fixNull(extensions));
    }
    
    private DescribableList<AWSEBSetup, AWSEBSetupDescriptor> extensions;
    
    public DescribableList<AWSEBSetup, AWSEBSetupDescriptor> getExtensions() {
        if (extensions == null) {
            extensions = new DescribableList<AWSEBSetup, AWSEBSetupDescriptor>(Saveable.NOOP,Util.fixNull(extensions));
        }
        return extensions;
    }
    

    public Object readResolve() {
        readBackExtensionsFromLegacy();
        return this;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        if (build.getResult().isWorseThan(Result.SUCCESS)) {
            return true;
        }
        try {

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
            return "Deploy into AWS Elastic Beanstalk";
        }

        
        public List<AWSEBSetupDescriptor> getExtensionDescriptors() {
            List<AWSEBSetupDescriptor> extensions = new ArrayList<AWSEBSetupDescriptor>(1);
            extensions.add(AWSEBElasticBeanstalkSetup.getDesc());
            return extensions;
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
        
        public FormValidation doLoadApplicationsGlobal(@QueryParameter("credential.awsAccessKeyId") String accessKey, @QueryParameter("credential.awsSecretSharedKey") String secretKey, @QueryParameter("awsRegion") String regionString) {
            if (accessKey == null || secretKey == null) {
                return FormValidation.error("Access key and Secret key cannot be empty");
            }
            AWSEBCredentials credentials = new AWSEBCredentials("", accessKey, secretKey);
            Regions region = Enum.valueOf(Regions.class, regionString);
            if (region == null) {
                return FormValidation.error("Missing valid Region");
            }
            
            List<ApplicationDescription> apps = AWSEBUtils.getApplications(credentials.getAwsCredentials(), region);
            
            
            StringBuilder sb = new StringBuilder();
            for (ApplicationDescription app : apps) {
                sb.append(app.getApplicationName());
                sb.append("\n");
            }
            return FormValidation.ok(sb.toString());
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
            
            return FormValidation.ok(AWSEBUtils.getApplicationListAsString(credentials, region));
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
            
           
            return FormValidation.ok(AWSEBUtils.getEnvironmentsListAsString(credentials, region, appName));
        }

    }

}
