package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBElasticBeanstalkSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetupDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;

/**
 * AWS Elastic Beanstalk Deployment
 */
public class AWSEBPublisher extends AWSEBPublisherBackwardsCompatibility {
    private static final Logger logger = Logger.getLogger(AWSEBPublisher.class.getName());
    
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
            return false;
        }
        return AWSEBSetup.perform(build, launcher, listener, getExtensions());
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

        private transient Set<AWSEBCredentials> credentials;

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
                CredentialsStore provider = new SystemCredentialsProvider.StoreImpl();
                List<DomainSpecification> specifications = new ArrayList<DomainSpecification>(1);
                Domain domain = new Domain("aws.amazon.com", "Auto generated credentials domain", specifications);
                
                for (AWSEBCredentials creds : credentials) {
                    AWSCredentialsImpl newCreds = new AWSCredentialsImpl(
                                                                         CredentialsScope.GLOBAL, 
                                                                         UUID.randomUUID().toString(),
                                                                         creds.getAwsAccessKeyId(),
                                                                         creds.getAwsSecretSharedKey(),
                                                                         "Transfer from AWSEBCredentials");
                    try {
                        provider.addCredentials(domain, newCreds);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Unable to transfer credentials", e);
                    }
                }
                credentials = null;
            }

            saveAfterPause();
        }
        
        private void saveAfterPause() {
            new java.util.Timer().schedule( 
                                           new java.util.TimerTask() {
                                               @Override
                                               public void run() {
                                                   save();
                                               }
                                           }, 
                                           5000 
                                   );
        }


        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            return super.configure(req, json);
        }
    }

}
