package com.saucelabs.jenkins.pipeline;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.plugins.sauce_ondemand.SauceEnvironmentUtil;
import hudson.plugins.sauce_ondemand.SauceOnDemandBuildAction;
import hudson.plugins.sauce_ondemand.SauceOnDemandBuildWrapper;
import hudson.plugins.sauce_ondemand.credentials.SauceCredentials;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

@ExportedBean
public class SauceStep extends Step {
    private String credentialsId;

    @DataBoundConstructor
    public SauceStep(String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(this, context);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1;

        private transient SauceStep step;

        public Execution(SauceStep sauceStep, StepContext context) {
            super(context);
            step = sauceStep;
        }

        @Override
        protected Void run() throws Exception {
            Run<?,?> run = getContext().get(Run.class);
            Job<?,?> job = run.getParent();
            if (!(job instanceof TopLevelItem)) {
                throw new Exception(job + " must be a top-level job");
            }

            SauceCredentials credentials = SauceCredentials.getCredentialsById(job, step.getCredentialsId());
            if (credentials == null) {
                throw new Exception("no credentials provided");
            }
            CredentialsProvider.track(run, credentials);


            HashMap<String,String> overrides = new HashMap<String,String>();
            overrides.put(SauceOnDemandBuildWrapper.SAUCE_USERNAME, credentials.getUsername());
            overrides.put(SauceOnDemandBuildWrapper.SAUCE_ACCESS_KEY, credentials.getPassword().getPlainText());
            overrides.put(SauceOnDemandBuildWrapper.SAUCE_REST_ENDPOINT, credentials.getRestEndpoint());
            overrides.put(SauceOnDemandBuildWrapper.JENKINS_BUILD_NUMBER, SauceEnvironmentUtil.getSanitizedBuildNumber(run));
            overrides.put(SauceOnDemandBuildWrapper.SAUCE_BUILD_NAME, SauceEnvironmentUtil.getSanitizedBuildNumber(run));

            SauceOnDemandBuildAction buildAction = run.getAction(SauceOnDemandBuildAction.class);
            if (buildAction == null) {
                buildAction = new SauceOnDemandBuildAction(run, credentials.getId());
                run.addAction(buildAction);
            }

            getContext().newBodyInvoker()
                .withContext(credentials)
                .withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(overrides)))
                .withCallback(BodyExecutionCallback.wrap(getContext()))
                .start();

            return null;
        }

    }


    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getDisplayName() {
            return "Sauce";
        }

        @Override public String getFunctionName() {
            return "sauce";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<Class<?>> getProvidedContext() {
            return Collections.<Class<?>>singleton(SauceCredentials.class);
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(final @AncestorInPath Item project) {
            return new StandardUsernameListBoxModel()
                .withAll(SauceCredentials.all(project));
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, Run.class);
        }

    }
}
