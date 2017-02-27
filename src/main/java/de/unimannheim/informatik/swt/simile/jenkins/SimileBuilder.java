/*
 * Copyright (c) 2017, Chair of Software Technology
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * •	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 * •	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 * •	Neither the name of the University Mannheim nor the names of its
 * 	contributors may be used to endorse or promote products derived from
 * 	this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */

package de.unimannheim.informatik.swt.simile.jenkins;

import com.google.common.base.Strings;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.regex.Pattern;

public class SimileBuilder extends Builder implements SimpleBuildStep {

    private String SIMILE_URL = getSimilerServerUrl() + "/repository";

    private final String repository;
    private final String branch;
    private final String email;

    @DataBoundConstructor
    public SimileBuilder(String repository, String branch, String email) {
        this.repository = repository;
        this.branch = branch;
        this.email = email;
    }

    public String getRepository() {
        return this.repository;
    }

    public String getBranch() {
        return this.branch;
    }

    public String getEmail() {
        return this.email;
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
        try {
            listener.getLogger().println("Simile Jenkins Plugin");
            listener.getLogger().println(Strings.repeat("=", "Simile Jenkins Plugin".length()));
            listener.getLogger().println("Sending data to Simile for component search.");
            listener.getLogger().println(String.format("Simile Endpoint: %s", SIMILE_URL));

            HttpResponse<JsonNode> response = Unirest.post(SIMILE_URL)
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .queryString("repo", this.repository)
                    .queryString("branch", this.branch)
                    .queryString("email", this.email)
                    .asJson();

            if(response.getStatus() == 200) {
                listener.getLogger().println("Data sent successfully!");
                listener.getLogger().println(Strings.repeat("=", "Simile Jenkins Plugin".length()));
            } else {
                listener.getLogger().println("An error happened when sending data to Simile.");
                listener.getLogger().println(response.getBody().toString());
            }
        } catch (UnirestException e) {
            listener.getLogger().println("An error happened when sending data to Simile.");
            listener.getLogger().println(e.getMessage());
            listener.getLogger().println(ExceptionUtils.getFullStackTrace(e));
        }
    }

    private String getSimilerServerUrl() {
        if (Strings.isNullOrEmpty(getDescriptor().getSimileUrl()))
            return "http://localhost:8080/simile";
        else
            return getDescriptor().getSimileUrl();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link SimileBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private String simileUrl;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'gitRepository'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckRepository(@QueryParameter String value) throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a Git repository");
            if (!this.isValidGithubWebURL(value))
                return FormValidation.error("The repository is not a valid Git repository. Please use HTTP URL.");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the repository too short?");
            return FormValidation.ok();
        }

        public FormValidation doCheckEmail(@QueryParameter String value) throws IOException, ServletException {
            if (!this.isValidEmail(value))
                return FormValidation.error("The email is not valid");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            this.simileUrl = formData.getString("simileUrl");
            save();
            return super.configure(req, formData);
        }

        /**
         * This human readable gitRepository is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "Simile - Search for similar components";
        }

        /**
         * Validates if an email address is valid.
         *
         * @see <a href="http://www.regexr.com/3c0ol">http://www.regexr.com/3c0ol</a>
         *
         * @param email email address to be validated.
         * @return true in case the email address is valid, otherwise false.
         * */
        private boolean isValidEmail(String email) {
            final Pattern pattern = Pattern.compile("[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?");
            return pattern.matcher(email).matches();
        }

        /**
         * Validates if a Git repository url is a valid Git Web URL.
         *
         * @param gitRepo Git web URL to be validated.
         * @return true if the git url is valid, otherwise false.
         * */
        private boolean isValidGithubWebURL(String gitRepo) {
            final Pattern pattern = Pattern.compile("(http(s)?)(:(//)?)([\\w\\.@\\:/\\-~]+)(\\.git)?");
            return pattern.matcher(gitRepo).matches();
        }

        public String getSimileUrl() {
            return this.simileUrl;
        }
    }
}

