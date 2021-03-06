/*
The MIT License (MIT)

Copyright (c) 2016, Andrew Bayer, CloudBees Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.jenkinsci.plugins.xunit;

import java.io.IOException;

import org.jenkinsci.lib.dtkit.type.TestType;
import org.jenkinsci.plugins.xunit.threshold.FailedThreshold;
import org.jenkinsci.plugins.xunit.threshold.XUnitThreshold;
import org.jenkinsci.plugins.xunit.types.JUnitType;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.junit.TestResultAction;

public class XUnitPublisherTest {

    public static class SpyXUnitPublisher extends XUnitPublisher {

        public SpyXUnitPublisher(TestType[] tools, XUnitThreshold[] thresholds, int thresholdMode, String testTimeMargin) {
            super(tools, thresholds, thresholdMode, testTimeMargin);
        }

        @Override
        public void perform(Run<?, ?> build,
                            FilePath workspace,
                            Launcher launcher,
                            TaskListener listener) throws InterruptedException, IOException {
            super.perform(build, workspace, launcher, listener);
            Assert.assertEquals("Unexpected build FAILURE setup by the first publisher", build.getResult(), Result.SUCCESS);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public BuildStepDescriptor getDescriptor() {
            return new BuildStepDescriptor<Publisher>(SpyXUnitPublisher.class) {

                @Override
                public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                    return true;
                }
            };
        }
    }

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @LocalData
    @Issue("JENKINS-47194")
    @Test
    public void different_build_steps_use_separate_output_folders_and_use_new_instance_of_TestResult_against_validate_thresholds() throws Exception {
        FreeStyleProject job = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "JENKINS-47194");

        TestType[] tools1 = new TestType[] { new JUnitType("module1/*.xml", false, false, false, true) };

        XUnitThreshold threshold1 = new FailedThreshold();
        threshold1.setFailureThreshold("2");
        // this publisher should not fails since the failure threshold is equals
        // to that of the failed counter of the test result
        job.getPublishersList().add(new SpyXUnitPublisher(tools1, new XUnitThreshold[] { threshold1 }, 1, "3000"));

        TestType[] tools2 = new TestType[] { new JUnitType("module2/*.xml", false, false, false, true) };
        XUnitThreshold threshold2 = new FailedThreshold();
        threshold2.setFailureThreshold("2");
        // this publisher should not fails since the failure threshold is equals
        // to that of the failed counter of the test result. The failed count
        // should not takes in account any results from previous publishers
        job.getPublishersList().add(new XUnitPublisher(tools2, new XUnitThreshold[] { threshold2 }, 1, "3000"));

        FreeStyleBuild build = job.scheduleBuild2(0).get();
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        Assert.assertNotNull(testResultAction);
        Assert.assertEquals(9, testResultAction.getTotalCount());
        Assert.assertEquals(4, testResultAction.getFailCount());
    }

}