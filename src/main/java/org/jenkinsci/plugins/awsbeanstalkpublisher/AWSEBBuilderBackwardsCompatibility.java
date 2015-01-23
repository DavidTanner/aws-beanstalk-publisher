package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBElasticBeanstalkSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBS3Setup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetupDescriptor;

import com.amazonaws.regions.Regions;

import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import static org.apache.commons.lang.StringUtils.isNotBlank;

public abstract class AWSEBBuilderBackwardsCompatibility extends Builder implements BuildStep {

    abstract DescribableList<AWSEBSetup, AWSEBSetupDescriptor> getExtensions();

    void readBackExtensionsFromLegacy() {
        try {
            if (isNotBlank(applicationName)) {
                List<AWSEBSetup> s3Setup = new ArrayList<AWSEBSetup>(1);
                if (isNotBlank(bucketName)) {
                    s3Setup.add(new AWSEBS3Setup(bucketName, keyPrefix, 
                            rootObject, includes, excludes, overwriteExistingFile));
                    bucketName = null;
                    keyPrefix = null;
                    rootObject = null;
                    includes = null;
                    excludes = null;
                }
                addIfMissing(new AWSEBElasticBeanstalkSetup(awsRegion, credentials.getDisplayName(), applicationName, 
                        environmentList, versionLabelFormat, failOnError, s3Setup));
            }

        } catch (IOException e) {
            throw new AssertionError(e); // since our extensions don't have any real Saveable
        }
    }

    private void addIfMissing(AWSEBSetup ext) throws IOException {
        if (getExtensions().get(ext.getClass()) == null) {
            getExtensions().add(ext);
        }
    }
    

    /**
     * Credentials Name from the global config
     * @deprecated
     */
    protected transient AWSEBCredentials credentials;

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
    protected transient String environmentList;

    @Deprecated
    protected transient String versionLabelFormat;

}
