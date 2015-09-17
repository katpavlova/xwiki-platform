/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.test.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.EntityType;
import org.xwiki.model.internal.reference.RelativeStringEntityReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rest.model.jaxb.ObjectFactory;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.rest.model.jaxb.Xwiki;
import org.xwiki.rest.resources.attachments.AttachmentResource;
import org.xwiki.rest.resources.objects.ObjectPropertyResource;
import org.xwiki.rest.resources.objects.ObjectResource;
import org.xwiki.rest.resources.objects.ObjectsResource;
import org.xwiki.rest.resources.pages.PageResource;
import org.xwiki.test.integration.XWikiExecutor;
import org.xwiki.test.ui.po.ViewPage;
import org.xwiki.test.ui.po.editor.ClassEditPage;
import org.xwiki.test.ui.po.editor.ObjectEditPage;

/**
 * Helper methods for testing, not related to a specific Page Object. Also made available to tests classes.
 *
 * @version $Id$
 * @since 3.2M3
 */
public class TestUtils
{
    /**
     * @since 5.0M2
     */
    public static final UsernamePasswordCredentials ADMIN_CREDENTIALS = new UsernamePasswordCredentials("Admin",
        "admin");

    /**
     * @since 5.1M1
     */
    public static final UsernamePasswordCredentials SUPER_ADMIN_CREDENTIALS = new UsernamePasswordCredentials(
        "superadmin", "pass");

    /**
     * @since 5.0M2
     * @deprecated since 7.3M1, use {@link #getBaseURL()} instead
     */
    @Deprecated
    public static final String BASE_URL = XWikiExecutor.URL + ":" + XWikiExecutor.DEFAULT_PORT + "/xwiki/";

    /**
     * @since 5.0M2
     * @deprecated since 7.3M1, use {@link #getBaseBinURL()} instead
     */
    @Deprecated
    public static final String BASE_BIN_URL = BASE_URL + "bin/";

    /**
     * @since 5.0M2
     * @deprecated since 7.3M1, use {@link #getBaseRestURL()} instead
     */
    @Deprecated
    public static final String BASE_REST_URL = BASE_URL + "rest/";

    /**
     * @since 7.3M1
     */
    private static final EntityReferenceResolver<String> RELATIVE_RESOLVER =
        new RelativeStringEntityReferenceResolver();

    /**
     * @since 7.3M1
     */
    private static final int[] STATUS_OKNOTFOUND = new int[] {Status.OK.getStatusCode(),
        Status.NOT_FOUND.getStatusCode()};

    /**
     * @since 7.3M1
     */
    private static final int[] STATUS_OK = new int[] {Status.OK.getStatusCode()};

    private static PersistentTestContext context;

    private static ComponentManager componentManager;

    private static EntityReferenceResolver<String> referenceResolver;

    private static EntityReferenceSerializer<String> referenceSerializer;

    /**
     * Used to convert Java object into its REST XML representation.
     */
    private static Marshaller marshaller;

    /**
     * Used to convert REST request XML result into its Java representation.
     */
    private static Unmarshaller unmarshaller;

    /**
     * Used to create REST Java resources.
     */
    private static ObjectFactory objectFactory;

