package org.jenkinsci.plugins.awsbeanstalkpublisher;

import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.util.DirScanner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBElasticBeanstalkSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBS3Setup;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest;
import com.amazonaws.services.elasticbeanstalk.model.S3Location;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.common.annotations.VisibleForTesting;

public class AWSEBS3Uploader {

    
    private final String keyPrefix;
    private final String bucketName;
    private final String includes;
    private final String excludes;
    private final String rootObject;
    private final boolean isOverwriteExistingFile;
    private final PrintStream log;
    
    private final String applicationName;
    private final String versionLabel;
    private final Regions awsRegion;
    private final AbstractBuild<?, ?> build;
    private final AWSEBCredentials credentials;
    

    private String objectKey;
    private String s3ObjectPath;
    private AmazonS3 s3;
    
    public AWSEBS3Uploader(AbstractBuild<?, ?> build, BuildListener listener, Regions awsRegion, 
            AWSEBCredentials credentials, AWSEBS3Setup s3Setup,
            String applicationName, String versionLabel) {
        this.credentials = credentials;
        this.build = build;
        this.awsRegion = awsRegion;
        this.log = listener.getLogger();
        this.applicationName = AWSEBUtils.getValue(build, applicationName);
        this.versionLabel = AWSEBUtils.getValue(build, versionLabel);
        this.keyPrefix = AWSEBUtils.getValue(build, s3Setup.getKeyPrefix());
        this.bucketName = AWSEBUtils.getValue(build, s3Setup.getBucketName());
        this.includes = AWSEBUtils.getValue(build, s3Setup.getIncludes());
        this.excludes = AWSEBUtils.getValue(build, s3Setup.getExcludes());
        this.rootObject = AWSEBUtils.getValue(build, s3Setup.getRootObject());
        this.isOverwriteExistingFile = s3Setup.isOverwriteExistingFile();
    }
    

    public AWSEBS3Uploader(AbstractBuild<?, ?> build, BuildListener listener, AWSEBElasticBeanstalkSetup envSetup, AWSEBS3Setup s3) {
        this(build, listener, envSetup.getAwsRegion(), envSetup.getCredentials(), s3, envSetup.getApplicationName(), envSetup.getVersionLabelFormat());
    }


    public void uploadArchive(AWSElasticBeanstalk awseb) throws Exception {
        if (s3 == null) {
            s3 = AWSEBUtils.getS3(credentials, awsRegion);
        }

        objectKey = AWSEBUtils.formatPath("%s/%s-%s.zip", keyPrefix, applicationName, versionLabel);

        s3ObjectPath = "s3://" + AWSEBUtils.formatPath("%s/%s", bucketName, objectKey);
        FilePath rootFileObject = new FilePath(build.getWorkspace(), AWSEBUtils.getValue(build, rootObject));
        File localArchive = getLocalFileObject(rootFileObject);

        AWSEBUtils.log(log, "Uploading file %s as %s", localArchive.getName(), s3ObjectPath);

        boolean uploadFile = true;

        try {
            ObjectMetadata meta = s3.getObjectMetadata(bucketName, objectKey);
            String awsMd5 = meta.getContentMD5();
            FileInputStream fis = new FileInputStream(localArchive);
            String ourMd5 = DigestUtils.md5Hex(fis);
            fis.close();
            if (ourMd5.equals(awsMd5)) {
                uploadFile = false || isOverwriteExistingFile;
            }
        } catch (AmazonS3Exception s3e) {
            if (s3e.getStatusCode() == 403 || s3e.getStatusCode() == 404) {
                // i.e. 404: NoSuchKey - The specified key does not exist
                // 403: PermissionDenied is a sneaky way to hide that the file doesn't exist
                uploadFile = true;
            } else {
                throw s3e;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace(log);
        } catch (IOException e) {
            e.printStackTrace(log);
        }

        if (uploadFile) {
            s3.putObject(bucketName, objectKey, localArchive);
        }
        createApplicationVersion(awseb);
    }

    @VisibleForTesting
    void setS3(AmazonS3 s3) {
        this.s3 = s3;
    }

    private File getLocalFileObject(FilePath rootFileObject) throws Exception {
        File resultFile = File.createTempFile("awseb-", ".zip");

        if (!rootFileObject.isDirectory()) {
            AWSEBUtils.log(log, "Root File Object is a file. We assume its a zip file, which is okay.");

            rootFileObject.copyTo(new FileOutputStream(resultFile));
        } else {
            AWSEBUtils.log(log, "Zipping contents of Root File Object (%s) into tmp file %s (includes=%s, excludes=%s)", rootFileObject.getName(), resultFile.getName(), includes, excludes);

            rootFileObject.zip(new FileOutputStream(resultFile), new DirScanner.Glob(includes, excludes));
        }

        return resultFile;
    }
    
    public void createApplicationVersion(AWSElasticBeanstalk awseb) {
        AWSEBUtils.log(log, "Creating application version %s for application %s for path %s", versionLabel, applicationName, s3ObjectPath);

        CreateApplicationVersionRequest cavRequest = new CreateApplicationVersionRequest().withApplicationName(applicationName).withAutoCreateApplication(true)
                .withSourceBundle(new S3Location(bucketName, objectKey)).withVersionLabel(versionLabel);

        awseb.createApplicationVersion(cavRequest);
    }


}
