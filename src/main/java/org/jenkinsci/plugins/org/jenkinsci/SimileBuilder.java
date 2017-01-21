package org.jenkinsci.plugins.org.jenkinsci;

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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link SimileBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #repository})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform} method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class SimileBuilder extends Builder implements SimpleBuildStep {

    private static final String SIMILE_URL = "http://simile.herokuapp.com/repository";

    private final String repository;
    private final String branch;
    private final String email;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public SimileBuilder(String repository, String branch, String email) {
        this.repository = repository;
        this.branch = branch;
        this.email = email;
    }

    /**
     * We'll use this from the {@code config.jelly}.
     */
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

            HttpResponse<JsonNode> response = Unirest.post(SIMILE_URL)
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
        }
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link SimileBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See {@code src/main/resources/hudson/plugins/hello_world/SimileBuilder/*.jelly}
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
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
            // Indicates that this builder can be used with all kinds of project types 
            return true;
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
    }
}