    {
        {
            try {
                // Initialize REST related tools
                JAXBContext context =
                    JAXBContext.newInstance("org.xwiki.rest.model.jaxb"
                        + ":org.xwiki.extension.repository.xwiki.model.jaxb");
                marshaller = context.createMarshaller();
                unmarshaller = context.createUnmarshaller();
                objectFactory = new ObjectFactory();
            } catch (JAXBException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Cached secret token. TODO cache for each user. */
    private String secretToken = null;

    private HttpClient httpClient;

    /**
     * @since 7.3M1
     */
    private XWikiExecutor executor;

    /**
     * @since 7.3M1
     */
    private String currentWiki = "xwiki";

    private RestTestUtils rest = new RestTestUtils();

    public TestUtils()
    {
        this.httpClient = new HttpClient();
        this.httpClient.getState().setCredentials(AuthScope.ANY, SUPER_ADMIN_CREDENTIALS);
        this.httpClient.getParams().setAuthenticationPreemptive(true);
    }

    /**
     * @since 7.3M1
     */
    public XWikiExecutor getExecutor()
    {
        return this.executor;
    }

    /**
     * @since 7.3M1
     */
    public void setExecutor(XWikiExecutor executor)
    {
        this.executor = executor;
    }

    /** Used so that AllTests can set the persistent test context. */
    public static void setContext(PersistentTestContext context)
    {
        TestUtils.context = context;
    }

    public static void initializeComponent(ComponentManager componentManager) throws Exception
    {
        TestUtils.componentManager = componentManager;
        TestUtils.referenceResolver = TestUtils.componentManager.getInstance(EntityReferenceResolver.TYPE_STRING);
        TestUtils.referenceSerializer = TestUtils.componentManager.getInstance(EntityReferenceSerializer.TYPE_STRING);
    }

    protected XWikiWebDriver getDriver()
    {
        return context.getDriver();
    }

    public Session getSession()
    {
        return this.new Session(getDriver().manage().getCookies(), getSecretToken());
    }

    public void setSession(Session session)
    {
        WebDriver.Options options = getDriver().manage();
        options.deleteAllCookies();
        if (session != null) {
            for (Cookie cookie : session.getCookies()) {
                options.addCookie(cookie);
            }
        }
        if (session != null && !StringUtils.isEmpty(session.getSecretToken())) {
            this.secretToken = session.getSecretToken();
        } else {
            recacheSecretToken();
        }
    }

    /**
     * @since 7.0RC1
     */
    public void setDefaultCredentials(String username, String password)
    {
        this.httpClient.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
    }

    /**
     * @since 7.0RC1
     */
    public void setDefaultCredentials(UsernamePasswordCredentials defaultCredentials)
    {
        this.httpClient.getState().setCredentials(AuthScope.ANY, defaultCredentials);
    }

    public UsernamePasswordCredentials getDefaultCredentials()
    {
        return (UsernamePasswordCredentials) this.httpClient.getState().getCredentials(AuthScope.ANY);
    }

    public void loginAsSuperAdmin()
    {
        login(SUPER_ADMIN_CREDENTIALS.getUserName(), SUPER_ADMIN_CREDENTIALS.getPassword());
    }

    public void loginAsSuperAdminAndGotoPage(String pageURL)
    {
        loginAndGotoPage(SUPER_ADMIN_CREDENTIALS.getUserName(), SUPER_ADMIN_CREDENTIALS.getPassword(), pageURL);
    }

    public void loginAsAdmin()
    {
        login(ADMIN_CREDENTIALS.getUserName(), ADMIN_CREDENTIALS.getPassword());
    }

    public void loginAsAdminAndGotoPage(String pageURL)
    {
        loginAndGotoPage(ADMIN_CREDENTIALS.getUserName(), ADMIN_CREDENTIALS.getPassword(), pageURL);
    }

    public void login(String username, String password)
    {
        loginAndGotoPage(username, password, null);
    }

    public void loginAndGotoPage(String username, String password, String pageURL)
    {
        if (!username.equals(getLoggedInUserName())) {
            // Log in and direct to a non existent page so that it loads very fast and we don't incur the time cost of
            // going to the home page for example.
            // Also recache the CSRF token
            getDriver().get(getURLToLoginAndGotoPage(username, password, getURL("XWiki", "Register", "register")));
            recacheSecretTokenWhenOnRegisterPage();
            if (pageURL != null) {
                // Go to the page asked
                getDriver().get(pageURL);
            } else {
                getDriver().get(getURLToNonExistentPage());
            }

            setDefaultCredentials(username, password);
        }
    }

    /**
     * Consider using setSession(null) because it will drop the cookies which is faster than invoking a logout action.
     */
    public String getURLToLogout()
    {
        return getURL("XWiki", "XWikiLogin", "logout");
    }

    public String getURLToLoginAsAdmin()
    {
        return getURLToLoginAs(ADMIN_CREDENTIALS.getUserName(), ADMIN_CREDENTIALS.getPassword());
    }

    public String getURLToLoginAsSuperAdmin()
    {
        return getURLToLoginAs(SUPER_ADMIN_CREDENTIALS.getUserName(), SUPER_ADMIN_CREDENTIALS.getPassword());
    }

    public String getURLToLoginAs(final String username, final String password)
    {
        return getURLToLoginAndGotoPage(username, password, null);
    }

    /**
     * @param pageURL the URL of the page to go to after logging in.
     * @return URL to accomplish login and goto.
     */
    public String getURLToLoginAsAdminAndGotoPage(final String pageURL)
    {
        return getURLToLoginAndGotoPage(ADMIN_CREDENTIALS.getUserName(), ADMIN_CREDENTIALS.getPassword(), pageURL);
    }

    /**
     * @param pageURL the URL of the page to go to after logging in.
     * @return URL to accomplish login and goto.
     */
    public String getURLToLoginAsSuperAdminAndGotoPage(final String pageURL)
    {
        return getURLToLoginAndGotoPage(SUPER_ADMIN_CREDENTIALS.getUserName(), SUPER_ADMIN_CREDENTIALS.getPassword(),
            pageURL);
    }

    /**
     * @param username the name of the user to log in as.
     * @param password the password for the user to log in.
     * @param pageURL the URL of the page to go to after logging in.
     * @return URL to accomplish login and goto.
     */
    public String getURLToLoginAndGotoPage(final String username, final String password, final String pageURL)
    {
        Map<String, String> parameters = new HashMap<String, String>()
        {
            {
                put("j_username", username);
                put("j_password", password);
                if (pageURL != null && pageURL.length() > 0) {
                    put("xredirect", pageURL);
                }
            }
        };
        return getURL("XWiki", "XWikiLogin", "loginsubmit", parameters);
    }

    /**
     * @return URL to a non existent page that loads very fast (we are using plain mode so that we don't even have to
     *         display the skin ;))
     */
    public String getURLToNonExistentPage()
    {
        return getURL("NonExistentSpace", "NonExistentPage", "view", "xpage=plain");
    }

    /**
     * After successful completion of this function, you are guaranteed to be logged in as the given user and on the
     * page passed in pageURL.
     */
    public void assertOnPage(final String pageURL)
    {
        final String pageURI = pageURL.replaceAll("\\?.*", "");
        getDriver().waitUntilCondition(new ExpectedCondition<Boolean>()
        {
            @Override
            public Boolean apply(WebDriver driver)
            {
                return getDriver().getCurrentUrl().contains(pageURI);
            }
        });
    }

    public String getLoggedInUserName()
    {
        By userAvatar = By.xpath("//div[@id='xwikimainmenu']//li[contains(@class, 'navbar-avatar')]/a");
        if (!getDriver().hasElementWithoutWaiting(userAvatar)) {
            // Guest
            return null;
        }

        WebElement element = getDriver().findElementWithoutWaiting(userAvatar);
        String href = element.getAttribute("href");
        String loggedInUserName = href.substring(href.lastIndexOf("/") + 1);

        // Return
        return loggedInUserName;
    }

    public void createUserAndLogin(final String username, final String password, Object... properties)
    {
        createUserAndLoginWithRedirect(username, password, getURLToNonExistentPage(), properties);
    }

    public void createUserAndLoginWithRedirect(final String username, final String password, String url,
        Object... properties)
    {
        createUser(username, password, getURLToLoginAndGotoPage(username, password, url), properties);

        setDefaultCredentials(username, password);
    }

    public void createUser(final String username, final String password, String redirectURL, Object... properties)
    {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("register", "1");
        parameters.put("xwikiname", username);
        parameters.put("register_password", password);
        parameters.put("register2_password", password);
        parameters.put("register_email", "");
        parameters.put("xredirect", redirectURL);
        parameters.put("form_token", getSecretToken());
        getDriver().get(getURL("XWiki", "Register", "register", parameters));
        recacheSecretToken();
        if (properties.length > 0) {
            updateObject("XWiki", username, "XWiki.XWikiUsers", 0, properties);
        }
    }

    public ViewPage gotoPage(String space, String page)
    {
        gotoPage(space, page, "view");
        return new ViewPage();
    }

    /**
     * @since 7.2M2
     */
    public ViewPage gotoPage(EntityReference reference)
    {
        gotoPage(reference, "view");
        return new ViewPage();
    }

    public void gotoPage(String space, String page, String action)
    {
        gotoPage(space, page, action, "");
    }

    /**
     * @since 7.2M2
     */
    public void gotoPage(EntityReference reference, String action)
    {
        gotoPage(reference, action, "");
    }

    /**
     * @since 3.5M1
     */
    public void gotoPage(String space, String page, String action, Object... queryParameters)
    {
        gotoPage(space, page, action, toQueryString(queryParameters));
    }

    public void gotoPage(String space, String page, String action, Map<String, ?> queryParameters)
    {
        gotoPage(Collections.singletonList(space), page, action, queryParameters);
    }

    /**
     * @since 7.2M2
     */
    public void gotoPage(List<String> spaces, String page, String action, Map<String, ?> queryParameters)
    {
        gotoPage(spaces, page, action, toQueryString(queryParameters));
    }

    /**
     * @since 7.2M2
     */
    public void gotoPage(EntityReference reference, String action, Map<String, ?> queryParameters)
    {
        gotoPage(reference, action, toQueryString(queryParameters));
    }

    public void gotoPage(String space, String page, String action, String queryString)
    {
        gotoPage(Collections.singletonList(space), page, action, queryString);
    }

    /**
     * @since 7.2M2
     */
    public void gotoPage(List<String> spaces, String page, String action, String queryString)
    {
        gotoPage(getURL(spaces, page, action, queryString));
    }

    /**
     * @since 7.2M2
     */
    public void gotoPage(EntityReference reference, String action, String queryString)
    {
        gotoPage(getURL(reference, action, queryString));

        // Update current wiki
        EntityReference wikiReference = reference.extractReference(EntityType.WIKI);
        if (wikiReference != null) {
            this.currentWiki = wikiReference.getName();
        }
    }

    public void gotoPage(String url)
    {
        // Only navigate if the current URL is different from the one to go to, in order to improve performances.
        if (!getDriver().getCurrentUrl().equals(url)) {
            getDriver().get(url);
        }
    }

    public String getURLToDeletePage(String space, String page)
    {
        return getURL(space, page, "delete", "confirm=1");
    }

    /**
     * @since 7.2M2
     */
    public String getURLToDeletePage(EntityReference reference)
    {
        return getURL(reference, "delete", "confirm=1");
    }

    /**
     * @param space the name of the space to delete
     * @return the URL that can be used to delete the specified pace
     * @since 4.5
     */
    public String getURLToDeleteSpace(String space)
    {
        return getURL(space, "WebHome", "deletespace", "confirm=1");
    }

    public ViewPage createPage(String space, String page, String content, String title)
    {
        return createPage(Collections.singletonList(space), page, content, title);
    }

    /**
     * @since 7.2M2
     */
    public ViewPage createPage(EntityReference reference, String content, String title)
    {
        return createPage(reference, content, title, null);
    }

    /**
     * @since 7.2M2
     */
    public ViewPage createPage(List<String> spaces, String page, String content, String title)
    {
        return createPage(spaces, page, content, title, null);
    }

    public ViewPage createPage(String space, String page, String content, String title, String syntaxId)
    {
        return createPage(Collections.singletonList(space), page, content, title, syntaxId);
    }

    /**
     * @since 7.2M2
     */
    public ViewPage createPage(EntityReference reference, String content, String title, String syntaxId)
    {
        return createPage(reference, content, title, syntaxId, null);
    }

    /**
     * @since 7.2M2
     */
    public ViewPage createPage(List<String> spaces, String page, String content, String title, String syntaxId)
    {
        return createPage(spaces, page, content, title, syntaxId, null);
    }

    public ViewPage createPage(String space, String page, String content, String title, String syntaxId,
        String parentFullPageName)
    {
        return createPage(Collections.singletonList(space), page, content, title, syntaxId, parentFullPageName);
    }

    /**
     * @since 7.2M2
     */
    public ViewPage createPage(List<String> spaces, String page, String content, String title, String syntaxId,
        String parentFullPageName)
    {
        Map<String, String> queryMap = new HashMap<String, String>();
        if (content != null) {
            queryMap.put("content", content);
        }
        if (title != null) {
            queryMap.put("title", title);
        }
        if (syntaxId != null) {
            queryMap.put("syntaxId", syntaxId);
        }
        if (parentFullPageName != null) {
            queryMap.put("parent", parentFullPageName);
        }
        gotoPage(spaces, page, "save", queryMap);
        return new ViewPage();
    }

    /**
     * @since 7.2M2
     */
    public ViewPage createPage(EntityReference reference, String content, String title, String syntaxId,
        String parentFullPageName)
    {
        Map<String, String> queryMap = new HashMap<>();
        if (content != null) {
            queryMap.put("content", content);
        }
        if (title != null) {
            queryMap.put("title", title);
        }
        if (syntaxId != null) {
            queryMap.put("syntaxId", syntaxId);
        }
        if (parentFullPageName != null) {
            queryMap.put("parent", parentFullPageName);
        }
        gotoPage(reference, "save", queryMap);
        return new ViewPage();
    }

    /**
     * @since 5.1M2
     */
    public ViewPage createPageWithAttachment(String space, String page, String content, String title, String syntaxId,
        String parentFullPageName, String attachmentName, InputStream attachmentData) throws Exception
    {
        return createPageWithAttachment(space, page, content, title, syntaxId, parentFullPageName, attachmentName,
            attachmentData, null);
    }

    /**
     * @since 5.1M2
     */
    public ViewPage createPageWithAttachment(String space, String page, String content, String title, String syntaxId,
        String parentFullPageName, String attachmentName, InputStream attachmentData,
        UsernamePasswordCredentials credentials) throws Exception
    {
        return createPageWithAttachment(Collections.singletonList(space), page, content, title, syntaxId,
            parentFullPageName, attachmentName, attachmentData, credentials);
    }

    /**
     * @since 7.2M2
     */
    public ViewPage createPageWithAttachment(List<String> spaces, String page, String content, String title,
        String syntaxId, String parentFullPageName, String attachmentName, InputStream attachmentData,
        UsernamePasswordCredentials credentials) throws Exception
    {
        ViewPage vp = createPage(spaces, page, content, title, syntaxId, parentFullPageName);
        attachFile(spaces, page, attachmentName, attachmentData, false, credentials);
        return vp;
    }

    /**
     * @since 5.1M2
     */
    public ViewPage createPageWithAttachment(String space, String page, String content, String title,
        String attachmentName, InputStream attachmentData) throws Exception
    {
        return createPageWithAttachment(space, page, content, title, null, null, attachmentName, attachmentData);
    }

    /**
     * @since 5.1M2
     */
    public ViewPage createPageWithAttachment(String space, String page, String content, String title,
        String attachmentName, InputStream attachmentData, UsernamePasswordCredentials credentials) throws Exception
    {
        ViewPage vp = createPage(space, page, content, title);
        attachFile(space, page, attachmentName, attachmentData, false, credentials);
        return vp;
    }

    public void deletePage(String space, String page)
    {
        getDriver().get(getURLToDeletePage(space, page));
    }

    /**
     * @since 7.2M2
     */
    public void deletePage(EntityReference reference)
    {
        getDriver().get(getURLToDeletePage(reference));
    }

    /**
     * @since 7.2M2
     */
    public EntityReference resolveDocumentReference(String referenceAsString)
    {
        return referenceResolver.resolve(referenceAsString, EntityType.DOCUMENT);
    }

    /**
     * @since 7.2M3
     */
    public EntityReference resolveSpaceReference(String referenceAsString)
    {
        return referenceResolver.resolve(referenceAsString, EntityType.SPACE);
    }

    /**
     * @since 7.2RC1
     */
    public String serializeReference(EntityReference reference)
    {
        return referenceSerializer.serialize(reference);
    }

    /**
     * Accesses the URL to delete the specified space.
     *
     * @param space the name of the space to delete
     * @since 4.5
     */
    public void deleteSpace(String space)
    {
        getDriver().get(getURLToDeleteSpace(space));
    }

    public boolean pageExists(String space, String page)
    {
        return pageExists(Collections.singletonList(space), page);
    }

    /**
     * @since 7.2M2
     */
    public boolean pageExists(List<String> spaces, String page)
    {
        boolean exists;
        try {
            executeGet(getURL(spaces, page, "view", null), Status.OK.getStatusCode());
            exists = true;
        } catch (Exception e) {
            exists = false;
        }

        return exists;
    }

    /**
     * Get the URL to view a page.
     *
     * @param space the space in which the page resides.
     * @param page the name of the page.
     */
    public String getURL(String space, String page)
    {
        return getURL(space, page, "view");
    }

    /**
     * Get the URL of an action on a page.
     *
     * @param space the space in which the page resides.
     * @param page the name of the page.
     * @param action the action to do on the page.
     */
    public String getURL(String space, String page, String action)
    {
        return getURL(space, page, action, "");
    }

    /**
     * Get the URL of an action on a page with a specified query string.
     *
     * @param space the space in which the page resides.
     * @param page the name of the page.
     * @param action the action to do on the page.
     * @param queryString the query string to pass in the URL.
     */
    public String getURL(String space, String page, String action, String queryString)
    {
        return getURL(action, new String[] {space, page}, queryString);
    }

    /**
     * @since 7.2M2
     */
    public String getURL(List<String> spaces, String page, String action, String queryString)
    {
        List<String> path = new ArrayList<>(spaces);
        path.add(page);
        return getURL(action, path.toArray(new String[] {}), queryString);
    }

    /**
     * @since 7.2M2
     */
    public String getURL(EntityReference reference, String action, String queryString)
    {
        return getURL(action, extractListFromReference(reference).toArray(new String[] {}), queryString);
    }

    /**
     * @since 7.2M2
     */
    public String getURLFragment(EntityReference reference)
    {
        return StringUtils.join(extractListFromReference(reference), "/");
    }

    private List<String> extractListFromReference(EntityReference reference)
    {
        List<String> path = new ArrayList<>();
        // Add the spaces
        EntityReference spaceReference = reference.extractReference(EntityType.SPACE);
        EntityReference wikiReference = reference.extractReference(EntityType.WIKI);
        for (EntityReference singleReference : spaceReference.removeParent(wikiReference).getReversedReferenceChain()) {
            path.add(singleReference.getName());
        }
        if (reference.getType() == EntityType.DOCUMENT) {
            path.add(reference.getName());
        }
        return path;
    }

    /**
     * @since 7.3M1
     */
    public String getCurrentWiki()
    {
        return this.currentWiki;
    }

    /**
     * @since 7.3M1
     */
    public String getBaseURL()
    {
        return XWikiExecutor.URL + ":" + (this.executor != null ? this.executor.getPort() : XWikiExecutor.DEFAULT_PORT)
            + "/xwiki/";
    }

    /**
     * @since 7.3M1
     */
    public String getBaseBinURL()
    {
        return getBaseURL() + "bin/";
    }

    /**
     * @since 7.2M1
     */
    public String getURL(String action, String[] path, String queryString)
    {
        StringBuilder builder = new StringBuilder(getBaseBinURL());

        if (!StringUtils.isEmpty(action)) {
            builder.append(action).append('/');
        }
        List<String> escapedPath = new ArrayList<>();
        for (String element : path) {
            escapedPath.add(escapeURL(element));
        }
        builder.append(StringUtils.join(escapedPath, '/'));

        boolean needToAddSecretToken = !Arrays.asList("view", "register", "download").contains(action);
        if (needToAddSecretToken || !StringUtils.isEmpty(queryString)) {
            builder.append('?');
        }
        if (needToAddSecretToken) {
            addQueryStringEntry(builder, "form_token", getSecretToken());
            builder.append('&');
        }
        if (!StringUtils.isEmpty(queryString)) {
            builder.append(queryString);
        }

        return builder.toString();
    }

    /**
     * Get the URL of an action on a page with specified parameters. If you need to pass multiple parameters with the
     * same key, this function will not work.
     *
     * @param space the space in which the page resides.
     * @param page the name of the page.
     * @param action the action to do on the page.
     * @param queryParameters the parameters to pass in the URL, these will be automatically URL encoded.
     */
    public String getURL(String space, String page, String action, Map<String, ?> queryParameters)
    {
        return getURL(space, page, action, toQueryString(queryParameters));
    }

    /**
     * @param space the name of the space that contains the page with the specified attachment
     * @param page the name of the page that holds the attachment
     * @param attachment the attachment name
     * @param action the action to perform on the attachment
     * @param queryString the URL query string
     * @return the URL that performs the specified action on the specified attachment
     */
    public String getAttachmentURL(String space, String page, String attachment, String action, String queryString)
    {
        return getURL(action, new String[] {space, page, attachment}, queryString);
    }

    /**
     * @param space the name of the space that contains the page with the specified attachment
     * @param page the name of the page that holds the attachment
     * @param attachment the attachment name
     * @param action the action to perform on the attachment
     * @return the URL that performs the specified action on the specified attachment
     */
    public String getAttachmentURL(String space, String page, String attachment, String action)
    {
        return getAttachmentURL(space, page, attachment, action, "");
    }

    /**
     * @param space the name of the space that contains the page with the specified attachment
     * @param page the name of the page that holds the attachment
     * @param attachment the attachment name
     * @return the URL to download the specified attachment
     */
    public String getAttachmentURL(String space, String page, String attachment)
    {
        return getAttachmentURL(space, page, attachment, "download");
    }

    /**
     * (Re)-cache the secret token used for CSRF protection. A user with edit rights on Main.WebHome must be logged in.
     * This method must be called before {@link #getSecretToken()} is called and after each re-login.
     *
     * @see #getSecretToken()
     */
    public void recacheSecretToken()
    {
        // Save the current URL to be able to get back after we cache the secret token. We're not using the browser's
        // Back button because if the current page is the result of a POST request then by going back we are re-sending
        // the POST data which can have unexpected results. Moreover, some browsers pop up a modal confirmation box
        // which blocks the test.
        String previousURL = getDriver().getCurrentUrl();
        // Go to the registration page because the registration form uses secret token.
        gotoPage(getCurrentWiki(), "Register", "register");
        recacheSecretTokenWhenOnRegisterPage();
        // Return to the previous page.
        getDriver().get(previousURL);
    }

    private void recacheSecretTokenWhenOnRegisterPage()
    {
        try {
            WebElement tokenInput = getDriver().findElement(By.xpath("//input[@name='form_token']"));
            this.secretToken = tokenInput.getAttribute("value");
        } catch (NoSuchElementException exception) {
            // Something is really wrong if this happens.
            System.out.println("Warning: Failed to cache anti-CSRF secret token, some tests might fail!");
            exception.printStackTrace();
        }
    }

    /**
     * Get the secret token used for CSRF protection. Remember to call {@link #recacheSecretToken()} first.
     *
     * @return anti-CSRF secret token, or empty string if the token was not cached
     * @see #recacheSecretToken()
     */
    public String getSecretToken()
    {
        if (this.secretToken == null) {
            System.out.println("Warning: No cached anti-CSRF token found. "
                + "Make sure to call recacheSecretToken() before getSecretToken(), otherwise this test might fail.");
            return "";
        }
        return this.secretToken;
    }

    /**
     * This class represents all cookies stored in the browser. Use with getSession() and setSession()
     */
    public class Session
    {
        private final Set<Cookie> cookies;

        private final String secretToken;

        private Session(final Set<Cookie> cookies, final String secretToken)
        {
            this.cookies = Collections.unmodifiableSet(new HashSet<Cookie>()
            {
                {
                    addAll(cookies);
                }
            });
            this.secretToken = secretToken;
        }

        private Set<Cookie> getCookies()
        {
            return this.cookies;
        }

        private String getSecretToken()
        {
            return this.secretToken;
        }
    }

    public boolean isInWYSIWYGEditMode()
    {
        return getDriver().findElements(By.xpath("//div[@id='editcolumn' and contains(@class, 'editor-wysiwyg')]"))
            .size() > 0;
    }

    public boolean isInWikiEditMode()
    {
        return getDriver().findElements(By.xpath("//div[@id='editcolumn' and contains(@class, 'editor-wiki')]")).size() > 0;
    }

    public boolean isInViewMode()
    {
        return !getDriver().hasElementWithoutWaiting(By.id("editMeta"));
    }

    public boolean isInSourceViewMode()
    {
        return getDriver().findElements(By.xpath("//textarea[@class = 'wiki-code']")).size() > 0;
    }

    public boolean isInInlineEditMode()
    {
        String currentURL = getDriver().getCurrentUrl();
        // Keep checking the deprecated inline action for backward compatibility.
        return currentURL.contains("editor=inline") || currentURL.contains("/inline/");
    }

    public boolean isInRightsEditMode()
    {
        return getDriver().getCurrentUrl().contains("editor=rights");
    }

    public boolean isInObjectEditMode()
    {
        return getDriver().getCurrentUrl().contains("editor=object");
    }

    public boolean isInClassEditMode()
    {
        return getDriver().getCurrentUrl().contains("editor=class");
    }

    public boolean isInDeleteMode()
    {
        return getDriver().getCurrentUrl().contains("/delete/");
    }

    public boolean isInRenameMode()
    {
        return getDriver().getCurrentUrl().contains("xpage=rename");
    }

    public boolean isInCreateMode()
    {
        return getDriver().getCurrentUrl().contains("/create/");
    }

    public boolean isInAdminMode()
    {
        return getDriver().getCurrentUrl().contains("/admin/");
    }

    /**
     * Forces the current user to be the Guest user by clearing all coookies.
     */
    public void forceGuestUser()
    {
        setSession(null);
    }

    public void addObject(String space, String page, String className, Object... properties)
    {
        gotoPage(space, page, "objectadd", toQueryParameters(className, null, properties));
    }

    /**
     * @since 7.2RC1
     */
    public void addObject(EntityReference reference, String className, Object... properties)
    {
        gotoPage(reference, "objectadd", toQueryParameters(className, null, properties));
    }

    public void addObject(String space, String page, String className, Map<String, ?> properties)
    {
        gotoPage(space, page, "objectadd", toQueryParameters(className, null, properties));
    }

    public void deleteObject(String space, String page, String className, int objectNumber)
    {
        StringBuilder queryString = new StringBuilder();

        queryString.append("classname=");
        queryString.append(escapeURL(className));
        queryString.append('&');
        queryString.append("classid=");
        queryString.append(objectNumber);

        gotoPage(space, page, "objectremove", queryString.toString());
    }

    public void updateObject(String space, String page, String className, int objectNumber, Map<String, ?> properties)
    {
        gotoPage(space, page, "save", toQueryParameters(className, objectNumber, properties));
    }

    public void updateObject(String space, String page, String className, int objectNumber, Object... properties)
    {
        // TODO: would be even quicker using REST
        Map<String, Object> queryParameters =
            (Map<String, Object>) toQueryParameters(className, objectNumber, properties);

        // Append the updateOrCreate objectPolicy since we always want this in our tests.
        queryParameters.put("objectPolicy", "updateOrCreate");

        gotoPage(space, page, "save", queryParameters);
    }

    public ClassEditPage addClassProperty(String space, String page, String propertyName, String propertyType)
    {
        gotoPage(space, page, "propadd", "propname", propertyName, "proptype", propertyType);
        return new ClassEditPage();
    }

    /**
     * @since 3.5M1
     */
    public String toQueryString(Object... queryParameters)
    {
        return toQueryString(toQueryParameters(queryParameters));
    }

    /**
     * @since 3.5M1
     */
    public String toQueryString(Map<String, ?> queryParameters)
    {
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, ?> entry : queryParameters.entrySet()) {
            addQueryStringEntry(builder, entry.getKey(), entry.getValue());
            builder.append('&');
        }

        return builder.toString();
    }

    /**
     * @sice 3.2M1
     */
    public void addQueryStringEntry(StringBuilder builder, String key, Object value)
    {
        if (value != null) {
            if (value instanceof Iterable) {
                for (Object element : (Iterable<?>) value) {
                    addQueryStringEntry(builder, key, element.toString());
                    builder.append('&');
                }
            } else {
                addQueryStringEntry(builder, key, value.toString());
            }
        } else {
            addQueryStringEntry(builder, key, (String) null);
        }
    }

    /**
     * @sice 3.2M1
     */
    public void addQueryStringEntry(StringBuilder builder, String key, String value)
    {
        builder.append(escapeURL(key));
        if (value != null) {
            builder.append('=');
            builder.append(escapeURL(value));
        }
    }

    /**
     * @since 3.5M1
     */
    public Map<String, ?> toQueryParameters(Object... properties)
    {
        return toQueryParameters(null, null, properties);
    }

    public Map<String, ?> toQueryParameters(String className, Integer objectNumber, Object... properties)
    {
        Map<String, Object> queryParameters = new HashMap<String, Object>();

        queryParameters.put("classname", className);

        for (int i = 0; i < properties.length; i += 2) {
            int nextIndex = i + 1;
            queryParameters.put(toQueryParameterKey(className, objectNumber, (String) properties[i]),
                nextIndex < properties.length ? properties[nextIndex] : null);
        }

        return queryParameters;
    }

    public Map<String, ?> toQueryParameters(String className, Integer objectNumber, Map<String, ?> properties)
    {
        Map<String, Object> queryParameters = new HashMap<String, Object>();

        if (className != null) {
            queryParameters.put("classname", className);
        }

        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            queryParameters.put(toQueryParameterKey(className, objectNumber, entry.getKey()), entry.getValue());
        }

        return queryParameters;
    }

    public String toQueryParameterKey(String className, Integer objectNumber, String key)
    {
        if (className == null) {
            return key;
        } else {
            StringBuilder keyBuilder = new StringBuilder(className);

            keyBuilder.append('_');

            if (objectNumber != null) {
                keyBuilder.append(objectNumber);
                keyBuilder.append('_');
            }

            keyBuilder.append(key);

            return keyBuilder.toString();
        }
    }

    public ObjectEditPage editObjects(String space, String page)
    {
        gotoPage(space, page, "edit", "editor=object");
        return new ObjectEditPage();
    }

    public ClassEditPage editClass(String space, String page)
    {
        gotoPage(space, page, "edit", "editor=class");
        return new ClassEditPage();
    }

    public String getVersion() throws Exception
    {
        Xwiki xwiki = rest().getResource("", null);

        return xwiki.getVersion();
    }

    public String getMavenVersion() throws Exception
    {
        String version = getVersion();

        int index = version.indexOf('-');
        if (index > 0) {
            version = version.substring(0, index) + "-SNAPSHOT";
        }

        return version;
    }

    public void attachFile(String space, String page, String name, File file, boolean failIfExists) throws Exception
    {
        InputStream is = new FileInputStream(file);
        try {
            attachFile(space, page, name, is, failIfExists);
        } finally {
            is.close();
        }
    }

    /**
     * @since 5.1M2
     */
    public void attachFile(String space, String page, String name, InputStream is, boolean failIfExists,
        UsernamePasswordCredentials credentials) throws Exception
    {
        attachFile(Collections.singletonList(space), page, name, is, failIfExists, credentials);
    }

    /**
     * @since 7.2M2
     */
    public void attachFile(List<String> spaces, String page, String name, InputStream is, boolean failIfExists,
        UsernamePasswordCredentials credentials) throws Exception
    {
        UsernamePasswordCredentials currentCredentials = getDefaultCredentials();

        try {
            if (credentials != null) {
                setDefaultCredentials(credentials);
            }
            attachFile(spaces, page, name, is, failIfExists);
        } finally {
            setDefaultCredentials(currentCredentials);
        }
    }

    public void attachFile(String space, String page, String name, InputStream is, boolean failIfExists)
        throws Exception
    {
        attachFile(Collections.singletonList(space), page, name, is, failIfExists);
    }

    /**
     * @since 7.2M2
     */
    public void attachFile(List<String> spaces, String page, String name, InputStream is, boolean failIfExists)
        throws Exception
    {
        // make sure xwiki.Import exists
        if (!pageExists(spaces, page)) {
            createPage(spaces, page, null, null);
        }

        StringBuilder url = new StringBuilder(BASE_REST_URL);

        url.append("wikis/xwiki");
        for (String space : spaces) {
            url.append("/spaces/").append(escapeURL(space));
        }
        url.append("/pages/");
        url.append(escapeURL(page));
        url.append("/attachments/");
        url.append(escapeURL(name));

        if (failIfExists) {
            executePut(url.toString(), is, MediaType.APPLICATION_OCTET_STREAM, Status.CREATED.getStatusCode());
        } else {
            executePut(url.toString(), is, MediaType.APPLICATION_OCTET_STREAM, Status.CREATED.getStatusCode(),
                Status.ACCEPTED.getStatusCode());
        }
    }

    // FIXME: improve that with a REST API to directly import a XAR
    public void importXar(File file) throws Exception
    {
        // attach file
        attachFile("XWiki", "Import", file.getName(), file, false);

        // import file
        executeGet(
            getBaseBinURL() + "import/XWiki/Import?historyStrategy=add&importAsBackup=true&ajax&action=import&name="
                + escapeURL(file.getName()), Status.OK.getStatusCode());
    }

    /**
     * Delete the latest version from the history of a page, using the {@code /deleteversions/} action.
     * 
     * @param space the space name of the page
     * @param page the name of the page
     * @since 7.0M2
     */
    public void deleteLatestVersion(String space, String page)
    {
        deleteVersion(space, page, "latest");
    }

    /**
     * Delete a specific version from the history of a page, using the {@code /deleteversions/} action.
     * 
     * @param space the space name of the page
     * @param page the name of the page
     * @param version the version to delete
     * @since 7.0M2
     */
    public void deleteVersion(String space, String page, String version)
    {
        deleteVersions(space, page, version, version);
    }

    /**
     * Delete an interval of versions from the history of a page, using the {@code /deleteversions/} action.
     * 
     * @param space the space name of the page
     * @param page the name of the page
     * @param v1 the starting version to delete
     * @param v2 the ending version to delete
     * @since 7.0M2
     */
    public void deleteVersions(String space, String page, String v1, String v2)
    {
        gotoPage(space, page, "deleteversions", "rev1", v1, "rev2", v2, "confirm", "1");
    }

    /**
     * Roll back a page to the previous version, using the {@code /rollback/} action.
     * 
     * @param space the space name of the page
     * @param page the name of the page
     * @since 7.0M2
     */
    public void rollbackToPreviousVersion(String space, String page)
    {
        rollBackTo(space, page, "previous");
    }

    /**
     * Roll back a page to the specified version, using the {@code /rollback/} action.
     * 
     * @param space the space name of the page
     * @param page the name of the page
     * @param version the version to rollback to
     * @since 7.0M2
     */
    public void rollBackTo(String space, String page, String version)
    {
        gotoPage(space, page, "rollback", "rev", version, "confirm", "1");
    }

    /**
     * Set the hierarchy mode used in the wiki
     * 
     * @param mode the mode to use ("reference" or "parentchild")
     * @since 7.2M2
     */
    public void setHierarchyMode(String mode)
    {
        setPropertyInXWikiPreferences("core.hierarchyMode", "String", mode);
    }

    /**
     * Add and set a property into XWiki.XWikiPreferences. Create XWiki.XWikiPreferences if it does not exist.
     * 
     * @param propertyName name of the property to set
     * @param propertyType the type of the property to add
     * @param value value to set to the property
     * @since 7.2M2
     */
    public void setPropertyInXWikiPreferences(String propertyName, String propertyType, Object value)
    {
        addClassProperty("XWiki", "XWikiPreferences", propertyName, propertyType);
        gotoPage("XWiki", "XWikiPreferences", "edit", "editor", "object");
        ObjectEditPage objectEditPage = new ObjectEditPage();
        if (objectEditPage.hasObject("XWiki.XWikiPreferences")) {
            updateObject("XWiki", "XWikiPreferences", "XWiki.XWikiPreferences", 0, propertyName, value);
        } else {
            addObject("XWiki", "XWikiPreferences", "XWiki.XWikiPreferences", propertyName, value);
        }
    }

    /**
     * @since 7.3M1
     */
    public static void assertStatuses(int actualCode, int... expectedCodes)
    {
        if (!ArrayUtils.contains(expectedCodes, actualCode)) {
            Assert.fail("Unexpected code [" + actualCode + "], was expecting one of [" + Arrays.asList(expectedCodes)
                + "]");
        }
    }

    /**
     * @since 7.3M1
     */
    public static <M extends HttpMethod> M assertStatusCodes(M method, int... expectedCodes)
    {
        if (expectedCodes.length > 0) {
            assertStatuses(method.getStatusCode(), expectedCodes);

            method.releaseConnection();
        }

        return method;
    }

    // HTTP

    /**
     * Encodes a given string so that it may be used as a URL component. Compatable with javascript decodeURIComponent,
     * though more strict than encodeURIComponent: all characters except [a-zA-Z0-9], '.', '-', '*', '_' are converted
     * to hexadecimal, and spaces are substituted by '+'.
     *
     * @param s
     */
    public String escapeURL(String s)
    {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // should not happen
            throw new RuntimeException(e);
        }
    }

