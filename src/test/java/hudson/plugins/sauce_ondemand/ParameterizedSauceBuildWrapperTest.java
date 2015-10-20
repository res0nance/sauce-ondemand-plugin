package hudson.plugins.sauce_ondemand;

import com.saucelabs.ci.sauceconnect.SauceConnectFourManager;
import com.saucelabs.hudson.HudsonSauceManagerFactory;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;


import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;


/**
 * Created by gavinmogan on 10/14/15.
 */
@RunWith(Parameterized.class)
public class ParameterizedSauceBuildWrapperTest {
    /**
     * JUnit rule which instantiates a local Jenkins instance with our plugin installed.
     */
    @Rule
    public transient JenkinsRule jenkinsRule = new JenkinsRule();

    /**
     * Dummy credentials to be used by the test.
     */
    private Credentials sauceCredentials = new Credentials("username", "access key");

    /**
     * Build Wrapper with all the parameters set right
     */
    private SauceOnDemandBuildWrapper sauceBuildWrapper;

    public ParameterizedSauceBuildWrapperTest(
            boolean enableSauceConnect,
            boolean launchSauceConnectOnSlave,
            boolean useGeneratedTunnelIdentifier,
            boolean verboseLogging,
            boolean useLatestVersion,
            String seleniumPort,
            String seleniumHost
    ) {
        super();
        sauceBuildWrapper = new TestSauceOnDemandBuildWrapper(sauceCredentials);
        sauceBuildWrapper.setEnableSauceConnect(enableSauceConnect);
        sauceBuildWrapper.setLaunchSauceConnectOnSlave(launchSauceConnectOnSlave);
        sauceBuildWrapper.setUseGeneratedTunnelIdentifier(useGeneratedTunnelIdentifier);
        sauceBuildWrapper.setVerboseLogging(verboseLogging);
        sauceBuildWrapper.setUseLatestVersion(useLatestVersion);
        sauceBuildWrapper.setSeleniumPort(seleniumPort);
        sauceBuildWrapper.setSeleniumHost(seleniumHost);
    }

