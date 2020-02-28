/*
 * RESTHeart Security
 *
 * Copyright (C) SoftInstigate Srl
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;
import org.restheart.ConfigurationException;
import static org.restheart.security.ConfigurationKeys.ALLOW_UNESCAPED_CHARACTERS_IN_URL;
import static org.restheart.security.ConfigurationKeys.ANSI_CONSOLE_KEY;
import static org.restheart.security.ConfigurationKeys.AUTHENTICATORS_KEY;
import static org.restheart.security.ConfigurationKeys.AUTHORIZERS_KEY;
import static org.restheart.security.ConfigurationKeys.AUTH_MECHANISMS_KEY;
import static org.restheart.security.ConfigurationKeys.BUFFER_SIZE_KEY;
import static org.restheart.security.ConfigurationKeys.CERT_PASSWORD_KEY;
import static org.restheart.security.ConfigurationKeys.CONNECTION_OPTIONS_KEY;
import static org.restheart.security.ConfigurationKeys.DEFAULT_HTTPS_HOST;
import static org.restheart.security.ConfigurationKeys.DEFAULT_HTTPS_LISTENER;
import static org.restheart.security.ConfigurationKeys.DEFAULT_HTTPS_PORT;
import static org.restheart.security.ConfigurationKeys.DEFAULT_HTTP_HOST;
import static org.restheart.security.ConfigurationKeys.DEFAULT_HTTP_LISTENER;
import static org.restheart.security.ConfigurationKeys.DEFAULT_HTTP_PORT;
import static org.restheart.security.ConfigurationKeys.DEFAULT_INSTANCE_NAME;
import static org.restheart.security.ConfigurationKeys.DIRECT_BUFFERS_KEY;
import static org.restheart.security.ConfigurationKeys.ENABLE_LOG_CONSOLE_KEY;
import static org.restheart.security.ConfigurationKeys.ENABLE_LOG_FILE_KEY;
import static org.restheart.security.ConfigurationKeys.FORCE_GZIP_ENCODING_KEY;
import static org.restheart.security.ConfigurationKeys.HTTPS_HOST_KEY;
import static org.restheart.security.ConfigurationKeys.HTTPS_LISTENER;
import static org.restheart.security.ConfigurationKeys.HTTPS_PORT_KEY;
import static org.restheart.security.ConfigurationKeys.HTTP_HOST_KEY;
import static org.restheart.security.ConfigurationKeys.HTTP_LISTENER_KEY;
import static org.restheart.security.ConfigurationKeys.HTTP_PORT_KEY;
import static org.restheart.security.ConfigurationKeys.INSTANCE_NAME_KEY;
import static org.restheart.security.ConfigurationKeys.IO_THREADS_KEY;
import static org.restheart.security.ConfigurationKeys.KEYSTORE_FILE_KEY;
import static org.restheart.security.ConfigurationKeys.KEYSTORE_PASSWORD_KEY;
import static org.restheart.security.ConfigurationKeys.LOG_FILE_PATH_KEY;
import static org.restheart.security.ConfigurationKeys.LOG_LEVEL_KEY;
import static org.restheart.security.ConfigurationKeys.LOG_REQUESTS_LEVEL_KEY;
import static org.restheart.security.ConfigurationKeys.PLUGINS_ARGS_KEY;
import static org.restheart.security.ConfigurationKeys.PLUGINS_DIRECTORY_PATH_KEY;
import static org.restheart.security.ConfigurationKeys.PROXY_KEY;
import static org.restheart.security.ConfigurationKeys.REQUESTS_LIMIT_KEY;
import static org.restheart.security.ConfigurationKeys.REQUESTS_LOG_TRACE_HEADERS_KEY;
import static org.restheart.security.ConfigurationKeys.SERVICES_KEY;
import static org.restheart.security.ConfigurationKeys.TOKEN_MANAGER;
import static org.restheart.security.ConfigurationKeys.USE_EMBEDDED_KEYSTORE_KEY;
import static org.restheart.security.ConfigurationKeys.WORKER_THREADS_KEY;
import org.restheart.security.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Utility class to help dealing with the configuration file.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Configuration {

    /**
     * the version is read from the JAR's MANIFEST.MF file, which is
     * automatically generated by the Maven build process
     */
    public static final String VERSION = Configuration.class.getPackage()
            .getImplementationVersion() == null
                    ? "unknown, not packaged"
                    : Configuration.class.getPackage().getImplementationVersion();

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    public static final String DEFAULT_ROUTE = "0.0.0.0";

    private boolean silent = false;
    private final boolean httpsListener;
    private final int httpsPort;
    private final String httpsHost;
    private final boolean httpListener;
    private final int httpPort;
    private final String httpHost;
    private final String instanceName;
    private final String pluginsDirectory;
    private final boolean useEmbeddedKeystore;
    private final String keystoreFile;
    private final String keystorePassword;
    private final String certPassword;
    private final List<Map<String, Object>> proxies;
    private final Map<String, Map<String, Object>> pluginsArgs;
    private final Map<String, Map<String, Object>> authMechanisms;
    private final Map<String, Map<String, Object>> authenticators;
    private final Map<String, Map<String, Object>> authorizers;
    private final Map<String, Map<String, Object>> tokenManagers;
    private final String logFilePath;
    private final Level logLevel;
    private final boolean logToConsole;
    private final boolean logToFile;
    private final List<String> traceHeaders;
    private final int requestsLimit;
    private final int ioThreads;
    private final int workerThreads;
    private final int bufferSize;
    private final boolean directBuffers;
    private final boolean forceGzipEncoding;
    private final Map<String, Object> connectionOptions;
    private final Integer logExchangeDump;
    private final boolean ansiConsole;
    private final boolean allowUnescapedCharactersInUrl;

    /**
     * Creates a new instance of Configuration with defaults values.
     */
    public Configuration() {
        ansiConsole = true;

        httpsListener = DEFAULT_HTTPS_LISTENER;
        httpsPort = DEFAULT_HTTPS_PORT;
        httpsHost = DEFAULT_HTTPS_HOST;

        httpListener = DEFAULT_HTTP_LISTENER;
        httpPort = DEFAULT_HTTP_PORT;
        httpHost = DEFAULT_HTTP_HOST;

        instanceName = DEFAULT_INSTANCE_NAME;

        useEmbeddedKeystore = true;
        keystoreFile = null;
        keystorePassword = null;
        certPassword = null;

        proxies = new ArrayList<>();
        initDefaultProxy();

        pluginsDirectory = "plugins";

        pluginsArgs = new LinkedHashMap<>();
        authMechanisms = new LinkedHashMap<>();
        authenticators = new LinkedHashMap<>();
        authorizers = null;
        tokenManagers = new HashMap<>();

        logFilePath = URLUtils.removeTrailingSlashes(System.getProperty("java.io.tmpdir"))
                .concat(File.separator + "restheart-security.log");
        logToConsole = true;
        logToFile = true;
        logLevel = Level.INFO;

        traceHeaders = Collections.emptyList();

        requestsLimit = 100;
        ioThreads = 2;
        workerThreads = 32;
        bufferSize = 16384;
        directBuffers = true;
        forceGzipEncoding = false;
        logExchangeDump = 0;
        connectionOptions = Maps.newHashMap();
        allowUnescapedCharactersInUrl = true;
    }

    /**
     * Creates a new instance of Configuration from the configuration file For
     * any missing property the default value is used.
     *
     * @param confFilePath the path of the configuration file
     * @throws org.restheart.ConfigurationException
     */
    public Configuration(final Path confFilePath) throws ConfigurationException {
        this(confFilePath, false);
    }

    /**
     * Creates a new instance of Configuration from the configuration file For
     * any missing property the default value is used.
     *
     * @param confFilePath the path of the configuration file
     * @param silent
     * @throws org.restheart.ConfigurationException
     */
    public Configuration(final Path confFilePath, boolean silent) throws ConfigurationException {
        this(getConfigurationFromFile(confFilePath), silent);
    }

    /**
     * Creates a new instance of Configuration from the configuration file For
     * any missing property the default value is used.
     *
     * @param conf the key-value configuration map
     * @param silent
     * @throws org.restheart.ConfigurationException
     */
    @SuppressWarnings("deprecation")
    public Configuration(Map<String, Object> conf, boolean silent) throws ConfigurationException {
        this.silent = silent;

        // check if service configuration follows old (<2.0)format
        if (conf.get(SERVICES_KEY) != null) {
            LOGGER.error("The services configuration section is obsolete. Refer to https://restheart.org/docs/upgrade-to-v4.2 and upgrade it.");
            throw new ConfigurationException("Wrong Services configuration");
        }

        ansiConsole = getAsBoolean(conf, ANSI_CONSOLE_KEY, true);

        httpsListener = getAsBoolean(conf, HTTPS_LISTENER, true);
        httpsPort = getAsInteger(conf, HTTPS_PORT_KEY, DEFAULT_HTTPS_PORT);
        httpsHost = getAsString(conf, HTTPS_HOST_KEY, DEFAULT_HTTPS_HOST);

        httpListener = getAsBoolean(conf, HTTP_LISTENER_KEY, false);
        httpPort = getAsInteger(conf, HTTP_PORT_KEY, DEFAULT_HTTP_PORT);
        httpHost = getAsString(conf, HTTP_HOST_KEY, DEFAULT_HTTP_HOST);

        instanceName = getAsString(conf, INSTANCE_NAME_KEY, DEFAULT_INSTANCE_NAME);
        useEmbeddedKeystore = getAsBoolean(conf, USE_EMBEDDED_KEYSTORE_KEY, true);
        keystoreFile = getAsString(conf, KEYSTORE_FILE_KEY, null);
        keystorePassword = getAsString(conf, KEYSTORE_PASSWORD_KEY, null);
        certPassword = getAsString(conf, CERT_PASSWORD_KEY, null);

        proxies = getAsListOfMaps(conf, PROXY_KEY, new ArrayList<>());

        if (proxies.isEmpty()) {
            initDefaultProxy();
        }

        pluginsDirectory = getAsString(conf, PLUGINS_DIRECTORY_PATH_KEY, "plugins");

        pluginsArgs = getAsMapOfMaps(conf, PLUGINS_ARGS_KEY, new LinkedHashMap<>());

        authMechanisms = getAsMapOfMaps(conf, AUTH_MECHANISMS_KEY, new LinkedHashMap<>());

        // check if configuration follows old (<2.0)format
        if (authMechanisms.isEmpty()) {
            // check if exception is due to old configuration format
            var old = conf.get(AUTH_MECHANISMS_KEY);

            if (old != null
                    && old instanceof List
                    && checkPre20Confs((List) old)) {
                LOGGER.error("The auth-mechanisms configuration section follows old format. Refer to https://restheart.org/docs/upgrade-to-v4.2 and upgrade it.");
                throw new ConfigurationException("Wrong Authentication Mechanisms configuration");
            }
        }

        authenticators = getAsMapOfMaps(conf, AUTHENTICATORS_KEY, new LinkedHashMap<>());

        // check if configuration follows old (<2.0)format
        if (authenticators.isEmpty()) {
            // check if exception is due to old configuration format
            var old = conf.get(AUTHENTICATORS_KEY);

            if (old != null
                    && old instanceof List
                    && checkPre20Confs((List) old)) {
                LOGGER.error("The authenticator configuration section follows old format. Refer to https://restheart.org/docs/upgrade-to-v4.2 and upgrade it.");
                throw new ConfigurationException("Wrong Authenticators configuration");
            }
        }

        authorizers = getAsMapOfMaps(conf, AUTHORIZERS_KEY, new LinkedHashMap<>());
        
        // check if configuration follows old (<2.0)format
        if (authorizers.isEmpty()) {
            // check if exception is due to old configuration format
            var old = conf.get(AUTHORIZERS_KEY);

            if (old != null
                    && old instanceof List
                    && checkPre20Confs((List) old)) {
                LOGGER.error("The authorizers configuration section follows old format. Refer to https://restheart.org/docs/upgrade-to-v4.2 and upgrade it.");
                throw new ConfigurationException("Wrong Authorizers configuration");
            }
        }

        tokenManagers = getAsMapOfMaps(conf, TOKEN_MANAGER, new LinkedHashMap<>());

        // check if configuration follows old (<2.0)format
        if (checkPre20Confs(tokenManagers)) {
            // check if exception is due to old configuration format
            LOGGER.error("The token-manager configuration section follows old format. Refer to https://restheart.org/docs/upgrade-to-v4.2 and upgrade it.");
            throw new ConfigurationException("Wrong Token Manager configuration");
        }

        logFilePath = getAsString(conf, LOG_FILE_PATH_KEY, URLUtils
                .removeTrailingSlashes(System.getProperty("java.io.tmpdir"))
                .concat(File.separator + "restheart-security.log"));
        String _logLevel = getAsString(conf, LOG_LEVEL_KEY, "INFO");
        logToConsole = getAsBoolean(conf, ENABLE_LOG_CONSOLE_KEY, true);
        logToFile = getAsBoolean(conf, ENABLE_LOG_FILE_KEY, true);

        Level level;
        try {
            level = Level.valueOf(_logLevel);
        }
        catch (Exception e) {
            if (!silent) {
                LOGGER.info("wrong value for parameter {}: {}. using its default value {}",
                        "log-level", _logLevel, "INFO");
            }
            level = Level.INFO;
        }

        logLevel = level;

        traceHeaders = getAsListOfStrings(conf, REQUESTS_LOG_TRACE_HEADERS_KEY, Collections.emptyList());

        requestsLimit = getAsInteger(conf, REQUESTS_LIMIT_KEY, 100);
        ioThreads = getAsInteger(conf, IO_THREADS_KEY, 2);
        workerThreads = getAsInteger(conf, WORKER_THREADS_KEY, 32);
        bufferSize = getAsInteger(conf, BUFFER_SIZE_KEY, 16384);
        directBuffers = getAsBoolean(conf, DIRECT_BUFFERS_KEY, true);
        forceGzipEncoding = getAsBoolean(conf, FORCE_GZIP_ENCODING_KEY, false);
        logExchangeDump = getAsInteger(conf, LOG_REQUESTS_LEVEL_KEY, 0);
        connectionOptions = getAsMap(conf, CONNECTION_OPTIONS_KEY);
        allowUnescapedCharactersInUrl = getAsBoolean(conf, ALLOW_UNESCAPED_CHARACTERS_IN_URL, true);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getConfigurationFromFile(final Path confFilePath) throws ConfigurationException {
        Yaml yaml = new Yaml();
        Map<String, Object> conf = null;

        try (FileInputStream fis = new FileInputStream(confFilePath.toFile())) {
            conf = (Map<String, Object>) yaml.load(fis);
        }
        catch (FileNotFoundException fne) {
            throw new ConfigurationException("Configuration file not found", fne);
        }
        catch (Throwable t) {
            throw new ConfigurationException("Error parsing the configuration file", t);
        }

        return conf;
    }

    static boolean isParametric(final Path confFilePath) throws IOException {
        Scanner sc = new Scanner(confFilePath, "UTF-8");

        return sc.findAll(Pattern.compile("\\{\\{.*\\}\\}"))
                .limit(1)
                .count() > 0;
    }

    private void initDefaultProxy() {
        var entry = new HashMap();

        LOGGER.warn("No proxies defined via configuration, "
                + "assuming default proxy: / -> ajp://localhost:8009");

        entry.put(ConfigurationKeys.PROXY_LOCATION_KEY, "/");
        entry.put(ConfigurationKeys.PROXY_PASS_KEY, "ajp://localhost:8009");
        entry.put(ConfigurationKeys.PROXY_NAME, "restheart");

        this.proxies.add(entry);
    }

    /**
     *
     * @param integers
     * @return
     */
    public static int[] convertListToIntArray(List<Object> integers) {
        int[] ret = new int[integers.size()];
        Iterator<Object> iterator = integers.iterator();
        for (int i = 0; i < ret.length; i++) {
            Object o = iterator.next();

            if (o instanceof Integer) {
                ret[i] = (Integer) o;
            } else {
                return new int[0];
            }
        }

        return ret;
    }

    @Override
    public String toString() {
        return "Configuration{"
                + "silent=" + silent
                + ", httpsListener=" + httpsListener
                + ", httpsPort=" + httpsPort
                + ", httpsHost=" + httpsHost
                + ", httpListener=" + httpListener
                + ", httpPort=" + httpPort
                + ", httpHost=" + httpHost
                + ", instanceName=" + instanceName
                + ", pluginsDirectory=" + pluginsDirectory
                + ", useEmbeddedKeystore=" + useEmbeddedKeystore
                + ", keystoreFile=" + keystoreFile
                + ", keystorePassword=" + keystorePassword
                + ", certPassword=" + certPassword
                + ", proxies=" + proxies
                + ", pluginsArgs=" + pluginsArgs
                + ", authMechanisms=" + authMechanisms
                + ", authenticators=" + authenticators
                + ", authorizers=" + authorizers
                + ", tokenManager=" + tokenManagers
                + ", logFilePath=" + logFilePath
                + ", logLevel=" + logLevel
                + ", logToConsole=" + logToConsole
                + ", logToFile=" + logToFile
                + ", traceHeaders=" + traceHeaders
                + ", requestsLimit=" + requestsLimit
                + ", ioThreads=" + ioThreads
                + ", workerThreads=" + workerThreads
                + ", bufferSize=" + bufferSize
                + ", directBuffers=" + directBuffers
                + ", forceGzipEncoding=" + forceGzipEncoding
                + ", connectionOptions=" + connectionOptions
                + ", logExchangeDump=" + logExchangeDump
                + ", ansiConsole=" + ansiConsole
                + ", allowUnescapedCharactersInUrl="
                + allowUnescapedCharactersInUrl + '}';
    }

    /**
     * @return the proxies
     */
    public List<Map<String, Object>> getProxies() {
        return proxies;
    }

    /**
     *
     * @return true if the Ansi console is enabled
     */
    public boolean isAnsiConsole() {
        return ansiConsole;
    }

    /**
     *
     * @param conf
     * @param key
     * @param defaultValue
     * @return
     */
    private <V extends Object> V getOrDefault(final Map<String, Object> conf, final String key, final V defaultValue) {
        return getOrDefault(conf, key, defaultValue, this.silent);
    }

    /**
     * @return the httpsListener
     */
    public boolean isHttpsListener() {
        return httpsListener;
    }

    /**
     * @return the httpsPort
     */
    public int getHttpsPort() {
        return httpsPort;
    }

    /**
     * @return the httpsHost
     */
    public String getHttpsHost() {
        return httpsHost;
    }

    /**
     * @return the httpListener
     */
    public boolean isHttpListener() {
        return httpListener;
    }

    /**
     * @return the httpPort
     */
    public int getHttpPort() {
        return httpPort;
    }

    /**
     * @return the httpHost
     */
    public String getHttpHost() {
        return httpHost;
    }

    /**
     * @return the pluginsDirectory
     */
    public String getPluginsDirectory() {
        return this.pluginsDirectory;
    }

    /**
     * @return the useEmbeddedKeystore
     */
    public boolean isUseEmbeddedKeystore() {
        return useEmbeddedKeystore;
    }

    /**
     * @return the keystoreFile
     */
    public String getKeystoreFile() {
        return keystoreFile;
    }

    /**
     * @return the keystorePassword
     */
    public String getKeystorePassword() {
        return keystorePassword;
    }

    /**
     * @return the certPassword
     */
    public String getCertPassword() {
        return certPassword;
    }

    /**
     * @return the logFilePath
     */
    public String getLogFilePath() {
        return logFilePath;
    }

    /**
     * @return the logLevel
     */
    public Level getLogLevel() {

        String logbackConfigurationFile = System.getProperty("logback.configurationFile");
        if (logbackConfigurationFile != null && !logbackConfigurationFile.isEmpty()) {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger logger = loggerContext.getLogger("org.restheart.security");
            return logger.getLevel();
        }

        return logLevel;
    }

    /**
     * @return the logToConsole
     */
    public boolean isLogToConsole() {
        return logToConsole;
    }

    /**
     * @return the logToFile
     */
    public boolean isLogToFile() {
        return logToFile;
    }

    public List<String> getTraceHeaders() {
        return Collections.unmodifiableList(traceHeaders);
    }

    /**
     * @return the ioThreads
     */
    public int getIoThreads() {
        return ioThreads;
    }

    /**
     * @return the workerThreads
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * @return the bufferSize
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * @return the directBuffers
     */
    public boolean isDirectBuffers() {
        return directBuffers;
    }

    /**
     * @return the forceGzipEncoding
     */
    public boolean isForceGzipEncoding() {
        return forceGzipEncoding;
    }

    /**
     * @return the pluginsArgs
     */
    public Map<String, Map<String, Object>> getPluginsArgs() {
        return Collections.unmodifiableMap(pluginsArgs);
    }

    /**
     * @return the authMechanisms
     */
    public Map<String, Map<String, Object>> getAuthMechanisms() {
        return authMechanisms;
    }

    /**
     * @return the authenticators
     */
    public Map<String, Map<String, Object>> getAuthenticators() {
        return authenticators;
    }

    /**
     * @return the authorizers
     */
    public Map<String, Map<String, Object>> getAuthorizers() {
        return authorizers;
    }

    /**
     * @return the requestsLimit
     */
    public int getRequestsLimit() {
        return requestsLimit;
    }

    /**
     * @return the tokenManagers
     */
    public Map<String, Map<String, Object>> getTokenManagers() {
        return tokenManagers;
    }

    /**
     *
     * @return the logExchangeDump Boolean
     */
    public Integer logExchangeDump() {
        return logExchangeDump;
    }

    /**
     * @return the connectionOptions
     */
    public Map<String, Object> getConnectionOptions() {
        return Collections.unmodifiableMap(connectionOptions);
    }

    /**
     * @return the instanceName
     */
    public String getInstanceName() {
        return instanceName;
    }

    public boolean isAllowUnescapedCharactersInUrl() {
        return allowUnescapedCharactersInUrl;
    }

    /**
     *
     * @return the base URL of restheart proxy identified by proxy configuration
     * property name="restheart"
     * @throws ConfigurationException
     */
    public URI getRestheartBaseUrl() throws ConfigurationException {
        var __proxyPass = Bootstrapper.getConfiguration().getProxies().stream()
                .filter(e -> e.containsKey(ConfigurationKeys.PROXY_NAME))
                .filter(e -> "restheart".equals(e.get(ConfigurationKeys.PROXY_NAME)))
                .map(e -> e.get(ConfigurationKeys.PROXY_PASS_KEY))
                .findFirst();

        if (__proxyPass.isEmpty()) {
            throw new ConfigurationException("No proxy pass defined "
                    + "for proxy 'restheart'");
        }

        var _proxyPass = __proxyPass.get();

        String proxyPass;

        if (_proxyPass instanceof String) {
            proxyPass = (String) _proxyPass;
        } else if (_proxyPass instanceof List) {
            var listOfProxyPass = (List) _proxyPass;

            if (listOfProxyPass.isEmpty()
                    || !(listOfProxyPass.get(0) instanceof String)) {
                throw new ConfigurationException("Wrong proxy pass for proxy 'restheart' "
                        + _proxyPass);
            } else {
                proxyPass = (String) listOfProxyPass.get(0);
            }
        } else {
            throw new ConfigurationException("Wrong proxy pass for proxy 'restheart' "
                    + _proxyPass);
        }

        try {
            return URI.create(proxyPass);
        }
        catch (IllegalArgumentException ex) {
            throw new ConfigurationException("Wrong proxy pass ULR "
                    + proxyPass, ex);
        }
    }

    /**
     *
     * @return the location of restheart proxy identified by proxy configuration
     * property name="restheart"
     * @throws ConfigurationException
     */
    public URI getRestheartLocation() throws ConfigurationException {
        var __proxyLocation = Bootstrapper.getConfiguration().getProxies().stream()
                .filter(e -> e.containsKey(ConfigurationKeys.PROXY_NAME))
                .filter(e -> "restheart".equals(e.get(ConfigurationKeys.PROXY_NAME)))
                .map(e -> e.get(ConfigurationKeys.PROXY_LOCATION_KEY))
                .findFirst();

        if (__proxyLocation.isEmpty()) {
            throw new ConfigurationException("No proxy pass defined "
                    + "for proxy 'restheart'");
        }

        var _proxyLocation = __proxyLocation.get();

        String proxyLocation;

        if (_proxyLocation instanceof String) {
            proxyLocation = (String) _proxyLocation;
        } else {
            throw new ConfigurationException("Wrong proxy location for proxy 'restheart' "
                    + _proxyLocation);
        }

        try {
            return URI.create(proxyLocation);
        }
        catch (IllegalArgumentException ex) {
            throw new ConfigurationException("Wrong proxy location URI "
                    + proxyLocation, ex);
        }
    }

    /**
     *
     * @param conf
     * @param key
     * @param defaultValue
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getAsListOfMaps(final Map<String, Object> conf, final String key,
            final List<Map<String, Object>> defaultValue) {
        if (conf == null) {
            if (!silent) {
                LOGGER.trace("parameter {} not specified in the "
                        + "configuration file. using its default value {}",
                        key, defaultValue);
            }

            return defaultValue;
        }

        Object o = conf.get(key);

        if (o == null) {
            if (!silent) {
                LOGGER.trace("configuration parameter {} not specified in the "
                        + "configuration file, using its default value {}",
                        key, defaultValue);
            }
            return defaultValue;
        } else if (o instanceof List) {
            try {
                return (List<Map<String, Object>>) o;
            }
            catch (Throwable t) {
                LOGGER.warn("wrong configuration parameter {}", key);
                return defaultValue;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong configuration parameter {}, expecting an array of objects",
                        key, defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     *
     * @param conf
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> getAsMapOfMaps(
            final Map<String, Object> conf,
            final String key,
            final Map<String, Map<String, Object>> defaultValue) {
        if (conf == null) {
            if (!silent) {
                LOGGER.trace("parameter {} not specified in the "
                        + "configuration file. using its default value {}",
                        key, defaultValue);
            }

            return defaultValue;
        }

        Object o = conf.get(key);

        if (o == null) {
            if (!silent) {
                LOGGER.trace("configuration parameter {} not specified in the "
                        + "configuration file, using its default value {}",
                        key, defaultValue);
            }
            return defaultValue;
        } else if (o instanceof Map) {
            try {
                return (Map<String, Map<String, Object>>) o;
            }
            catch (Throwable t) {
                LOGGER.warn("wrong configuration parameter {}", key);
                return defaultValue;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong configuration parameter {}, expecting a map of maps",
                        key, defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     *
     * @param conf
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getAsMap(final Map<String, Object> conf, final String key) {
        if (conf == null) {
            if (!silent) {
                LOGGER.trace("parameter {} not specified in the "
                        + "configuration file. using its default value {}",
                        key, null);
            }

            return null;
        }

        Object o = conf.get(key);

        if (o instanceof Map) {
            try {
                return (Map<String, Object>) o;
            }
            catch (Throwable t) {
                LOGGER.warn("wrong configuration parameter {}", key);
                return null;
            }
        } else {
            if (!silent) {
                LOGGER.trace("configuration parameter {} not specified in the configuration file.", key);
            }
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getAsListOfStrings(final Map<String, Object> conf, final String key, final List<String> defaultValue) {
        if (conf == null || conf.get(key) == null) {
            // if default value is null there is no default value actually
            if (defaultValue != null && !silent) {
                LOGGER.trace("parameter {} not specified in the configuration file."
                        + " Using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (conf.get(key) instanceof List) {
            if (!silent) {
                LOGGER.debug("paramenter {} set to {}", key, conf.get(key));
            }

            List<String> ret = ((List<String>) conf.get(key));

            if (ret.isEmpty()) {
                if (!silent) {
                    LOGGER.warn("wrong value for parameter {}: {}."
                            + " Using its default value {}", key, conf.get(key), defaultValue);
                }
                return defaultValue;
            } else {
                return ret;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong value for parameter {}: {}."
                        + " Using its default value {}", key, conf.get(key), defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     *
     * @param <V>
     * @param conf
     * @param key
     * @param defaultValue
     * @param silent
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <V extends Object> V getOrDefault(
            final Map<String, Object> conf,
            final String key,
            final V defaultValue,
            boolean silent) {

        if (conf == null || conf.get(key) == null) {
            // if default value is null there is no default value actually
            if (defaultValue != null && !silent) {
                LOGGER.warn("Parameter \"{}\" not specified in the configuration file. "
                        + "using its default value \"{}\"", key, defaultValue);
            }
            return defaultValue;
        }

        try {
            if (!silent) {
                LOGGER.trace("configuration paramenter \"{}\" set to \"{}\"", key, conf.get(key));
            }
            return (V) conf.get(key);
        }
        catch (Throwable t) {
            if (!silent) {
                LOGGER.warn("Wrong configuration parameter \"{}\": \"{}\". using its default value \"{}\"",
                        key, conf.get(key), defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     *
     * @param key
     * @return the environment or java property variable, if found
     */
    private static String overriddenValueFromEnv(final String key) {
        String shellKey = "RESTHEART_SECURITY_" + key.toUpperCase().replaceAll("-", "_");
        String envValue = System.getProperty(key);

        if (envValue == null) {
            envValue = System.getProperty(shellKey);
        }

        if (envValue == null) {
            envValue = System.getenv(shellKey);
        }
        if (null != envValue) {
            LOGGER.warn(">>> Found environment variable '{}': overriding parameter '{}' with value '{}'",
                    shellKey, key, envValue);
        }
        return envValue;
    }

    private Boolean getAsBoolean(final Map<String, Object> conf, final String key, final Boolean defaultValue) {
        String envValue = overriddenValueFromEnv(key);
        if (envValue != null) {
            return Boolean.valueOf(envValue);
        }
        return getOrDefault(conf, key, defaultValue);
    }

    private String getAsString(final Map<String, Object> conf, final String key, final String defaultValue) {
        String envValue = overriddenValueFromEnv(key);
        if (envValue != null) {
            return envValue;
        }
        return getOrDefault(conf, key, defaultValue);
    }

    private Integer getAsInteger(final Map<String, Object> conf, final String key, final Integer defaultValue) {
        String envValue = overriddenValueFromEnv(key);
        if (envValue != null) {
            return Integer.valueOf(envValue);
        }
        return getOrDefault(conf, key, defaultValue);
    }

    private Long getAsLong(final Map<String, Object> conf, final String key, final Long defaultValue) {
        String envValue = overriddenValueFromEnv(key);
        if (envValue != null) {
            return Long.valueOf(envValue);
        }
        return getOrDefault(conf, key, defaultValue);
    }

    /**
     * this checks if an old (< 2.0) configuration is found
     *
     * old format:
     *
     * <pre>
     * auth-mechanisms:
     *  - name: basicAuthMechanism
     *    class: org.restheart.security.plugins.mechanisms.BasicAuthMechanism
     *    args:
     *      argParam1: value
     *      argParam2: value
     * </pre>
     *
     * new format
     *
     * <pre>
     * auth-mechanisms:
     *  - basicAuthMechanism:
     *      argParam1: value
     *      argParam2: value
     * </pre>
     *
     * @param conf
     * @return true if an old (< 2.0) configuration is found
     */
    private static boolean checkPre20Confs(List<Map<String, Object>> conf) {
        return conf != null && conf.stream()
                .anyMatch(e -> e.containsKey("name")
                || e.containsKey("class")
                || e.containsKey("args"));
    }

    private static boolean checkPre20Confs(Map<String, Map<String, Object>> conf) {
        return conf != null && (conf.containsKey("name")
                || conf.containsKey("class")
                || conf.containsKey("args"));
    }
}