    public InputStream getInputStream(String path, Map<String, ?> queryParams) throws Exception
    {
        return getInputStream(getBaseURL(), path, queryParams);
    }

    public String getString(String path, Map<String, ?> queryParams) throws Exception
    {
        try (InputStream inputStream = getInputStream(getBaseURL(), path, queryParams)) {
            return IOUtils.toString(inputStream);
        }
    }

    public InputStream getInputStream(String prefix, String path, Map<String, ?> queryParams, Object... elements)
        throws Exception
    {
        String cleanPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        if (path.startsWith(cleanPrefix)) {
            cleanPrefix = "";
        }

        UriBuilder builder = UriBuilder.fromUri(cleanPrefix).path(path.startsWith("/") ? path.substring(1) : path);

        if (queryParams != null) {
            for (Map.Entry<String, ?> entry : queryParams.entrySet()) {
                if (entry.getValue() instanceof Object[]) {
                    builder.queryParam(entry.getKey(), (Object[]) entry.getValue());
                } else {
                    builder.queryParam(entry.getKey(), entry.getValue());
                }
            }
        }

        String url = builder.build(elements).toString();

        return executeGet(url, Status.OK.getStatusCode()).getResponseBodyAsStream();
    }

    protected GetMethod executeGet(String uri) throws Exception
    {
        GetMethod getMethod = new GetMethod(uri);

        this.httpClient.executeMethod(getMethod);

        return getMethod;
    }