    @Parameterized.Parameters
    public static Collection SauceOnDemandBuildWrapperValues() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        for(boolean enableSauceConnect : new boolean[] {true, false }) {
            for(boolean launchSauceConnectOnSlave : new boolean[] { true, false }) {
                for (boolean useGeneratedTunnelIdentifier : new boolean[]{true, false}) {
                    for (boolean verboseLogging : new boolean[]{true, false}) {
                        for (boolean useLatestVersion : new boolean[]{true, false}) {
                            for (String seleniumPort : new String[]{"", "4444"}) {
                                for (String seleniumHost : new String[]{"", "localhost"}) {
                                    list.add(new Object[]{
                                            enableSauceConnect,
                                            launchSauceConnectOnSlave,
                                            useGeneratedTunnelIdentifier,
                                            verboseLogging,
                                            useLatestVersion,
                                            seleniumPort,
                                            seleniumHost
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    @Before
    public void setUp() throws Exception {
        SauceConnectFourManager sauceConnectFourManager = new SauceConnectFourManager() {
            @Override
            public Process openConnection(String username, String apiKey, int port, File sauceConnectJar, String options, PrintStream printStream, Boolean verboseLogging, String sauceConnectPath) throws SauceConnectException {
                return null;
            }
        };

        storeDummyManager(sauceConnectFourManager);
    }

    private void storeDummyManager(SauceConnectFourManager sauceConnectFourManager) throws Exception {
        HudsonSauceManagerFactory factory = HudsonSauceManagerFactory.getInstance();
        Field field = HudsonSauceManagerFactory.class.getDeclaredField("sauceConnectFourManager");
        field.setAccessible(true);
        field.set(factory, sauceConnectFourManager);
    }

    /**
     * @throws Exception thrown if an unexpected error occurs
     */
    @Test
    public void portIsProperlyProvidedToSauceConnect() throws Exception {
        if (!sauceBuildWrapper.isEnableSauceConnect()) { return; } /* Don't test sauce connect functionality if its not enabled */

        final JSONObject holder = new JSONObject();

        SauceConnectFourManager sauceConnectFourManager = new SauceConnectFourManager() {
            @Override
            public Process openConnection(String username, String apiKey, int port, File sauceConnectJar, String options, PrintStream printStream, Boolean verboseLogging, String sauceConnectPath) throws SauceConnectException {
                holder.element("scProvidedPort", port);
                return null;
            }
        };

        storeDummyManager(sauceConnectFourManager);
        SauceBuilder sauceBuilder = new SauceBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                Map<String, String> envVars = build.getEnvironment(listener);
                int port = Integer.parseInt(envVars.get("SELENIUM_PORT"));
                holder.element("port", port);
                return super.perform(build, launcher, listener);
            }
        };

        FreeStyleBuild build = runFreestyleBuild(sauceBuildWrapper, sauceBuilder);
        assertThat("greater than 0", holder.getInt("port"), greaterThan(0));
        assertEquals("Port provided to SC is the same as generated", holder.getInt("scProvidedPort"), holder.getInt("port"));
        jenkinsRule.assertBuildStatusSuccess(build);
    }

    @Test
    public void confirmEnvVariablesAreAlwaysSet() throws Exception {
        final JSONObject holder = new JSONObject();
        SauceBuilder sauceBuilder = new SauceBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                Map<String, String> envVars = build.getEnvironment(listener);
                holder.element("env", envVars);
                return super.perform(build, launcher, listener);
            }
        };

        FreeStyleBuild build = runFreestyleBuild(sauceBuildWrapper, sauceBuilder);
        jenkinsRule.assertBuildStatusSuccess(build);

        Map<String, String> envVars = (Map<String, String>)holder.get("env");
        assertNotNull(envVars);
        if (envVars != null) {
            assertEquals("legacy SAUCE_USER_NAME is set to API username", "username", envVars.get("SAUCE_USER_NAME"));
            assertEquals("proper SAUCE_USERNAME is set to API username", "username", envVars.get("SAUCE_USERNAME"));
            assertEquals("legacy SAUCE_API_KEY is set to API username", "access key", envVars.get("SAUCE_API_KEY"));
            assertEquals("proper SAUCE_ACCESS_KEY is set to API username", "access key", envVars.get("SAUCE_ACCESS_KEY"));
            assertThat("SELENIUM_HOST equals something", envVars.get("SELENIUM_HOST"), not(isEmptyOrNullString()));
            assertThat("SELENIUM_PORT equals something", envVars.get("SELENIUM_PORT"), not(isEmptyOrNullString()));
            assertThat("JENKINS_BUILD_NUMBER equals something", envVars.get("JENKINS_BUILD_NUMBER"), not(isEmptyOrNullString()));

            if (!"".equals(sauceBuildWrapper.getNativeAppPackage())) {
                assertNull("SAUCE_NATIVE_APP is not set when native package is not set", envVars.get("SAUCE_NATIVE_APP"));
            } else {
                assertThat("SAUCE_NATIVE_APP is set when native package is set", envVars.get("SAUCE_NATIVE_APP"), not(isEmptyOrNullString()));
            }
            if (sauceBuildWrapper.isEnableSauceConnect() && sauceBuildWrapper.isUseGeneratedTunnelIdentifier()) {
                assertThat("TUNNEL_IDENTIFIER is set when we are managing it", envVars.get("TUNNEL_IDENTIFIER"), not(isEmptyOrNullString()));

            } else {
                assertNull("TUNNEL_IDENTIFIER is not set when we are not managing it", envVars.get("SAUCE_NATIVE_APP"));

            }
            if (sauceBuildWrapper.isUseChromeForAndroid()) {
                assertNull("SAUCE_USE_CHROME is not set when use chrome is not set", envVars.get("SAUCE_USE_CHROME"));
            } else {
                assertThat("SAUCE_USE_CHROME is set when use chrome is set", envVars.get("SAUCE_USE_CHROME"), not(isEmptyOrNullString()));
            }
            /*
            SELENIUM_PLATFORM, SELENIUM_BROWSER, SELENIUM_VERSION, SELENIUM_DRIVER, SELENIUM_DEVICE, SELENIUM_DEVICE_TYPE, SELENIUM_DEVICE_ORIENTATION
            SAUCE_ONDEMAND_BROWSERS
            */
        }
    }

    private FreeStyleBuild runFreestyleBuild(SauceOnDemandBuildWrapper sauceBuildWrapper, TestBuilder builder) throws Exception {
        FreeStyleProject freeStyleProject = jenkinsRule.createFreeStyleProject();
        freeStyleProject.getBuildWrappersList().add(sauceBuildWrapper);
        freeStyleProject.getBuildersList().add(builder);

        QueueTaskFuture<FreeStyleBuild> future = freeStyleProject.scheduleBuild2(0);

        FreeStyleBuild build = future.get(1, TimeUnit.MINUTES);

        assertNotNull(build);
        return build;
    }

    /**
     * Dummy builder which is run by the unit tests.
     */
    private class SauceBuilder extends TestBuilder implements Serializable {

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            Thread.sleep(1);
            return true;
        }
    }

}
