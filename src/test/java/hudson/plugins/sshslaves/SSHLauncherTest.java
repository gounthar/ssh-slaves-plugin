/*
 * The MIT License
 *
 * Copyright (c) 2004-, all the contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.sshslaves;

import static hudson.plugins.sshslaves.SSHLauncher.JAR_CACHE_DIR;
import static hudson.plugins.sshslaves.SSHLauncher.JAR_CACHE_PARAM;
import static hudson.plugins.sshslaves.SSHLauncher.WORK_DIR_PARAM;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.HostnamePortSpecification;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor;
import hudson.model.Fingerprint;
import hudson.model.Slave;
import hudson.plugins.sshslaves.verifiers.KnownHostsFileKeyVerificationStrategy;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.slaves.DumbSlave;
import hudson.util.FormValidation;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collections;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SSHLauncherTest {

    @TempDir
    private File temporaryFolder;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private void checkRoundTrip(String host) throws Exception {
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(
                        Domain.global(),
                        Collections.singletonList(new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM, "dummyCredentialId", null, "user", "pass")));
        SSHLauncher launcher = new SSHLauncher(host, 123, "dummyCredentialId");
        launcher.setSshHostKeyVerificationStrategy(new KnownHostsFileKeyVerificationStrategy());
        assertEquals(host.trim(), launcher.getHost());
        DumbSlave agent =
                new DumbSlave("agent", newFolder(temporaryFolder, "junit").getAbsolutePath(), launcher);
        j.jenkins.addNode(agent);

        HtmlPage p = j.createWebClient().getPage(agent, "configure");
        j.submit(p.getFormByName("config"));
        Slave n = (Slave) j.jenkins.getNode("agent");

        assertNotSame(n, agent);
        assertNotSame(n.getLauncher(), launcher);
        j.assertEqualDataBoundBeans(n.getLauncher(), launcher);
    }

    @Test
    void configurationRoundTrip() throws Exception {
        checkRoundTrip("localhost");
    }

    @Test
    void fillCredentials() {
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(
                        new Domain("test", null, Collections.singletonList(new HostnamePortSpecification(null, null))),
                        Collections.singletonList(new BasicSSHUserPrivateKey(
                                CredentialsScope.SYSTEM, "dummyCredentialId", "john", null, null, null)));

        SSHLauncher.DescriptorImpl desc = (SSHLauncher.DescriptorImpl) j.jenkins.getDescriptorOrDie(SSHLauncher.class);
        assertEquals(
                2,
                desc.doFillCredentialsIdItems(j.jenkins, "", "22", "does-not-exist")
                        .size());
        assertEquals(
                1,
                desc.doFillCredentialsIdItems(j.jenkins, "", "22", "dummyCredentialId")
                        .size());
        assertEquals(
                1,
                desc.doFillCredentialsIdItems(j.jenkins, "", "forty two", "does-not-exist")
                        .size());
        assertEquals(
                1,
                desc.doFillCredentialsIdItems(j.jenkins, "", "", "does-not-exist")
                        .size());
    }

    @Test
    void checkJavaPathWhiteSpaces() {
        SSHLauncher.DescriptorImpl desc = (SSHLauncher.DescriptorImpl) j.jenkins.getDescriptorOrDie(SSHLauncher.class);
        assertEquals(FormValidation.ok(), desc.doCheckJavaPath("/usr/lib/jdk/bin/java"));
        assertEquals(FormValidation.ok(), desc.doCheckJavaPath("\"/usr/lib/jdk/bin/java\""));
        assertEquals(FormValidation.ok(), desc.doCheckJavaPath("'/usr/lib/jdk/bin/java'"));
        assertEquals(FormValidation.ok(), desc.doCheckJavaPath("\"/usr/lib/jdk 11/bin/java\""));
        assertEquals(FormValidation.ok(), desc.doCheckJavaPath("'/usr/lib/jdk 11/bin/java'"));
        assertEquals(FormValidation.Kind.WARNING, desc.doCheckJavaPath("/usr/lib/jdk 11/bin/java").kind);
    }

    @Test
    void checkHost() {
        SSHLauncher.DescriptorImpl desc = (SSHLauncher.DescriptorImpl) j.jenkins.getDescriptorOrDie(SSHLauncher.class);
        assertEquals(FormValidation.ok(), desc.doCheckHost("hostname"));
        assertEquals(FormValidation.Kind.ERROR, desc.doCheckHost("").kind);
        assertEquals(FormValidation.Kind.ERROR, desc.doCheckHost(null).kind);
    }

    @Test
    void checkPort() {
        SSHLauncher.DescriptorImpl desc = (SSHLauncher.DescriptorImpl) j.jenkins.getDescriptorOrDie(SSHLauncher.class);
        assertEquals(FormValidation.ok(), desc.doCheckPort("22"));
        assertEquals(FormValidation.Kind.ERROR, desc.doCheckPort("").kind);
        assertEquals(FormValidation.Kind.ERROR, desc.doCheckPort(null).kind);
        assertEquals(FormValidation.Kind.ERROR, desc.doCheckPort("-1").kind);
        assertEquals(FormValidation.Kind.ERROR, desc.doCheckPort("65536").kind);
    }

    @Test
    void trimWhiteSpace() throws Exception {
        checkRoundTrip("   localhost");
        checkRoundTrip("localhost    ");
        checkRoundTrip("   localhost    ");
    }

    @Issue("JENKINS-38832")
    @Test
    void trackCredentialsWithUsernameAndPassword() throws Exception {
        UsernamePasswordCredentialsImpl credentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummyCredentialId", null, "user", "pass");
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(Domain.global(), Collections.singletonList(credentials));
        SSHLauncher launcher = new SSHLauncher("localhost", 123, "dummyCredentialId");
        launcher.setLaunchTimeoutSeconds(5);
        launcher.setRetryWaitTime(5);
        launcher.setMaxNumRetries(2);
        DumbSlave agent =
                new DumbSlave("agent", newFolder(temporaryFolder, "junit").getAbsolutePath(), launcher);

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat("No fingerprint created until use", fingerprint, nullValue());

        j.jenkins.addNode(agent);
        while (agent.toComputer().isConnecting()) {
            // Make sure verification takes place after launch is complete
            Thread.sleep(100);
        }

        fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat(fingerprint, notNullValue());
    }

    @Issue("JENKINS-38832")
    @Test
    void trackCredentialsWithUsernameAndPrivateKey() throws Exception {
        BasicSSHUserPrivateKey credentials =
                new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "dummyCredentialId", "user", null, "", "desc");
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(Domain.global(), Collections.singletonList(credentials));
        SSHLauncher launcher = new SSHLauncher("localhost", 123, "dummyCredentialId");
        launcher.setLaunchTimeoutSeconds(5);
        launcher.setRetryWaitTime(5);
        launcher.setMaxNumRetries(2);
        DumbSlave agent =
                new DumbSlave("agent", newFolder(temporaryFolder, "junit").getAbsolutePath(), launcher);

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat("No fingerprint created until use", fingerprint, nullValue());

        j.jenkins.addNode(agent);
        while (agent.toComputer().isConnecting()) {
            // Make sure verification takes place after launch is complete
            Thread.sleep(100);
        }

        fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat(fingerprint, notNullValue());
    }

    @Issue("JENKINS-44111")
    @Test
    void workDirTest() {
        String rootFS = "/home/user";
        String anotherWorkDir = "/another/workdir";

        SSHLauncher launcher = new SSHLauncher(
                "Hostname",
                22,
                "credentialID",
                "jvmOptions",
                "javaPath",
                "prefix",
                "suffix",
                60,
                10,
                15,
                new NonVerifyingKeyVerificationStrategy());
        // use rootFS
        assertEquals(
                WORK_DIR_PARAM + rootFS + JAR_CACHE_PARAM + rootFS + JAR_CACHE_DIR, launcher.getWorkDirParam(rootFS));

        launcher = new SSHLauncher(
                "Hostname",
                22,
                "credentialID",
                "jvmOptions",
                "javaPath",
                "prefix",
                "suffix" + WORK_DIR_PARAM + anotherWorkDir,
                60,
                10,
                15,
                new NonVerifyingKeyVerificationStrategy());
        // if worDir is in suffix return ""
        assertEquals("", launcher.getWorkDirParam(rootFS));
        // if worDir is in suffix return "", even do you set workDir in configuration
        launcher.setWorkDir(anotherWorkDir);
        assertEquals("", launcher.getWorkDirParam(rootFS));

        launcher = new SSHLauncher(
                "Hostname",
                22,
                "credentialID",
                "jvmOptions",
                "javaPath",
                "prefix",
                "suffix",
                60,
                10,
                15,
                new NonVerifyingKeyVerificationStrategy());
        // user the workDir set in configuration
        launcher.setWorkDir(anotherWorkDir);
        assertEquals(
                WORK_DIR_PARAM + anotherWorkDir + JAR_CACHE_PARAM + anotherWorkDir + JAR_CACHE_DIR,
                launcher.getWorkDirParam(rootFS));
    }

    @Test
    void timeoutAndRetrySettings() {
        final SSHLauncher launcher = new SSHLauncher(
                "Hostname",
                22,
                "credentialID",
                "jvmOptions",
                "javaPath",
                "prefix",
                "suffix",
                39,
                18,
                25,
                new NonVerifyingKeyVerificationStrategy());
        assertEquals(39, launcher.getLaunchTimeoutSeconds().intValue());
        assertEquals(18, launcher.getMaxNumRetries().intValue());
        assertEquals(25, launcher.getRetryWaitTime().intValue());
    }

    @Issue("JENKINS-54934")
    @Test
    void timeoutAndRetrySettingsAllowZero() {
        final SSHLauncher launcher = new SSHLauncher(
                "Hostname",
                22,
                "credentialID",
                "jvmOptions",
                "javaPath",
                "prefix",
                "suffix",
                0,
                0,
                0,
                new NonVerifyingKeyVerificationStrategy());
        assertEquals(0, launcher.getMaxNumRetries().intValue());
        assertEquals(0, launcher.getRetryWaitTime().intValue());
    }

    @Test
    void timeoutAndRetrySettingsSetDefaultsIfOutOfRange() {
        final SSHLauncher launcher = new SSHLauncher(
                "Hostname",
                22,
                "credentialID",
                "jvmOptions",
                "javaPath",
                "prefix",
                "suffix",
                0,
                -1,
                -1,
                new NonVerifyingKeyVerificationStrategy());
        assertEquals(SSHLauncher.DEFAULT_LAUNCH_TIMEOUT_SECONDS, launcher.getLaunchTimeoutSeconds());
        assertEquals(SSHLauncher.DEFAULT_MAX_NUM_RETRIES, launcher.getMaxNumRetries());
        assertEquals(SSHLauncher.DEFAULT_RETRY_WAIT_TIME, launcher.getRetryWaitTime());

        final SSHLauncher launcher2 = new SSHLauncher(
                "Hostname",
                22,
                "credentialID",
                "jvmOptions",
                "javaPath",
                "prefix",
                "suffix",
                null,
                null,
                null,
                new NonVerifyingKeyVerificationStrategy());
        assertEquals(SSHLauncher.DEFAULT_LAUNCH_TIMEOUT_SECONDS, launcher2.getLaunchTimeoutSeconds());
        assertEquals(SSHLauncher.DEFAULT_MAX_NUM_RETRIES, launcher2.getMaxNumRetries());
        assertEquals(SSHLauncher.DEFAULT_RETRY_WAIT_TIME, launcher2.getRetryWaitTime());
    }

    @Test
    void getMd5Hash() throws Exception {
        byte[] bytes = "Leave me alone!".getBytes();
        String result = SSHLauncher.getMd5Hash(bytes);
        assertEquals("1EB226C8E950BAC1494BE197E84A264C", result);
    }

    @Test
    void readInputStreamIntoByteArrayAndClose() throws Exception {
        File testFile =
                new File("target" + File.separator + "test-classes", "readInputStreamIntoByteArrayTestFile.txt");
        try (InputStream inputStream = new FileInputStream(testFile)) {
            assertTrue(testFile.exists());
            byte[] bytes = SSHLauncher.readInputStreamIntoByteArrayAndClose(inputStream);
            assertNotNull(bytes);
            assertTrue(bytes.length > 0);
            assertEquals("Don't change me or add newlines!", new String(bytes));
        }
    }

    @Test
    void retryTest() throws IOException, InterruptedException, Descriptor.FormException {
        DumbSlave agent = getPermanentAgentHostNotExist();
        j.jenkins.addNode(agent);
        String log = "";
        for (int i = 0; i < 60; i++) {
            Thread.sleep(1000);
            log = agent.getComputer().getLog();
            if (log.contains("There are 1 more retries left.")) {
                break;
            }
        }
        assertTrue(log.contains("There are 3 more retries left."));
        assertTrue(log.contains("There are 2 more retries left."));
        assertTrue(log.contains("There are 1 more retries left."));
        assertFalse(log.contains("There are 4 more retries left."));
    }

    @Test
    void knownHostsFileDefaultConfig() {
        String defaultPath = Paths.get(System.getProperty("user.home"), ".ssh", "known_hosts")
                .toString();
        KnownHostsFileKeyVerificationStrategy khvs = new KnownHostsFileKeyVerificationStrategy();
        assertEquals(khvs.getKnownHostsFile().getPath(), defaultPath);
    }

    private DumbSlave getPermanentAgentHostNotExist() throws Descriptor.FormException, IOException {
        fakeCredentials("dummyCredentialId");
        final SSHLauncher launcher = new SSHLauncher("HostNotExists", 22, "dummyCredentialId");
        launcher.setSshHostKeyVerificationStrategy(new NonVerifyingKeyVerificationStrategy());
        launcher.setLaunchTimeoutSeconds(5);
        launcher.setRetryWaitTime(1);
        launcher.setMaxNumRetries(3);
        return new DumbSlave("agent", newFolder(temporaryFolder, "junit").getAbsolutePath(), launcher);
    }

    private void fakeCredentials(String id) {
        BasicSSHUserPrivateKey credentials =
                new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, id, "user", null, "", "desc");
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(Domain.global(), Collections.singletonList(credentials));
    }

    private static File newFolder(File root, String... subDirs) {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        assertFalse(!result.exists() && !result.mkdirs(), "Couldn't create folders " + result);
        return result;
    }
}