    protected GetMethod executeGet(String uri, int... expectedCodes) throws Exception
    {
        return assertStatusCodes(executeGet(uri), expectedCodes);
    }

    /**
     * @since 7.3M1
     */
    protected PostMethod executePost(String uri, InputStream content, String mediaType) throws Exception
    {
        PostMethod postMethod = new PostMethod(uri);
        RequestEntity entity = new InputStreamRequestEntity(content, mediaType);
        postMethod.setRequestEntity(entity);

        this.httpClient.executeMethod(postMethod);

        return postMethod;
    }

    protected PostMethod executePost(String uri, InputStream content, String mediaType, int... expectedCodes)
        throws Exception
    {
        return assertStatusCodes(executePost(uri, content, mediaType), expectedCodes);
    }

    /**
     * @since 7.3M1
     */
    protected DeleteMethod executeDelete(String uri) throws Exception
    {
        DeleteMethod postMethod = new DeleteMethod(uri);

        this.httpClient.executeMethod(postMethod);

        return postMethod;
    }

    /**
     * @since 7.3M1
     */
    protected DeleteMethod executeDelete(String uri, int... expectedCodes) throws Exception
    {
        return assertStatusCodes(executeDelete(uri), expectedCodes);
    }

    /**
     * @since 7.3M1
     */
    protected PutMethod executePut(String uri, InputStream content, String mediaType) throws Exception
    {
        PutMethod putMethod = new PutMethod(uri);
        RequestEntity entity = new InputStreamRequestEntity(content, mediaType);
        putMethod.setRequestEntity(entity);

        this.httpClient.executeMethod(putMethod);

        return putMethod;
    }

