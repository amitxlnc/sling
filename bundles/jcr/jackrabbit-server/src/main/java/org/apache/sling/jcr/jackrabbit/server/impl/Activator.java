/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jcr.jackrabbit.server.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;

import org.apache.sling.jcr.base.AbstractSlingRepository;
import org.apache.sling.jcr.base.util.RepositoryAccessor;
import org.apache.sling.jcr.jackrabbit.server.security.LoginModulePlugin;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>Activator</code> TODO
 */
public class Activator implements BundleActivator, ServiceListener {

    /** default log */
    private static final Logger log = LoggerFactory.getLogger(Activator.class);

    public static final String SERVER_REPOSITORY_FACTORY_PID = "org.apache.sling.jcr.jackrabbit.server.SlingServerRepository";

    /**
     * The name of the configuration property naming the Sling Context for which
     * a factory configuration has been created.
     */
    public static final String SLING_CONTEXT = "sling.context";

    /**
     * The name of the framework property containing the default sling context
     * name.
     */
    public static final String SLING_CONTEXT_DEFAULT = "sling.context.default";

    // The name of the Configuration Admin Service
    private static final String CONFIG_ADMIN_NAME = ConfigurationAdmin.class.getName();

    // this bundle's context, used by verifyConfiguration
    private static BundleContext bundleContext;

    // the service tracker used by the PluggableDefaultLoginModule
    // this field is only set on the first call to getLoginModules()
    private static ServiceTracker loginModuleTracker;

    // the tracking count when the moduleCache has been filled
    private static int lastTrackingCount = -1;

    // the cache of login module services
    private static LoginModulePlugin[] moduleCache;

    // empty list of login modules if there are none registered
    private static LoginModulePlugin[] EMPTY = new LoginModulePlugin[0];

    // the name of the default sling context
    private String slingContext;
    private static AccessManagerFactoryTracker accessManagerFactoryTracker;

    protected String getRepositoryName() {
    	String repoName = bundleContext.getProperty("sling.repository.name");
    	if (repoName != null) {
            return repoName; // the repository name is set
    	}
		return "jackrabbit";
    }

    public void start(BundleContext context) {

        bundleContext = context;

        // ensure the module cache is not set right now, this may
        // (theoretically) be non-null after the last bundle stop
        moduleCache = null;

        // check the name of the default context, nothing to do if none
        slingContext = context.getProperty(SLING_CONTEXT_DEFAULT);
        if (slingContext == null) {
            slingContext = "default";
        }

        ServiceReference sr = context.getServiceReference(CONFIG_ADMIN_NAME);
        if (sr != null) {

            // immediately verify the configuration as the service is here
            verifyConfiguration(sr);

        } else {

            // register as service listener for Configuration Admin to verify
            // the configuration when the service is registered
            try {
                bundleContext.addServiceListener(this, "("
                    + Constants.OBJECTCLASS + "=" + CONFIG_ADMIN_NAME + ")");
            } catch (InvalidSyntaxException ise) {
                log.error(
                    "start: Failed to register for Configuration Admin Service, will not verify configuration",
                    ise);
            }
        }
        if (accessManagerFactoryTracker == null) {
            accessManagerFactoryTracker = new AccessManagerFactoryTracker(bundleContext);
        }
        accessManagerFactoryTracker.open();
    }

    public void stop(BundleContext arg0) {

        // drop module cache
        moduleCache = null;

        // close the loginModuleTracker
        if (loginModuleTracker != null) {
            loginModuleTracker.close();
            loginModuleTracker = null;
        }

        if (accessManagerFactoryTracker != null) {
            accessManagerFactoryTracker.close();
            accessManagerFactoryTracker = null;
        }

        // clear the bundle context field
        bundleContext = null;
    }

    // ---------- ServiceListener ----------------------------------------------

    public void serviceChanged(ServiceEvent event) {
        if (event.getType() == ServiceEvent.REGISTERED) {

            // verify the configuration with the newly registered service
            verifyConfiguration(event.getServiceReference());

            // don't care for any more service state changes
            bundleContext.removeServiceListener(this);
        }
    }

    // ---------- LoginModule tracker for PluggableDefaultLoginModule

    private static BundleContext getBundleContext() {
        return bundleContext;
    }

    /**
     * Returns the registered {@link LoginModulePlugin} services. If there are
     * no {@link LoginModulePlugin} services registered, this method returns an
     * empty array. <code>null</code> is never returned from this method.
     */
    public static LoginModulePlugin[] getLoginModules() {
        // fast track cache (cache first, since loginModuleTracker is only
        // non-null if moduleCache is non-null)
        if (moduleCache != null
            && lastTrackingCount == loginModuleTracker.getTrackingCount()) {
            return moduleCache;
        }
        // invariant: moduleCache is null or modules have changed

        // tracker may be null if moduleCache is null
        if (loginModuleTracker == null) {
            loginModuleTracker = new ServiceTracker(getBundleContext(),
                LoginModulePlugin.class.getName(), null);
            loginModuleTracker.open();
        }

        if (moduleCache == null || lastTrackingCount < loginModuleTracker.getTrackingCount()) {
            Object[] services = loginModuleTracker.getServices();
            if (services == null || services.length == 0) {
                moduleCache = EMPTY;
            } else {
                moduleCache = new LoginModulePlugin[services.length];
                System.arraycopy(services, 0, moduleCache, 0, services.length);
            }
            lastTrackingCount = loginModuleTracker.getTrackingCount();
        }

        // the module cache is now up to date
        return moduleCache;
    }

