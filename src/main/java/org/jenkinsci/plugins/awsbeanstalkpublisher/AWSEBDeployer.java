package org.jenkinsci.plugins.awsbeanstalkpublisher;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.util.DirScanner;
import hudson.util.LogTaskListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationDescription;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeApplicationsResult;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.S3Location;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.common.annotations.VisibleForTesting;

public class AWSEBDeployer {
    private static final Logger logger = Logger.getLogger(AWSEBDeployer.class.getName());

    private AWSEBProvider context;

    private PrintStream log;

    private AmazonS3 s3;

    private AWSElasticBeanstalk awseb;

    private File localArchive;

    private FilePath rootFileObject;

    private String keyPrefix;

    private String bucketName;

    private String applicationName;

    private String versionLabel;

    private String objectKey;

    private String s3ObjectPath;

    private List<String> environmentNames;

    private BuildListener listener;
    
    private AbstractBuild<?, ?> build;
    
    private final static int MAX_THREAD_COUNT = 5;

    public AWSEBDeployer(AWSEBProvider builder, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        this.context = builder;
        this.build = build;
        this.log = listener.getLogger();
        this.listener = listener;

        this.rootFileObject = new FilePath(build.getWorkspace(), getValue(context.getRootObject()));
    }

    public void perform() throws Exception {
        initThis();
        initAWS();

        log("Running AWS Elastic Beanstalk deploy for %s, on %s", applicationName, environmentNames);

        localArchive = getLocalFileObject(rootFileObject);

        uploadArchive();

        createApplicationVersion();

        updateEnvironments();
    }

    private void initThis() {
        this.keyPrefix = getValue(context.getKeyPrefix());
        this.bucketName = getValue(context.getBucketName());
        this.applicationName = getValue(context.getApplicationName());
        this.versionLabel = getValue(context.getVersionLabelFormat());
        this.environmentNames = getValue(context.getEnvironments());
    }

    public void updateEnvironments() {
        DescribeEnvironmentsRequest request;
        if (environmentNames != null && !environmentNames.isEmpty()) {
            request = new DescribeEnvironmentsRequest().withApplicationName(applicationName).withEnvironmentNames(environmentNames);
        } else {
            request = new DescribeEnvironmentsRequest().withApplicationName(applicationName);
        }
        try {
            updateEnvironments(request);
        } catch (Exception e) {
            e.printStackTrace(log);
        }
    }

    public void updateEnvironments(DescribeEnvironmentsRequest request) throws InterruptedException {
        DescribeEnvironmentsResult result = awseb.describeEnvironments(request);

        List<EnvironmentDescription> envList = result.getEnvironments();

        if (envList.size() <= 0) {
            log("No environments found matching applicationName:%s with environments:%s", applicationName, environmentNames);
            listener.finished(Result.SUCCESS);
            return;
        }
        
        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREAD_COUNT);
        
        List<AWSEBEnvironmentUpdater> updaters = new ArrayList<AWSEBEnvironmentUpdater>();
        for (EnvironmentDescription envd : envList) {
            log("Environment found (environment id='%s', name='%s'). Attempting to update environment to version label '%s'", envd.getEnvironmentId(), envd.getEnvironmentName(), versionLabel);
            updaters.add(new AWSEBEnvironmentUpdater(awseb, request, envd, log, versionLabel));
        }
        List<Future<AWSEBEnvironmentUpdater>> results = pool.invokeAll(updaters);
        
