package org.jenkinsci.plugins.awsbeanstalkpublisher;

import static org.apache.commons.lang.StringUtils.isNotBlank;
import hudson.tasks.Recorder;
import hudson.util.DescribableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBElasticBeanstalkSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBS3Setup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.envlookup.ByName;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetupDescriptor;

import com.amazonaws.regions.Regions;

@SuppressWarnings("unchecked")
public abstract class AWSEBPublisherBackwardsCompatibility  extends Recorder {

    public abstract DescribableList<AWSEBSetup, AWSEBSetupDescriptor> getExtensions();

    protected void readBackExtensionsFromLegacy() {
        try {
            if (isNotBlank(applicationName) || (environments != null && environments.size() > 0) || isNotBlank(versionLabelFormat)) {
                List<AWSEBSetup> s3Setup = new ArrayList<AWSEBSetup>(2);
                if (isNotBlank(bucketName) || isNotBlank(keyPrefix)) {
                    s3Setup.add(new AWSEBS3Setup(bucketName, keyPrefix, 
                            rootObject, includes, excludes, overwriteExistingFile));
                    bucketName = null;
                    keyPrefix = null;
                    rootObject = null;
                    includes = null;
                    excludes = null;
                }
                String credentialsName = "";
                if (credentials != null ){
                    credentialsName = credentials.getDisplayName();
                }
                List<AWSEBSetup> envLookup = new ArrayList<AWSEBSetup>(2);
                ByName byName = new ByName(StringUtils.join(environments, '\n'));
                envLookup.add(byName);
                addIfMissing(new AWSEBElasticBeanstalkSetup(awsRegion, "", credentialsName, "", 
                        applicationName, 
                        versionLabelFormat, failOnError, s3Setup, envLookup));
            }

        } catch (IOException e) {
            throw new AssertionError(e); // since our extensions don't have any real Saveable
        }
    }

    protected void addIfMissing(AWSEBSetup ext) throws IOException {
        if (getExtensions().get(ext.getClass()) == null) {
            getExtensions().add(ext);
        }
    }
    
    /**
     * Credentials Name from the global config
     * @deprecated
     */
    private transient AWSEBCredentials credentials;

    /**
     * Bucket Name
     * 
     * @deprecated
     */
    protected transient String bucketName;

    /**
     * Key Format
     * 
     * @deprecated
     */
    protected transient String keyPrefix;


    @Deprecated
    protected transient String rootObject;


    @Deprecated
    protected transient String includes;


    @Deprecated
    protected transient String excludes;


    @Deprecated
    protected transient Boolean overwriteExistingFile;


    @Deprecated
    protected transient Boolean failOnError;


    /**
     * AWS Region
     * 
     * @deprecated
     */
    protected transient Regions awsRegion;

    /**
     * Application Name
     * 
     * @deprecated
     */
    protected transient String applicationName;

    /**
     * Environment Name
     * 
     * @deprecated
     */
    protected transient List<String> environments;

    @Deprecated
    protected transient String versionLabelFormat;

}