    protected PutMethod executePut(String uri, InputStream content, String mediaType, int... expectedCodes)
        throws Exception
    {
        return assertStatusCodes(executePut(uri, content, mediaType), expectedCodes);
    }

    // REST

    public RestTestUtils rest()
    {
        return this.rest;
    }

    /**
     * @since 7.3M1
     */
    public class RestTestUtils
    {
        public final Boolean ELEMENTS_ENCODED = new Boolean(true);

        /**
         * @since 7.3M1
         */
        public String getBaseURL()
        {
            return TestUtils.this.getBaseURL() + "rest";
        }

        private String toSpaceElement(String spaceReference)
        {
            StringBuilder builder = new StringBuilder();

            for (EntityReference reference : RELATIVE_RESOLVER.resolve(spaceReference, EntityType.SPACE)
                .getReversedReferenceChain()) {
                if (builder.length() > 0) {
                    builder.append("/spaces/");
                }

                builder.append(reference.getName());
            }

            return builder.toString();
        }

        protected Object[] toElements(Page page)
        {
            List<Object> elements = new ArrayList<>();

            // Add wiki
            if (page.getWiki() != null) {
                elements.add(page.getWiki());
            } else {
                elements.add(getCurrentWiki());
            }

            // Add spaces
            elements.add(toSpaceElement(page.getSpace()));

            // Add name
            elements.add(page.getName());

            return elements.toArray();
        }

