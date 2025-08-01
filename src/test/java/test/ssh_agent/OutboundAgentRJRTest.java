/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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

package test.ssh_agent;

import hudson.model.Slave;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RealJenkinsRule;

public final class OutboundAgentRJRTest {

    @Rule
    public final RealJenkinsRule rr = new RealJenkinsRule();

    @Test
    public void smokes() throws Throwable {
        rr.startJenkins();
        try (var outbountAgent = new OutboundAgent()) {
            rr.runRemotely(OutboundAgent::createAgent, "remote", outbountAgent.start());
            rr.run(r -> {
                var agent = (Slave) r.jenkins.getNode("remote");
                r.waitOnline(agent);
                System.err.println(
                        "Running in " + agent.toComputer().getEnvironment().get("PWD"));
            });
        }
    }
}