    public static AccessManagerFactoryTracker getAccessManagerFactoryTracker() {
        return accessManagerFactoryTracker;
    }

    // ---------- internal -----------------------------------------------------

    private void verifyConfiguration(ServiceReference ref) {
        ConfigurationAdmin ca = (ConfigurationAdmin) bundleContext.getService(ref);
        if (ca == null) {
            log.error("verifyConfiguration: Failed to get Configuration Admin Service from Service Reference");
            return;
        }

        try {
            // find a configuration for theses properties...
            Configuration[] cfgs = ca.listConfigurations("("
                + ConfigurationAdmin.SERVICE_FACTORYPID + "="
                + SERVER_REPOSITORY_FACTORY_PID + ")");
            if (cfgs != null && cfgs.length > 0) {
                log.info(
                    "verifyConfiguration: {} Configurations available for {}, nothing to do",
                    new Object[] { new Integer(cfgs.length),
                        SERVER_REPOSITORY_FACTORY_PID });
                return;
            }

            // No config, create a default one.
            Hashtable<String, String> defaultConfig = new Hashtable<String, String>();
            final String overrideUrl = bundleContext.getProperty(RepositoryAccessor.REPOSITORY_URL_OVERRIDE_PROPERTY);
            if(overrideUrl != null && overrideUrl.length() > 0) {
                // Ignore other parameters if override URL (SLING-254) is set
                defaultConfig.put(RepositoryAccessor.REPOSITORY_URL_OVERRIDE_PROPERTY, overrideUrl);
                log.info(RepositoryAccessor.REPOSITORY_URL_OVERRIDE_PROPERTY + "=" + overrideUrl +
                    ", using it to create the default configuration");

            } else {
               initDefaultConfig(defaultConfig, bundleContext);
            }

            // create the factory and set the properties
            Configuration config = ca.createFactoryConfiguration(SERVER_REPOSITORY_FACTORY_PID);
            config.update(defaultConfig);

            log.info("verifyConfiguration: Created configuration {} for {}",
                config.getPid(), config.getFactoryPid());

        } catch (Throwable t) {
            log.error(
                "verifyConfiguration: Cannot check or define configuration", t);
        } finally {
            bundleContext.ungetService(ref);
        }
    }

    private void initDefaultConfig(Hashtable<String, String> props, BundleContext bundleContext) throws IOException {
        String slingHomePath = bundleContext.getProperty("sling.home");
        String home = getHomePath(bundleContext, slingHomePath);
        if (home == null) {
        	return;
        }

        String configFileUrl = getConfigFileUrl(bundleContext, home);

        // make home relative if inside sling.home
        if (slingHomePath != null && home.startsWith(slingHomePath + "/")) {
            home = home.substring(slingHomePath.length() + 1);
        }

        // default config values
        props.put(SLING_CONTEXT, slingContext);
        if (configFileUrl != null) {
            props.put(SlingServerRepository.REPOSITORY_CONFIG_URL,
                configFileUrl);
        }
        props.put(SlingServerRepository.REPOSITORY_HOME_DIR, home);
        props.put(SlingServerRepository.REPOSITORY_REGISTRATION_NAME,
            this.getRepositoryName());

        // password properties are not used any more, set to a n/a value
        props.put(AbstractSlingRepository.PROPERTY_ADMIN_PASS, "not-used");
        props.put(AbstractSlingRepository.PROPERTY_ANONYMOUS_PASS, "not-used");
    }

    private String getHomePath(BundleContext bundleContext, String slingHomePath) {
        File homeDir;
        String repoHomePath = bundleContext.getProperty("sling.repository.home");
        if (repoHomePath != null) {
            homeDir = new File(repoHomePath, getRepositoryName());
        } else if (slingHomePath != null) {
            homeDir = new File(slingHomePath, getRepositoryName());
        } else {
            homeDir = new File(getRepositoryName());
        }

        // make sure jackrabbit home exists
        log.info("Creating default config for Jackrabbit in " + homeDir);
        if (!homeDir.isDirectory()) {
            if (!homeDir.mkdirs()) {
                log.info("verifyConfiguration: Cannot create Jackrabbit home "
                    + homeDir + ", failed creating default configuration");
                return null;
            }
        }

        return homeDir.getPath();
    }

    private String getConfigFileUrl(BundleContext bundleContext, String home) throws IOException {
    	String repoConfigFileUrl = bundleContext.getProperty("sling.repository.config.file.url");
    	if (repoConfigFileUrl != null) {
    		// the repository config file is set
    		URL configFileUrl = null;
			try {
			    // verify it is a good url
				configFileUrl = new URL(repoConfigFileUrl);
				return repoConfigFileUrl;
			} catch (MalformedURLException e) {
				// this not an url, trying with "file:"
				configFileUrl = new URL("file:///" + repoConfigFileUrl);
				File configFile = new File(configFileUrl.getFile());
				if (configFile.canRead()) {
				    return configFileUrl.toString();
				}
			}
    	}

        // ensure the configuration file (inside the home Dir !)
        File configFile = new File(home, "repository.xml");
        boolean copied = false;

        try {
            URL contextConfigURL = new URL("context:repository.xml");
            InputStream contextConfigStream = contextConfigURL.openStream();
            if (contextConfigStream != null) {
                SlingServerRepository.copyStream(contextConfigStream, configFile);
                copied = true;
            }
        } catch (Exception e) {}

        if (!copied) {
            SlingServerRepository.copyFile(bundleContext.getBundle(), "repository.xml", configFile);
        }

        // config file is repository.xml (default) in homeDir
        return null;
    }

}