        protected Object[] toElements(org.xwiki.rest.model.jaxb.Object obj, boolean onlyDocument)
        {
            List<Object> elements = new ArrayList<>();

            // Add wiki
            if (obj.getWiki() != null) {
                elements.add(obj.getWiki());
            } else {
                elements.add(getCurrentWiki());
            }

            // Add spaces
            elements.add(toSpaceElement(obj.getSpace()));

            // Add name
            elements.add(obj.getPageName());

            if (!onlyDocument) {
                // Add class
                elements.add(obj.getClassName());

                // Add number
                elements.add(obj.getNumber());
            }

            return elements.toArray();
        }

        public Object[] toElements(EntityReference reference)
        {
            List<EntityReference> references = reference.getReversedReferenceChain();

            List<Object> elements = new ArrayList<>(references.size() + 2);

            // Indicate that elements are already encoded
            elements.add(ELEMENTS_ENCODED);

            // Add current wiki if the reference does not contains any
            if (reference.extractReference(EntityType.WIKI) == null) {
                elements.add(escapeURL(getCurrentWiki()));
            }

            // Add reference
            for (EntityReference ref : references) {
                if (ref.getType() == EntityType.SPACE) {
                    // The URI builder does not support multiple elements like space reference so we hack it by doing
                    // the opposite of what is done when reading the URL (generate a value looking like
                    // "space1/spaces/space2")
                    Object value = elements.get(elements.size() - 1);

                    StringBuilder builder;
                    if (value instanceof StringBuilder) {
                        builder = (StringBuilder) value;
                        builder.append("/spaces/");
                    } else {
                        builder = new StringBuilder();
                        elements.add(builder);
                    }

                    builder.append(escapeURL(ref.getName()));
                } else {
                    elements.add(escapeURL(ref.getName()));
                }
            }

            return elements.toArray();
        }

