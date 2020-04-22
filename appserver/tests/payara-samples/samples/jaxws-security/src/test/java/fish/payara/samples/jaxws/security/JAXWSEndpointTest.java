/*
 *    DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *    Copyright (c) [2020] Payara Foundation and/or its affiliates. All rights reserved.
 *
 *    The contents of this file are subject to the terms of either the GNU
 *    General Public License Version 2 only ("GPL") or the Common Development
 *    and Distribution License("CDDL") (collectively, the "License").  You
 *    may not use this file except in compliance with the License.  You can
 *    obtain a copy of the License at
 *    https://github.com/payara/Payara/blob/master/LICENSE.txt
 *    See the License for the specific
 *    language governing permissions and limitations under the License.
 *
 *    When distributing the software, include this License Header Notice in each
 *    file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *    GPL Classpath Exception:
 *    The Payara Foundation designates this particular file as subject to the "Classpath"
 *    exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *    file that accompanied this code.
 *
 *    Modifications:
 *    If applicable, add the following below the License Header, with the fields
 *    enclosed by brackets [] replaced by your own identifying information:
 *    "Portions Copyright [year] [name of copyright owner]"
 *
 *    Contributor(s):
 *    If you wish your version of this file to be governed by only the CDDL or
 *    only the GPL Version 2, indicate your decision by adding "[Contributor]
 *    elects to include this software in this distribution under the [CDDL or GPL
 *    Version 2] license."  If you don't indicate a single choice of license, a
 *    recipient has the option to distribute your version of this file under
 *    either the CDDL, the GPL Version 2 or to extend the choice of license to
 *    its licensees as provided above.  However, if you add GPL Version 2 code
 *    and therefore, elected the GPL Version 2 license, then the option applies
 *    only if the new code is made subject to such option by the copyright
 *    holder.
 */
package fish.payara.samples.jaxws.security;

import java.net.URL;

import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.BeforeClass;

import fish.payara.samples.PayaraTestShrinkWrap;
import fish.payara.samples.ServerOperations;
import java.io.*;
import java.net.ProtocolException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.hamcrest.CoreMatchers;
import org.junit.*;
import static org.junit.Assert.assertEquals;

public abstract class JAXWSEndpointTest {

    private static final String WEBAPP_SRC_ROOT = "src/main/webapp";

    @ArquillianResource
    protected URL baseUrl;

    protected URL serviceUrl;
    protected final InsecureSSLConfigurator insecureSSLConfigurator = new InsecureSSLConfigurator();

    public static WebArchive createBaseDeployment() {
        return PayaraTestShrinkWrap
                .getWebArchive()
                .addPackage(JAXWSEndpointTest.class.getPackage())
                .addAsWebInfResource(new File(WEBAPP_SRC_ROOT, "WEB-INF/web.xml"))
                .addAsWebInfResource(new File(WEBAPP_SRC_ROOT, "WEB-INF/wsit-fish.payara.samples.jaxws.security.CalculatorService.xml"));

    }

    @BeforeClass
    public static void configureServer() {
        ServerOperations.setupContainerFileIdentityStore("myCustomRealm");
        ServerOperations.addUserToContainerIdentityStore("myCustomRealm", "tester", "calculator");
        ServerOperations.addUserToContainerIdentityStore("myCustomRealm", "testernotallowed", "");
    }


    protected final HttpsURLConnection sendSoapHttpRequest(String requestFile) throws IOException, ProtocolException {
        HttpsURLConnection serviceConnection = (HttpsURLConnection) serviceUrl.openConnection();
        serviceConnection.setRequestProperty("Content-Type", "text/xml");
        serviceConnection.setRequestMethod("POST");
        serviceConnection.setDoInput(true);
        serviceConnection.setDoOutput(true);
        try (InputStream requestStream = this.getClass().getResourceAsStream(requestFile)) {
            pipe(requestStream, serviceConnection.getOutputStream());
        }
        serviceConnection.getOutputStream().close();
        return serviceConnection;
    }

    protected final void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int size;

        while ((size = in.read(buffer)) != -1) {
            out.write(buffer, 0, size);
        }
    }

    protected final String readTextFromInputStream(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
            char[] buffer = new char[1024];
            int size;
            while ((size = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, size);
            }
        }
        return sb.toString();
    }

    protected final void assertResponseOK(HttpsURLConnection serviceConnection) throws IOException {
        try {
            serviceConnection.getInputStream();
        } catch (IOException e) {
            String text = readTextFromInputStream(serviceConnection.getErrorStream());
            System.out.println("Error Response: \n\n" + text);
        }

        assertEquals("HTTP Response Code", 200, serviceConnection.getResponseCode());
    }

    protected final void assertResponseIsAuthFailed(HttpsURLConnection serviceConnection) throws IOException {
        String responseText;
        try {
            responseText = readTextFromInputStream(serviceConnection.getInputStream());
            System.out.println("Unexpected OK Response: \n\n" + responseText);
        } catch (IOException e) {
            responseText = readTextFromInputStream(serviceConnection.getErrorStream());
        }

        Assert.assertTrue("Response code starts with 5", isBetween(serviceConnection.getResponseCode(), 500, 600) );
        Assert.assertThat(responseText, CoreMatchers.containsString("Authentication of Username Password Token Failed"));
    }
    
    protected final void assertResponseIsNotPermitted(HttpsURLConnection serviceConnection) throws IOException {
        String responseText;
        try {
            responseText = readTextFromInputStream(serviceConnection.getInputStream());
            System.out.println("Unexpected OK Response: \n\n" + responseText);
        } catch (IOException e) {
            responseText = readTextFromInputStream(serviceConnection.getErrorStream());
        }

        Assert.assertTrue("Response code starts with 5", isBetween(serviceConnection.getResponseCode(), 500, 600) );
        Assert.assertThat(responseText, CoreMatchers.containsString("Caller was not permitted access"));
    }
    

    private boolean isBetween(int value, int minInclusive, int maxExclusive) {
        boolean ok = minInclusive <= value && value < maxExclusive;
        if (!ok) {
            System.out.println("Value is " + value);
        }
        return ok;
    }

}
