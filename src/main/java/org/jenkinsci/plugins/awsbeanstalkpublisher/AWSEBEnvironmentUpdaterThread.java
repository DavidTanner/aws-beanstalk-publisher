package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest;

public class AWSEBEnvironmentUpdaterThread implements Callable<AWSEBEnvironmentUpdaterThread> {
    private static final int MAX_ATTEMPTS = 15;
    private static final int WAIT_TIME_SECONDS = 30;
    private static final long WAIT_TIME_MILLISECONDS = TimeUnit.SECONDS.toMillis(WAIT_TIME_SECONDS);
    
    private final EnvironmentDescription envd;
    private final AWSElasticBeanstalk awseb;
    private final DescribeEnvironmentsRequest request;
    private final String environmentId;
    private final PrintStream logger;
    private final String versionLabel;

    private boolean isUpdated = false;
    private boolean isComplete = false;
    private boolean success = false;
    private int nAttempt;

    public AWSEBEnvironmentUpdaterThread(AWSElasticBeanstalk awseb, DescribeEnvironmentsRequest request, EnvironmentDescription envd, PrintStream logger, String versionLabel) {
        this.awseb = awseb;
        this.envd = envd;
        this.logger = logger;
        this.versionLabel = versionLabel;
        this.request = request;
        this.environmentId = envd.getEnvironmentId();
        nAttempt = 0;

    }

    private void log(String mask, Object... args) {
        logger.println(String.format(mask, args));
    }

    private void updateEnv() {

        log("'%s': Attempt %d/%d", envd.getEnvironmentName(), nAttempt, MAX_ATTEMPTS);

        UpdateEnvironmentRequest uavReq = new UpdateEnvironmentRequest().withEnvironmentId(environmentId).withVersionLabel(versionLabel);
        nAttempt = 0;
        isUpdated = true;

        try {
            awseb.updateEnvironment(uavReq);
            isReady();
        } catch (Exception e) {
            log("'%s': Problem:", envd.getEnvironmentName());
            e.printStackTrace(logger);

            if (nAttempt >= MAX_ATTEMPTS) {
                log("'%s': Unable to update environment!", envd.getEnvironmentName());
                isComplete = true;
            }

        }
    }

    private void isReady() {
        try {
            DescribeEnvironmentsResult result = awseb.describeEnvironments(request);
            EnvironmentDescription lastEnv = null;
            String envName = envd.getEnvironmentName();
            for (EnvironmentDescription env : result.getEnvironments()) {
                if (env.getEnvironmentId().equals(envd.getEnvironmentId())){
                    lastEnv = env;
                    break;
                }
            }
            if (lastEnv == null) {
                isComplete = true;
                log("'%s' is no longer found in ElasticBeanstalk!!!!", envName);
                return;
            }
            if (lastEnv.getStatus().equals("Ready")) {
                isComplete = true;
                
                log("'%s': Updated!", envName);
                log("'%s': Current version is:'%s'", envName, lastEnv.getVersionLabel());
                
                if (lastEnv.getVersionLabel().equals(versionLabel)) {
                    success = true;
                    log("'%s': Update was successful", envName);
                } else {
                    success = false;
                    log("'%s': Update failed, please check the recent events on the AWS console!!!!", envName);
                }
            } else {
                log("'%s': Waiting for update to finish. Status: %s", envName, lastEnv.getStatus());
            }
        } catch (Exception e) {

            log("Problem: " + e.getMessage());

            if (nAttempt >= MAX_ATTEMPTS) {
                log("'%s': unable to get environment status.", envd.getEnvironmentName());
                isComplete = true;
            }
        }
    }

    @Override
    public AWSEBEnvironmentUpdaterThread call() throws Exception {
        run();
        return this;
    }
    
    public void printResults() {
        StringBuilder status = new StringBuilder();
        status.append("'");
        status.append(envd.getEnvironmentName());
        status.append("': ");
        if (success) {
            status.append("Completed successfully.");
        } else {
            if (isUpdated) {
                status.append("Was updated, but couldn't be verified!");
            } else {
                status.append("Failed to be updated!!");
            }
        }
        log(status.toString());
    }
    
    public boolean isSuccessfull() {
        return success;
    }

    public void run() {
        while (!isComplete) {
            if (isUpdated) {
                isReady();
            } else {
                updateEnv();
            }
            if (!isComplete){
                try {
                    log("'%s': Pausing update for %d seconds", envd.getEnvironmentName(), WAIT_TIME_SECONDS);
                    Thread.sleep(WAIT_TIME_MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace(logger);
                }
            }
        }
    }
}