        printResults(results);
    }
    
    private void printResults(List<Future<AWSEBEnvironmentUpdater>> results) {
        boolean hadFailures = false;
        for (Future<AWSEBEnvironmentUpdater> future : results) {
            try {
            AWSEBEnvironmentUpdater result = future.get();
            hadFailures |= result.isSuccessfull();
            result.printResults();
            } catch (Exception e) {
                log("Unable to get results from update");
                e.printStackTrace(log);
            }
        }
        if (context.getFailOnError() && hadFailures){
            listener.finished(Result.FAILURE);
        } else {
            listener.finished(Result.SUCCESS);
        }
    }
    

    private void createApplicationVersion() {
        log("Creating application version %s for application %s for path %s", versionLabel, applicationName, s3ObjectPath);

        CreateApplicationVersionRequest cavRequest = new CreateApplicationVersionRequest().withApplicationName(applicationName).withAutoCreateApplication(true)
                .withSourceBundle(new S3Location(bucketName, objectKey)).withVersionLabel(versionLabel);

        awseb.createApplicationVersion(cavRequest);
    }

    private void uploadArchive() {
        objectKey = formatPath("%s/%s-%s.zip", keyPrefix, applicationName, versionLabel);

        s3ObjectPath = "s3://" + formatPath("%s/%s", bucketName, objectKey);

        log("Uploading file %s as %s", localArchive.getName(), s3ObjectPath);

        boolean uploadFile = true;

        try {
            ObjectMetadata meta = s3.getObjectMetadata(bucketName, objectKey);
            uploadFile = context.isOverwriteExistingFile();
        } catch (AmazonS3Exception s3e) {
            if (s3e.getStatusCode() == 403 || s3e.getStatusCode() == 404) {
                // i.e. 404: NoSuchKey - The specified key does not exist
                // 403: PermissionDenied is a sneaky way to hide that the file doesn't exist
                uploadFile = true;
            } else {
                throw s3e;
            }
        }

        if (uploadFile) {
            s3.putObject(bucketName, objectKey, localArchive);
        }
    }
    
    public static AWSElasticBeanstalk getElasticBeanstalk(AWSCredentialsProvider credentials, Region region) {
        AWSElasticBeanstalk awseb = region.createClient(AWSElasticBeanstalkClient.class, credentials, getClientConfig());
        return awseb;
    }
    
    public static AmazonS3 getS3(AWSCredentialsProvider credentials, Region region) {
        AmazonS3 s3 = region.createClient(AmazonS3Client.class, credentials, getClientConfig());
        return s3;
    }
    
    public static ClientConfiguration getClientConfig() {
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setUserAgent(ClientConfiguration.DEFAULT_USER_AGENT);
        return clientConfig;
    }

    private void initAWS() {
        log("Creating S3 and AWSEB Client (AWS Access Key Id: %s, region: %s)", context.getCredentials().getAwsAccessKeyId(), context.getAwsRegion());
        
        AWSCredentialsProvider provider = context.getCredentials().getAwsCredentials();
        Region region = Region.getRegion(context.getAwsRegion());
        
        s3 = getS3(provider, region);
        awseb = getElasticBeanstalk(provider, region);
    }

    @VisibleForTesting
    void setAsweb(AWSElasticBeanstalk awseb) {
        this.awseb = awseb;
    }

    @VisibleForTesting
    void setS3(AmazonS3 s3) {
        this.s3 = s3;
    }

    private void log(String mask, Object... args) {
        log.println(String.format(mask, args));
    }

    private File getLocalFileObject(FilePath rootFileObject) throws Exception {
        File resultFile = File.createTempFile("awseb-", ".zip");

        if (!rootFileObject.isDirectory()) {
            log("Root File Object is a file. We assume its a zip file, which is okay.");

            rootFileObject.copyTo(new FileOutputStream(resultFile));
        } else {
            log("Zipping contents of Root File Object (%s) into tmp file %s (includes=%s, excludes=%s)", rootFileObject.getName(), resultFile.getName(), context.getIncludes(), context.getExcludes());

            rootFileObject.zip(new FileOutputStream(resultFile), new DirScanner.Glob(context.getIncludes(), context.getExcludes()));
        }

        return resultFile;
    }

    private String formatPath(String mask, Object... args) {
        return strip(String.format(mask, args).replaceAll("/{2,}", ""));
    }

    private List<String> getValue(List<String> values) {
        List<String> newValues = new ArrayList<String>(values.size());
        for (String value : values) {
            if (!value.isEmpty()) {
                newValues.add(getValue(value));
            }
        }
        return newValues;
    }

    private String getValue(String value) {
        return strip(replaceMacros(build, value));
    }

    private static String strip(String str) {
        return StringUtils.strip(str, "/ ");
    }
    

    public static List<ApplicationDescription> getApplications(AWSCredentialsProvider credentials, Regions region) {
        AWSElasticBeanstalk awseb = AWSEBDeployer.getElasticBeanstalk(credentials, Region.getRegion(region));
        DescribeApplicationsResult result = awseb.describeApplications();
        return result.getApplications();
    }
    
    public static List<EnvironmentDescription> getEnvironments(AWSCredentialsProvider credentials, Regions region, String appName) {
        AWSElasticBeanstalk awseb = AWSEBDeployer.getElasticBeanstalk(credentials, Region.getRegion(region));
        
        DescribeEnvironmentsRequest request = new DescribeEnvironmentsRequest().withApplicationName(appName);
                
        DescribeEnvironmentsResult result = awseb.describeEnvironments(request);
        return result.getEnvironments();
    }
    
    public static String replaceMacros(AbstractBuild<?, ?> build, String inputString) {
        String returnString = inputString;
        if (build != null && inputString != null) {
            try {
                Map<String, String> messageEnvVars = new HashMap<String, String>();

                messageEnvVars.putAll(build.getCharacteristicEnvVars());
                messageEnvVars.putAll(build.getBuildVariables());
                messageEnvVars.putAll(build.getEnvironment(new LogTaskListener(logger, Level.INFO)));

                returnString = Util.replaceMacro(inputString, messageEnvVars);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Couldn't replace macros in message: ", e);
            }
        }
        return returnString;

    }
}