        /**
         * Add or update.
         */
        public EntityEnclosingMethod save(Page page, int... expectedCodes) throws Exception
        {
            return TestUtils.assertStatusCodes(executePut(PageResource.class, page, toElements(page)));
        }

        /**
         * Add a new object.
         */
        public EntityEnclosingMethod add(org.xwiki.rest.model.jaxb.Object obj, int... expectedCodes) throws Exception
        {
            return TestUtils.assertStatusCodes(executePost(ObjectsResource.class, obj, toElements(obj, true)));
        }

        /**
         * Fail if the object does not exist.
         */
        public EntityEnclosingMethod update(org.xwiki.rest.model.jaxb.Object obj, int... expectedCodes)
            throws Exception
        {
            return TestUtils.assertStatusCodes(executePut(ObjectResource.class, obj, toElements(obj, false)));
        }

        public DeleteMethod delete(EntityReference reference, int... expectedCodes) throws Exception
        {
            switch (reference.getType()) {
                case DOCUMENT:
                    return TestUtils.assertStatusCodes(executeDelete(PageResource.class, toElements(reference)),
                        expectedCodes);
                case ATTACHMENT:
                    return TestUtils.assertStatusCodes(executeDelete(AttachmentResource.class, toElements(reference)),
                        expectedCodes);
                case OBJECT:
                    return TestUtils.assertStatusCodes(executeDelete(ObjectResource.class, toElements(reference)),
                        expectedCodes);
                case OBJECT_PROPERTY:
                    return TestUtils.assertStatusCodes(
                        executeDelete(ObjectPropertyResource.class, toElements(reference)), expectedCodes);

                default:
                    throw new Exception("Unsuported type [" + reference.getType() + "]");
            }
        }

