package org.jenkinsci.plugins.liquibase.integration;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.liquibase.evaluator.ChangesetEvaluator;
import org.jenkinsci.plugins.liquibase.evaluator.ExecutedChangesetAction;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertThat;

public class ChangesetEvaluatorBuildResultTest {

    private static final Logger LOG = LoggerFactory.getLogger(ChangesetEvaluatorBuildResultTest.class);
    private static final String SUNNY_DAY_CHANGESET_XML = "/example-changesets/sunny-day-changeset.xml";
    private static final String CHANGESET_WITH_ERROR_XML = "/example-changesets/changeset-with-error.xml";
    private static final String IN_MEMORY_JDBC_URL = "jdbc:h2:mem:test";
    private static final String LIQUIBASE_PROPERTIES = "/example-changesets/h2.liquibase.properties";

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        temporaryFolder.create();
    }

    @Test
    public void should_report_success_with_error_free_changeset()
            throws IOException, ExecutionException, InterruptedException {

        FreeStyleBuild freeStyleBuild = createAndBuildErrorFreeProject();
        assertThat(freeStyleBuild.getResult(), is(Result.SUCCESS));
    }

    @Test
    public void should_indicate_unstable_build_when_changeset_has_error()
            throws IOException, ExecutionException, InterruptedException {
        File changesetFileWithError = createChangesetFileWithError(temporaryFolder);
        FreeStyleProject project = createProjectWithChangelogFile(changesetFileWithError);
        FreeStyleBuild build = launchBuildForProject(project);
        assertThat(build.getResult(), is(Result.UNSTABLE));
    }

    @Test
    public void should_indicate_success_with_yaml_formatted_changeset()
            throws IOException, ExecutionException, InterruptedException {
        FreeStyleBuild build = createAndBuildLiquibaseProject("/example-changesets/yaml-changeset-sunnyday.yml");
        assertThat(build.getResult(), is(Result.SUCCESS));
    }

    /**
     * Covers https://github.com/jenkinsci/liquibase-runner-plugin/issues/8
     */
    @Test
    public void should_use_liquibase_defaults_file() throws InterruptedException, ExecutionException, IOException {

        createProjectFiles(temporaryFolder, SUNNY_DAY_CHANGESET_XML, LIQUIBASE_PROPERTIES);
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        project.setCustomWorkspace(temporaryFolder.getRoot().getAbsolutePath());
        ChangesetEvaluator evaluator = new ChangesetEvaluator();
        evaluator.setLiquibasePropertiesPath(extractFilenameFromResourcePath(LIQUIBASE_PROPERTIES));
        project.getBuildersList().add(evaluator);
        FreeStyleBuild build = launchBuildForProject(project);
        assertThat(build.getResult(), is(Result.SUCCESS));
    }


    @Test
    public void should_executed_json_changeset_sucessfully()
            throws InterruptedException, ExecutionException, IOException {
        FreeStyleBuild build = createAndBuildLiquibaseProject("/example-changesets/json-changeset-sunnyday.json");
        assertThat(build.getResult(), is(Result.SUCCESS));
    }

    protected FreeStyleBuild createAndBuildLiquibaseProject(String changesetResourcePath)
            throws IOException, InterruptedException, ExecutionException {
        File yamlChangeset = createProjectFile(temporaryFolder, changesetResourcePath);
        FreeStyleProject project = createProjectWithChangelogFile(yamlChangeset);
        return launchBuildForProject(project);
    }

    @Test
    public void should_have_expected_executed_changesets()
            throws InterruptedException, ExecutionException, IOException {

        FreeStyleBuild build = createAndBuildErrorFreeProject();
        ExecutedChangesetAction action = build.getActions(ExecutedChangesetAction.class).get(0);
        assertThat(action.getChangeSetDetails(), hasSize(3));
    }


    protected FreeStyleBuild createAndBuildErrorFreeProject()
            throws IOException, InterruptedException, ExecutionException {
        File changelogFile = createErrorFreeChangeset(temporaryFolder);
        FreeStyleProject project = createProjectWithChangelogFile(changelogFile);
        return launchBuildForProject(project);
    }


    protected FreeStyleProject createProjectWithChangelogFile(File changelogFile) throws IOException {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        ChangesetEvaluator evaluator = new ChangesetEvaluator();
        evaluator.setChangeLogFile(changelogFile.getAbsolutePath());
        evaluator.setUrl(IN_MEMORY_JDBC_URL);
        evaluator.setDatabaseEngine("H2");
        project.getBuildersList().add(evaluator);
        return project;
    }

    protected static FreeStyleBuild launchBuildForProject(FreeStyleProject project)
            throws InterruptedException, ExecutionException {
        return project.scheduleBuild2(0).get();
    }

    private File createErrorFreeChangeset(TemporaryFolder temporaryFolder) throws IOException {
        return createProjectFile(temporaryFolder, SUNNY_DAY_CHANGESET_XML);
    }

    private File createChangesetFileWithError(TemporaryFolder temporaryFolder) throws IOException {
        return createProjectFile(temporaryFolder, CHANGESET_WITH_ERROR_XML);
    }

    private void createProjectFiles(TemporaryFolder temporaryFolder, String... resourcePaths) throws IOException {
        for (int i = 0; i < resourcePaths.length; i++) {
            String resourcePath = resourcePaths[i];
            createProjectFile(temporaryFolder, resourcePath);
        }
    }
    private File createProjectFile(TemporaryFolder temporaryFolder,
                                   String sourceResourcePath) throws IOException {
        String filename = extractFilenameFromResourcePath(sourceResourcePath);
        File changesetFile = temporaryFolder.newFile(filename);
        InputStream resourceAsStream = getClass().getResourceAsStream(sourceResourcePath);
        String changeset = IOUtils.toString(resourceAsStream);
        FileUtils.write(changesetFile, changeset);
        return changesetFile;
    }

    private String extractFilenameFromResourcePath(String sourceResourcePath) {
        return sourceResourcePath.substring(sourceResourcePath.lastIndexOf("/")+1, sourceResourcePath.length());
    }
}
