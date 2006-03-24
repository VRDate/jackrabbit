/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.jca;

import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.XASession;

import javax.jcr.Credentials;
import javax.jcr.RepositoryException;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.security.auth.Subject;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Set;

/**
 * Implements the JCA ManagedConnectionFactory contract.
 */
public final class JCAManagedConnectionFactory
        implements ManagedConnectionFactory {

    /**
     * Home directory.
     */
    private String homeDir;

    /**
     * Config file.
     */
    private String configFile;

    /**
     * Repository.
     */
    private transient RepositoryImpl repository;

    /**
     * Log writer.
     */
    private transient PrintWriter logWriter;

    /**
     * Return the repository home directory.
     */
    public String getHomeDir() {
        return homeDir;
    }

    /**
     * Set the repository home directory.
     */
    public void setHomeDir(String homeDir) {
        this.homeDir = homeDir;
    }

    /**
     * Return the repository configuration file.
     */
    public String getConfigFile() {
        return configFile;
    }

    /**
     * Set the repository configuration file.
     */
    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    /**
     * Get the log writer.
     */
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    /**
     * Set the log writer.
     */
    public void setLogWriter(PrintWriter logWriter)
            throws ResourceException {
        this.logWriter = logWriter;
    }

    /**
     * Creates a Connection Factory instance.
     */
    public Object createConnectionFactory()
            throws ResourceException {
        return createConnectionFactory(new JCAConnectionManager());
    }

    /**
     * Creates a Connection Factory instance.
     */
    public Object createConnectionFactory(ConnectionManager cm)
            throws ResourceException {
        createRepository();
        JCARepositoryHandle handle = new JCARepositoryHandle(this, cm);
        log("Created repository handle (" + handle + ")");
        return handle;
    }

    /**
     * Create a new session.
     */
    private XASession openSession(JCAConnectionRequestInfo cri)
            throws ResourceException {
        createRepository();
        Credentials creds = cri.getCredentials();
        String workspace = cri.getWorkspace();

        try {
            XASession session = (XASession) getRepository().login(creds, workspace);
            log("Created session (" + session + ")");
            return session;
        } catch (RepositoryException e) {
            log("Failed to create session", e);
            throw new ResourceException(e);
        }
    }

    /**
     * Creates a new physical connection to the underlying EIS resource manager.
     */
    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo cri)
            throws ResourceException {
        return createManagedConnection((JCAConnectionRequestInfo) cri);
    }

    /**
     * Creates a new physical connection to the underlying EIS resource manager.
     */
    private ManagedConnection createManagedConnection(JCAConnectionRequestInfo cri)
            throws ResourceException {
        return new JCAManagedConnection(this, cri, openSession(cri));
    }

    /**
     * Returns a matched connection from the candidate set of connections.
     */
    public ManagedConnection matchManagedConnections(Set set, Subject subject, ConnectionRequestInfo cri)
            throws ResourceException {
        for (Iterator i = set.iterator(); i.hasNext();) {
            Object next = i.next();

            if (next instanceof JCAManagedConnection) {
                JCAManagedConnection mc = (JCAManagedConnection) next;
                if (equals(mc.getManagedConnectionFactory())) {
                    JCAConnectionRequestInfo otherCri = mc.getConnectionRequestInfo();
                    if (equals(cri, otherCri)) {
                        return mc;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Return the repository.
     */
    public RepositoryImpl getRepository() {
        return repository;
    }

    /**
     * Log a message.
     */
    public void log(String message) {
        log(message, null);
    }

    /**
     * Log a message.
     */
    public void log(String message, Throwable exception) {
        if (logWriter != null) {
            logWriter.println(message);

            if (exception != null) {
                exception.printStackTrace(logWriter);
            }
        }
    }

    /**
     * Return the hash code.
     */
    public int hashCode() {
        int result = homeDir != null ? homeDir.hashCode() : 0;
        result = 37 * result + (configFile != null ? configFile.hashCode() : 0);
        return result;
    }

    /**
     * Return true if equals.
     */
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof JCAManagedConnectionFactory) {
            return equals((JCAManagedConnectionFactory) o);
        } else {
            return false;
        }
    }

    /**
     * Return true if equals.
     */
    private boolean equals(JCAManagedConnectionFactory o) {
        return equals(homeDir, o.homeDir) &&
               equals(configFile, o.configFile);
    }

    /**
     * Return true if equals.
     */
    private boolean equals(Object o1, Object o2) {
        if (o1 == o2) {
            return true;
        } else if ((o1 == null) || (o2 == null)) {
            return false;
        } else {
            return o1.equals(o2);
        }
    }

    /**
     * Shutdown the repository.
     */
    protected void finalize() {
        shutdownRepository();
    }

    /**
     * Create repository.
     */
    private void createRepository()
            throws ResourceException {
        if (repository == null) {
            // Check the home directory
            if ((homeDir == null) || homeDir.equals("")) {
                throw new ResourceException("Property 'homeDir' not set");
            }

            // Check the config file
            if ((configFile == null) || configFile.equals("")) {
                throw new ResourceException("Property 'configFile' not set");
            }

            try {
                JCARepositoryManager mgr = JCARepositoryManager.getInstance();
                repository = mgr.createRepository(homeDir, configFile);
                log("Created repository (" + repository + ")");
            } catch (RepositoryException e) {
                log("Failed to create repository", e);
                throw new ResourceException(e);
            }
        }
    }

    /**
     * Shutdown the repository.
     */
    private void shutdownRepository() {
        JCARepositoryManager mgr = JCARepositoryManager.getInstance();
        mgr.shutdownRepository(homeDir, configFile);
    }
}