        public InputStream getInputStream(String resourceUri, Map<String, ?> queryParams, Object... elements)
            throws Exception
        {
            return TestUtils.this.getInputStream(getBaseURL(), resourceUri, queryParams, elements);
        }

        public InputStream postRESTInputStream(Object resourceUri, Object restObject, Object... elements)
            throws Exception
        {
            return postInputStream(resourceUri, restObject, Collections.<String, Object[]>emptyMap(), elements);
        }

        public InputStream postInputStream(Object resourceUri, Object restObject, Map<String, Object[]> queryParams,
            Object... elements) throws Exception
        {
            return executePost(resourceUri, restObject, queryParams, elements).getResponseBodyAsStream();
        }

        protected InputStream toResourceInputStream(Object restObject) throws JAXBException
        {
            InputStream resourceStream;
            if (restObject instanceof InputStream) {
                resourceStream = (InputStream) restObject;
            } else {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                marshaller.marshal(restObject, stream);
                resourceStream = new ByteArrayInputStream(stream.toByteArray());
            }

            return resourceStream;
        }

        public PostMethod executePost(Object resourceUri, Object restObject, Object... elements) throws Exception
        {
            return executePost(resourceUri, restObject, Collections.<String, Object[]>emptyMap(), elements);
        }

        public PostMethod executePost(Object resourceUri, Object restObject, Map<String, Object[]> queryParams,
            Object... elements) throws Exception
        {
            // Build URI
            String uri = createUri(resourceUri, queryParams, elements).toString();

            try (InputStream resourceStream = toResourceInputStream(restObject)) {
                return TestUtils.this.executePost(uri, resourceStream, MediaType.APPLICATION_XML,
                    Status.OK.getStatusCode());
            }
        }

        public PutMethod executePut(Object resourceUri, Object restObject, Object... elements) throws Exception
        {
            return executePut(resourceUri, restObject, Collections.<String, Object[]>emptyMap(), elements);
        }

        public PutMethod executePut(Object resourceUri, Object restObject, Map<String, Object[]> queryParams,
            Object... elements) throws Exception
        {
            // Build URI
            String uri = createUri(resourceUri, queryParams, elements).toString();

            try (InputStream resourceStream = toResourceInputStream(restObject)) {
                return TestUtils.this.executePut(uri, resourceStream, MediaType.APPLICATION_XML,
                    Status.OK.getStatusCode());
            }
        }

        public DeleteMethod executeDelete(Object resourceUri, Object... elements) throws Exception
        {
            return executeDelete(resourceUri, Collections.<String, Object[]>emptyMap(), elements);
        }

        public DeleteMethod executeDelete(Object resourceUri, Map<String, Object[]> queryParams, Object... elements)
            throws Exception
        {
            // Build URI
            String uri = createUri(resourceUri, queryParams, elements).toString();

            return TestUtils.this.executeDelete(uri);
        }

        public URI createUri(Object resourceUri, Map<String, Object[]> queryParams, Object... elements)
        {
            // Create URI builder
            UriBuilder builder = getUriBuilder(resourceUri, queryParams);

            // Build URI
            URI uri;
            if (elements.length > 0 && elements[0] == ELEMENTS_ENCODED) {
                uri = builder.buildFromEncoded(Arrays.copyOfRange(elements, 1, elements.length));
            } else {
                uri = builder.build(elements);
            }

            return uri;
        }

        public UriBuilder getUriBuilder(Object resourceUri, Map<String, Object[]> queryParams)
        {
            // Create URI builder
            UriBuilder builder;
            if (resourceUri instanceof Class) {
                builder = getUriBuilder((Class) resourceUri);
            } else {
                String stringResourceUri = (String) resourceUri;
                builder =
                    UriBuilder.fromUri(getBaseURL().substring(0, getBaseURL().length() - 1)).path(
                        !stringResourceUri.isEmpty() && stringResourceUri.charAt(0) == '/' ? stringResourceUri
                            .substring(1) : stringResourceUri);
            }

            // Add query parameters
            if (queryParams != null) {
                for (Map.Entry<String, Object[]> entry : queryParams.entrySet()) {
                    builder.queryParam(entry.getKey(), entry.getValue());
                }
            }

            return builder;
        }

        protected UriBuilder getUriBuilder(Class<?> resource)
        {
            return UriBuilder.fromUri(getBaseURL()).path(resource);
        }

        public byte[] getBuffer(String resourceUri, Map<String, Object[]> queryParams, Object... elements)
            throws Exception
        {
            InputStream is = getInputStream(resourceUri, queryParams, elements);

            byte[] buffer;
            try {
                buffer = IOUtils.toByteArray(is);
            } finally {
                is.close();
            }

            return buffer;
        }

        public <T> T getResource(String resourceUri, Map<String, Object[]> queryParams, Object... elements)
            throws Exception
        {
            T resource;
            try (InputStream is = getInputStream(resourceUri, queryParams, elements)) {
                resource = (T) unmarshaller.unmarshal(is);
            }

            return resource;
        }
    }
}
