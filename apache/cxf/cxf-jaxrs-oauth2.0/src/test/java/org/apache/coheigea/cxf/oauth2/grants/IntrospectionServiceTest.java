/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.coheigea.cxf.oauth2.grants;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;

import org.apache.coheigea.cxf.oauth2.balanceservice.BankServer;
import org.apache.coheigea.cxf.oauth2.oauthservice.OAuthServer;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.apache.cxf.rs.security.oauth2.common.ClientAccessToken;
import org.apache.cxf.rs.security.oauth2.common.OAuthAuthorizationData;
import org.apache.cxf.rs.security.oauth2.common.TokenIntrospection;
import org.apache.cxf.rs.security.oauth2.provider.OAuthJSONProvider;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.junit.BeforeClass;

/**
 * Some unit tests for the token introspection service in CXF.
 */
public class IntrospectionServiceTest extends AbstractBusClientServerTestBase {
    
    public static final String BANK_PORT = allocatePort(BankServer.class);
    static final String PORT = allocatePort(OAuthServer.class);
    
    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue(
                "Server failed to launch",
                // run the server in the same process
                // set this to false to fork
                launchServer(OAuthServer.class, true)
        );
    }
    
    @org.junit.Test
    public void testTokenIntrospection() throws Exception {
        URL busFile = IntrospectionServiceTest.class.getResource("cxf-client.xml");
        
        String address = "https://localhost:" + PORT + "/services/";
        WebClient client = WebClient.create(address, setupProviders(), "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);
        
        // Get Authorization Code
        String code = getAuthorizationCode(client);
        assertNotNull(code);
        
        // Now get the access token
        client = WebClient.create(address, setupProviders(), "consumer-id", "this-is-a-secret", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);
        
        ClientAccessToken accessToken = getAccessTokenWithAuthorizationCode(client, code);
        assertNotNull(accessToken.getTokenKey());
        
        // Now query the token introspection service
        client = WebClient.create(address, setupProviders(), "consumer-id", "this-is-a-secret", busFile.toString());
        client.accept("application/json").type("application/x-www-form-urlencoded");
        Form form = new Form();
        form.param("token", accessToken.getTokenKey());
        client.path("introspect/");
        Response response = client.post(form);
        
        TokenIntrospection tokenIntrospection = response.readEntity(TokenIntrospection.class);
        assertEquals(tokenIntrospection.isActive(), true);
        assertEquals(tokenIntrospection.getUsername(), "alice");
        assertEquals(tokenIntrospection.getClientId(), "consumer-id");
        assertEquals(tokenIntrospection.getScope(), accessToken.getApprovedScope());
        Long validity = tokenIntrospection.getExp() - tokenIntrospection.getIat();
        assertTrue(validity == accessToken.getExpiresIn());
    }
    
    @org.junit.Test
    public void testTokenIntrospectionWithAudience() throws Exception {
        URL busFile = AuthorizationGrantTest.class.getResource("cxf-client.xml");
        
        String address = "https://localhost:" + PORT + "/services/";
        WebClient client = WebClient.create(address, setupProviders(), "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);
        
        // Get Authorization Code
        String code = getAuthorizationCode(client, null, "consumer-id-aud");
        assertNotNull(code);
        
        // Now get the access token
        client = WebClient.create(address, setupProviders(), "consumer-id-aud", "this-is-a-secret", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);
        
        String audience = "https://localhost:" + BANK_PORT + "/bankservice/partners/balance";
        ClientAccessToken accessToken = 
            getAccessTokenWithAuthorizationCode(client, code, "consumer-id-aud", audience);
        assertNotNull(accessToken.getTokenKey());
        
        // Now query the token introspection service
        client = WebClient.create(address, setupProviders(), "consumer-id", "this-is-a-secret", busFile.toString());
        client.accept("application/json").type("application/x-www-form-urlencoded");
        Form form = new Form();
        form.param("token", accessToken.getTokenKey());
        client.path("introspect/");
        Response response = client.post(form);
        
        TokenIntrospection tokenIntrospection = response.readEntity(TokenIntrospection.class);
        assertEquals(tokenIntrospection.isActive(), true);
        assertEquals(tokenIntrospection.getUsername(), "alice");
        assertEquals(tokenIntrospection.getClientId(), "consumer-id-aud");
        assertEquals(tokenIntrospection.getScope(), accessToken.getApprovedScope());
        Long validity = tokenIntrospection.getExp() - tokenIntrospection.getIat();
        assertTrue(validity == accessToken.getExpiresIn());
        assertEquals(tokenIntrospection.getAud().get(0), audience);
    }
    
    @org.junit.Test
    public void testRefreshedToken() throws Exception {
        URL busFile = AuthorizationGrantTest.class.getResource("cxf-client.xml");
        
        String address = "https://localhost:" + PORT + "/services/";
        WebClient client = WebClient.create(address, setupProviders(), "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);
        
        // Get Authorization Code
        String code = getAuthorizationCode(client);
        assertNotNull(code);
        
        // Now get the access token
        client = WebClient.create(address, setupProviders(), "consumer-id", "this-is-a-secret", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);
        
        ClientAccessToken accessToken = getAccessTokenWithAuthorizationCode(client, code);
        assertNotNull(accessToken.getTokenKey());
        assertNotNull(accessToken.getRefreshToken());
        String originalAccessToken = accessToken.getTokenKey();
        
        // Refresh the access token
        client.type("application/x-www-form-urlencoded").accept("application/json");
        
        Form form = new Form();
        form.param("grant_type", "refresh_token");
        form.param("refresh_token", accessToken.getRefreshToken());
        form.param("client_id", "consumer-id");
        Response response = client.post(form);
        
        accessToken = response.readEntity(ClientAccessToken.class);
        assertNotNull(accessToken.getTokenKey());
        assertNotNull(accessToken.getRefreshToken());
        
        // Now query the token introspection service
        client = WebClient.create(address, setupProviders(), "consumer-id", "this-is-a-secret", busFile.toString());
        client.accept("application/json").type("application/x-www-form-urlencoded");
        
        // Refreshed token should be ok
        form = new Form();
        form.param("token", accessToken.getTokenKey());
        client.path("introspect/");
        response = client.post(form);
        
        TokenIntrospection tokenIntrospection = response.readEntity(TokenIntrospection.class);
        assertEquals(tokenIntrospection.isActive(), true);
        
        // Original token should not be ok
        form = new Form();
        form.param("token", originalAccessToken);
        response = client.post(form);
        
        tokenIntrospection = response.readEntity(TokenIntrospection.class);
        assertEquals(tokenIntrospection.isActive(), false);
    }
    
    @org.junit.Test
    public void testTokenIntrospectionWithScope() throws Exception {
        URL busFile = IntrospectionServiceTest.class.getResource("cxf-client.xml");
        
        String address = "https://localhost:" + PORT + "/services/";
        WebClient client = WebClient.create(address, setupProviders(), "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);
        
        // Get Authorization Code
        String code = getAuthorizationCode(client, "read_balance");
        assertNotNull(code);
        
        // Now get the access token
        client = WebClient.create(address, setupProviders(), "consumer-id", "this-is-a-secret", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);
        
        ClientAccessToken accessToken = getAccessTokenWithAuthorizationCode(client, code);
        assertNotNull(accessToken.getTokenKey());
        assertTrue(accessToken.getApprovedScope().contains("read_balance"));
        
        // Now query the token introspection service
        client = WebClient.create(address, setupProviders(), "consumer-id", "this-is-a-secret", busFile.toString());
        client.accept("application/json").type("application/x-www-form-urlencoded");
        Form form = new Form();
        form.param("token", accessToken.getTokenKey());
        client.path("introspect/");
        Response response = client.post(form);
        
        TokenIntrospection tokenIntrospection = response.readEntity(TokenIntrospection.class);
        assertEquals(tokenIntrospection.isActive(), true);
        assertEquals(tokenIntrospection.getUsername(), "alice");
        assertEquals(tokenIntrospection.getClientId(), "consumer-id");
        assertEquals(tokenIntrospection.getScope(), accessToken.getApprovedScope());
        Long validity = tokenIntrospection.getExp() - tokenIntrospection.getIat();
        assertTrue(validity == accessToken.getExpiresIn());
    }
    
    private String getAuthorizationCode(WebClient client) {
        return getAuthorizationCode(client, null);
    }
    
    private String getAuthorizationCode(WebClient client, String scope) {
        return getAuthorizationCode(client, scope, "consumer-id");
    }
    
    private String getAuthorizationCode(WebClient client, String scope, String consumerId) {
        // Make initial authorization request
        client.type("application/json").accept("application/json");
        client.query("client_id", consumerId);
        client.query("redirect_uri", "http://www.blah.apache.org");
        client.query("response_type", "code");
        if (scope != null) {
            client.query("scope", scope);
        }
        client.path("authorize/");
        Response response = client.get();
        
        OAuthAuthorizationData authzData = response.readEntity(OAuthAuthorizationData.class);
        
        // Now call "decision" to get the authorization code grant
        client.path("decision");
        client.type("application/x-www-form-urlencoded");
        
        Form form = new Form();
        form.param("session_authenticity_token", authzData.getAuthenticityToken());
        form.param("client_id", authzData.getClientId());
        form.param("redirect_uri", authzData.getRedirectUri());
        if (authzData.getProposedScope() != null) {
            form.param("scope", authzData.getProposedScope());
        }
        form.param("oauthDecision", "allow");
        
        response = client.post(form);
        String location = response.getHeaderString("Location"); 
        return getSubstring(location, "code");
    }
    
    private String getSubstring(String parentString, String substringName) {
        String foundString = 
            parentString.substring(parentString.indexOf(substringName + "=") + (substringName + "=").length());
        int ampersandIndex = foundString.indexOf('&');
        if (ampersandIndex < 1) {
            ampersandIndex = foundString.length();
        }
        return foundString.substring(0, ampersandIndex);
    }
    
    private ClientAccessToken getAccessTokenWithAuthorizationCode(WebClient client, String code) {
        return getAccessTokenWithAuthorizationCode(client, code, "consumer-id", null);
    }
    
    private ClientAccessToken getAccessTokenWithAuthorizationCode(WebClient client, 
                                                                  String code,
                                                                  String consumerId,
                                                                  String audience) {
        client.type("application/x-www-form-urlencoded").accept("application/json");
        client.path("token");
        
        Form form = new Form();
        form.param("grant_type", "authorization_code");
        form.param("code", code);
        form.param("client_id", consumerId);
        if (audience != null) {
            form.param("audience", audience);
        }
        Response response = client.post(form);
        
        return response.readEntity(ClientAccessToken.class);
    }
    
    private static List<Object> setupProviders() {
        List<Object> providers = new ArrayList<Object>();
        JSONProvider<OAuthAuthorizationData> jsonP = new JSONProvider<OAuthAuthorizationData>();
        jsonP.setNamespaceMap(Collections.singletonMap("http://org.apache.cxf.rs.security.oauth",
                                                       "ns2"));
        providers.add(jsonP);
        providers.add(new OAuthJSONProvider());
        
        return providers;
    }
}
