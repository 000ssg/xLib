/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
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
package ssg.lib.oauth.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ssg.lib.common.JSON;
import ssg.lib.oauth.OAuthAuthorizationServer;
import ssg.lib.oauth.OAuthClient;

/**
 *
 * @author 000ssg
 */
public class OAuthClientMSO implements OAuthClient {

    public final String authUrl = "https://login.microsoftonline.com/${tenant}/oauth2/v2.0/authorize";
    public final String adminUrl = "https://login.microsoftonline.com/${tenant}/adminconsent";
    public final String tokenUrl = "https://login.microsoftonline.com/${tenant}/oauth2/v2.0/token";
    public final String userInfoUrl = "https://graph.microsoft.com/v1.0/me";

    private static Map<String, String> defaultMSOHeaders = new LinkedHashMap<String, String>() {
        {
            put("Accept", "application/json, text/plain, */*");
            put("Accept-Encoding", "deflate, identity");
            put("Accept-Language", "en-US;en;q=0.9");
        }
    };

    private String clientId;
    private String clientSecret;
    private String scope;

    public OAuthClientMSO() {
    }

    public OAuthClientMSO(
            String clientId,
            String clientSecret,
            String scope
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        if (scope == null) {
            this.scope = "openid";
            for (String s : selectedScope) {
                if (!adminPermissions.contains(s)) {
                    this.scope += "+" + s;
                }
            }
        } else {
            this.scope = scope;
        }
    }

    @Override
    public OAuthContext createContext(final String redirectId) throws IOException {
        OAuthContextMSO r = new OAuthContextMSO(redirectId);
        return r;
    }

    @Override
    public String authorize(String clientId, String redirectId, String scope, String state) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getToken(String code, String redirectId, String client_id) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getToken(String clientId, String redirectId, String scope, String state) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getToken(String username, String password) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getToken(String scope) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getTokenExtension(String extension) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getRefreshedToken(String refresh_token, String scope) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public class OAuthContextMSO extends OAuthContextBase {

        String redirect;
        String tenant;
        String state;
        String nonce;
        OAuthUserInfoMSO userInfo;

        public OAuthContextMSO(String redirect) {
            this.redirect = redirect;
            state = UUID.randomUUID().toString();
            nonce = UUID.randomUUID().toString();
        }

        public OAuthContextMSO(String redirect, String tenant, String state, String nonce) {
            this.redirect = redirect;
            this.tenant = tenant;
            this.state = state;
            this.nonce = nonce;
        }

        @Override
        public String domain() {
            return "microsoft-outlook";
        }

        @Override
        public String title() {
            return "Microsoft Outlook";
        }

        @Override
        public String[] scope() {
            return scope.split("+");
        }

        @Override
        public <A extends OAuthClient> A getOAuth() {
            return (A) OAuthClientMSO.this;
        }

        @Override
        public <U extends OAuthUserInfo> U getOAuthUserInfo() throws IOException {
            if (userInfo == null && accessToken() != null) {
                String s = doGet(new URL(userInfoUrl), defaultMSOHeaders, null);
                JSON.Decoder jsond = new JSON.Decoder();
                Map<String, Object> map = jsond.readObject(s, Map.class);
                /*
{
    "@odata.context": "https://graph.microsoft.com/v1.0/$metadata#users/$entity",
    "businessPhones": [
        "+358505515004"
    ],
    "displayName": "Sidorov Sergey",
    "givenName": "Sergey",
    "jobTitle": "Senior Software Specialist",
    "mail": "Sergey.Sidorov@digia.com",
    "mobilePhone": "+358505515004",
    "officeLocation": "Helsinki-Atomitie",
    "preferredLanguage": null,
    "surname": "Sidorov",
    "userPrincipalName": "sergey.sidorov@digia.com",
    "id": "b1131395-f950-41bb-bd5f-969ab2ad36f7"
}            
                 */
                String id = (String) map.get("id");
                String name = (String) map.get("displayName");
                String email = (String) map.get("mail");
                String image = null;
                userInfo = new OAuthUserInfoMSO(id, name, email, image);
                userInfo.getProperties().putAll(map);
            }
            return (U) userInfo;

        }

        @Override
        public boolean checkAuthData(Map<String, Object> authData) throws IOException {
            if (super.checkAuthData(authData)) {
                String state = (String) authData.get("state");
                if (this.state == null || this.state.equals(state)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public URL getAuthURL() throws IOException {
            if (state == null && nonce == null) {
                return this.getAuthURL(tenant);
            } else {
                return this.getAuthURL(tenant, state, nonce);
            }
        }

        @Override
        public String getAuthToken(String code, Map<String, Object> authData) throws IOException {
            String id_token = (String) authData.get("id_token");
            String state = (String) authData.get("state");
            String type = (String) authData.get("token_type");

            JSON.Decoder jsond = new JSON.Decoder();
            Map m = jsond.readObject(new ByteArrayInputStream(Base64.getUrlDecoder().decode(id_token.split("\\.")[1])), Map.class);

            if (nonce != null && nonce.toString().equals(m.get("nonce"))) {
                tenant = (String) m.get("tid");
            }

            URL url = new URL(tokenUrl.replace("${tenant}", (tenant != null) ? tenant : "common"));

            StringBuilder body = new StringBuilder();
            body.append("grant_type=");
            body.append((type != null) ? type : AT_AUTHORIZE);
            body.append((type != null && AT_REFRESH.equals(type)) ? "&refresh_token=" : "&code=");
            body.append(code);
            body.append("&redirect_uri=");
            body.append(URLEncoder.encode(redirect, "UTF-8"));
            body.append("&client_id=");
            body.append(URLEncoder.encode(clientId, "UTF-8"));
            body.append("&client_secret=");
            body.append(URLEncoder.encode(clientSecret, "UTF-8"));

            Map<String, Object> tt = tokenize(url, body.toString());
            setTokenType((String) tt.get("token_type"));
            setAccessToken((String) tt.get("access_token"));
            setRefreshToken((String) tt.get("refresh_token"));
            setIdToken((String) tt.get("id_token"));
            this.setExpiresAt((Long) tt.get("expires_at"));
            return accessToken();
        }

        @Override
        public boolean revokeAuthToken() throws IOException {
            return false;
        }

        public URL getAuthURL(String tenant) throws IOException {
            URL url = new URL(
                    authUrl.replace("${tenant}", (tenant != null) ? tenant : "common")
                    + "?client_id=" + URLEncoder.encode(clientId, "UTF-8")
                    + "&redirect_uri=" + URLEncoder.encode(redirect, "UTF-8")
                    + "&response_type=" + "code"
                    + "&scope=" + scope
            );
            return url;
        }

        public URL getAuthURL(String tenant, String state, String nonce) throws IOException {
            URL url = new URL(
                    authUrl.replace("${tenant}", (tenant != null) ? tenant : "common")
                    + "?client_id=" + URLEncoder.encode(clientId, "UTF-8")
                    + "&redirect_uri=" + URLEncoder.encode(redirect, "UTF-8")
                    + "&response_type=" + URLEncoder.encode("code id_token", "UTF-8")
                    + "&scope=" + scope
                    + "&state=" + state
                    + "&nonce=" + nonce
                    + "&response_mode=" + "form_post"
            );
            return url;
        }
    }

    ///////////////////// permission sets
    public static List<String> permissions = new ArrayList<String>() {
        {
            for (String s : new String[]{
                "Agreement.Read.All", // A
                "Agreement.ReadWrite.All", // A
                "AgreementAcceptance.Read", // A
                "AgreementAcceptance.Read.All", // A
                "Calendars.Read",
                "Calendars.Read.Shared",
                "Calendars.ReadWrite", // D
                "Calendars.ReadWrite.Shared",
                "Contacts.Read",
                "Contacts.Read.Shared",
                "Contacts.ReadWrite", // D
                "Contacts.ReadWrite.Shared",
                "Files.Read",
                "Files.Read.All",
                "Files.Read.Selected",
                "Files.ReadWrite",
                "Files.ReadWrite.All", // D
                "Files.ReadWrite.AppFolder",
                "Files.ReadWrite.Selected",
                "Mail.Read",
                "Mail.Read.Shared",
                "Mail.ReadWrite", // D
                "Mail.ReadWrite.Shared",
                "Mail.Send",
                "Mail.Sehd.Shared",
                "MailboxSettings.ReadWrite", // D
                "User.Read",
                "User.ReadWrite", // D
                "User.ReadBasic.All", // D
                "Notes.Create",
                "Notes.Read",
                "Notes.Read.All",
                "Notes.ReadWrite",
                "Notes.ReadWrite.All", // D
                "SecurityEvents.Read.All", // A
                "SecurityEvents.ReadWrite.All", // A
                "Sites.Read.All",
                "Sites.ReadWrite.All", // D
                "Sites.Manage.All",
                "Sites.FullControl.All",
                "Tasks.Read",
                "Tasks.Read.Shared",
                "Tasks.ReadWrite", // D
                "Tasks.ReadWrite.Shared",
                "Device.Read",
                "Device.Command",
                "Directory.AccessAsUser.All", // A,D
                "Directory.Read.All", // A
                "Directory.ReadWrite.All", // A,D
                "Group.Read.All", // A
                "Group.ReadWrite.All", // A,D
                "User.Read.All", // A
                "User.ReadWrite.All", // A, D
                "People.Read", // D
                "People.Read.All", // A
                "IdentityRiskEvent.Read.All", // A,D,P
                "DeviceManagementServiceConfig.Read.All", // A,D,P
                "DeviceManagementServiceConfig.ReadWrite.All", // A,D,P
                "DeviceManagementConfiguration.Read.All", // A,D,P
                "DeviceManagementConfiguration.ReadWrite.All", // A,D,P
                "DeviceManagementApps.Read.All", // A,D,P
                "DeviceManagementApps.ReadWrite.All", // A,D,P
                "DeviceManagementRBAC.Read.All", // A,D,P
                "DeviceManagementRBAC.ReadWrite.All", // A,D,P
                "DeviceManagementManagedDevices.Read.All", // A,D,P
                "DeviceManagementManagedDevices.ReadWrite.All", // A,D,P
                "DeviceManagementManagedDevices.PriviledgedOperations.All", // A,D,P
                "Reports.Read.All", // A,D,P
                "IdentityProvider.Read.All", // A,P
                "IdentityProvider.ReadWrite.All", // A,P
                "EduRoster.ReadBasic", // A,P
                "EduAssignments.ReadBasic", // A,P
                "EduAssignments.Read", // A,P
                "EduAssignments.ReadWriteBasic", // A,P
                "EduAssignments.ReadWrite", // A,P
                "EduAdministration.Read", // A,P
                "EduAdministration.ReadWrite", // A,P
                "Bookings.Read.All", // P
                "BookingsAppointment.ReadWrite.All", // P
                "Bookings.ReadWrite.All", // P
                "Bookings.Manage.All", // P
                "UserActivity.ReadWrite.CreatedByApp",
                "Financials.ReadWrite.All",
                "offline_access",
                "profile",}) {
                if (s != null && !s.trim().isEmpty()) {
                    add(s);
                }
            }
        }
    };
    public static Collection<String> adminPermissions = new HashSet<String>() {
        {
            for (String s : new String[]{
                "Agreement.Read.All", // A
                "Agreement.ReadWrite.All", // A
                "AgreementAcceptance.Read", // A
                "AgreementAcceptance.Read.All", // A
                "SecurityEvents.Read.All", // A
                "SecurityEvents.ReadWrite.All", // A
                "Directory.AccessAsUser.All", // A,D
                "Directory.Read.All", // A
                "Directory.ReadWrite.All", // A,D
                "Group.Read.All", // A
                "Group.ReadWrite.All", // A,D
                "User.Read.All", // A
                "User.ReadWrite.All", // A, D
                "People.Read.All", // A
                "IdentityRiskEvent.Read.All", // A,D,P
                "DeviceManagementServiceConfig.Read.All", // A,D,P
                "DeviceManagementServiceConfig.ReadWrite.All", // A,D,P
                "DeviceManagementConfiguration.Read.All", // A,D,P
                "DeviceManagementConfiguration.ReadWrite.All", // A,D,P
                "DeviceManagementApps.Read.All", // A,D,P
                "DeviceManagementApps.ReadWrite.All", // A,D,P
                "DeviceManagementRBAC.Read.All", // A,D,P
                "DeviceManagementRBAC.ReadWrite.All", // A,D,P
                "DeviceManagementManagedDevices.Read.All", // A,D,P
                "DeviceManagementManagedDevices.ReadWrite.All", // A,D,P
                "DeviceManagementManagedDevices.PriviledgedOperations.All", // A,D,P
                "Reports.Read.All", // A,D,P
                "IdentityProvider.Read.All", // A,P
                "IdentityProvider.ReadWrite.All", // A,P
                "EduRoster.ReadBasic", // A,P
                "EduAssignments.ReadBasic", // A,P
                "EduAssignments.Read", // A,P
                "EduAssignments.ReadWriteBasic", // A,P
                "EduAssignments.ReadWrite", // A,P
                "EduAdministration.Read", // A,P
                "EduAdministration.ReadWrite", // A,P
                "Bookings.Read.All", // P
                "BookingsAppointment.ReadWrite.All", // P
                "Bookings.ReadWrite.All", // P
                "Bookings.Manage.All" // P
            }) {
                if (s != null && !s.trim().isEmpty()) {
                    add(s);
                }
            }
        }
    };
    public Collection<String> selectedScope = new ArrayList<String>() {
        {
            for (String s : new String[]{
                "Calendars.Read.Shared", // D
                "Calendars.ReadWrite", // D
                "Contacts.ReadWrite", // D
                //                "Files.ReadWrite.All", // D
                //                "Mail.ReadWrite", // D
                //                "MailboxSettings.ReadWrite", // D
                //                "User.ReadWrite", // D
                "User.ReadBasic.All", // D
                //                "Notes.ReadWrite.All", // D
                //                "Sites.ReadWrite.All", // D
                //                "Tasks.ReadWrite", // D
                "Directory.AccessAsUser.All", // A,D
                "Directory.ReadWrite.All", // A,D
                "Group.ReadWrite.All", // A,D
                "User.ReadWrite.All", // A, D
                //                "People.Read", // D
                "IdentityRiskEvent.Read.All", // A,D,P
                "DeviceManagementServiceConfig.Read.All", // A,D,P
                "DeviceManagementServiceConfig.ReadWrite.All", // A,D,P
                "DeviceManagementConfiguration.Read.All", // A,D,P
                "DeviceManagementConfiguration.ReadWrite.All", // A,D,P
                "DeviceManagementApps.Read.All", // A,D,P
                "DeviceManagementApps.ReadWrite.All", // A,D,P
                "DeviceManagementRBAC.Read.All", // A,D,P
                "DeviceManagementRBAC.ReadWrite.All", // A,D,P
                "DeviceManagementManagedDevices.Read.All", // A,D,P
                "DeviceManagementManagedDevices.ReadWrite.All", // A,D,P
                "DeviceManagementManagedDevices.PriviledgedOperations.All", // A,D,P
                "Reports.Read.All", // A,D,P
                //                "Bookings.Read.All" // P
                //    "User.Read",
                "offline_access", //    "profile"
            }) {
                if (s != null && !s.trim().isEmpty()) {
                    add(s);
                }
            }
        }
    };

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /**
     */
    public class OAuthUserInfoMSO extends OAuthUserInfoBase {

        public OAuthUserInfoMSO(String id, String name, String email, String image) {
            super(id, name, email, image);
        }
    }
}
