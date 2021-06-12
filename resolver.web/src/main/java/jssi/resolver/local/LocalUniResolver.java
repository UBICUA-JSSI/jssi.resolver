/*
 *
 *  * Copyright 2021 UBICUA.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package jssi.resolver.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import foundation.identity.did.DIDDocument;
import foundation.identity.did.DIDURL;
import foundation.identity.did.parser.ParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

import uniresolver.ResolutionException;
import uniresolver.UniResolver;
import uniresolver.driver.Driver;
import jssi.resolver.local.extensions.Extension;
import jssi.resolver.local.extensions.ExtensionStatus;
import uniresolver.result.ResolveResult;


public class LocalUniResolver implements UniResolver, Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(LocalUniResolver.class);

    private List<Extension> extensions = new ArrayList<Extension>();
    private final Drivers drivers;
    
    public LocalUniResolver(Drivers drivers) {
        this.drivers = drivers;
    }
    
    @Override
    public ResolveResult resolve(String identifier) throws ResolutionException {
        return resolve(identifier, null);
    }

    @Override
    public ResolveResult resolve(String identifier, Map<String, String> options) throws ResolutionException {

        if (identifier == null) {
            throw new NullPointerException();
        }

        if (drivers.getDrivers().isEmpty()) {
            throw new ResolutionException("No drivers configured.");
        }
        // start time
        long start = System.currentTimeMillis();
        // prepare resolve result
        ResolveResult resolveResult = ResolveResult.build();
        ExtensionStatus extensionStatus = new ExtensionStatus();
        // parse DID URL
        DIDURL didUrl = null;

        try {
            didUrl = DIDURL.fromString(identifier);
            resolveResult.getDidResolutionMetadata().put("didUrl", didUrl);
            LOG.debug("Identifier " + identifier + " is a valid DID URL: " + didUrl);
        } catch (IllegalArgumentException | ParserException ex) {
            LOG.debug("Identifier " + identifier + " is not a valid DID URL: " + ex.getMessage());
        }

        // execute extensions (before)
        if (!extensionStatus.skipExtensionsBefore()) {
            for (Extension extension : this.getExtensions()) {
                extensionStatus.or(extension.beforeResolve(identifier, didUrl, options, resolveResult, this));
                if (extensionStatus.skipExtensionsBefore()) {
                    break;
                }
            }
        }

        // try all drivers
        if (!extensionStatus.skipDriver()) {
            String resolveIdentifier = didUrl != null ? didUrl.getDid().getDidString() : identifier;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolving identifier: " + resolveIdentifier);
            }

            ResolveResult driverResolveResult = ResolveResult.build();
            resolveWithDrivers(resolveIdentifier, driverResolveResult);
            resolveResult.setDidDocument(driverResolveResult.getDidDocument());
            resolveResult.setDidDocumentMetadata(driverResolveResult.getDidDocumentMetadata());
            resolveResult.getDidResolutionMetadata().putAll(driverResolveResult.getDidResolutionMetadata());
        }

        // execute extensions (after)
        if (!extensionStatus.skipExtensionsAfter()) {
            for (Extension extension : this.getExtensions()) {
                extensionStatus.or(extension.afterResolve(identifier, didUrl, options, resolveResult, this));
                if (extensionStatus.skipExtensionsAfter()) {
                    break;
                }
            }
        }

        // stop time
        long stop = System.currentTimeMillis();
        resolveResult.getDidResolutionMetadata().put("duration", stop - start);
        // done
        return resolveResult;
    }

    @Override
    public Map<String, Map<String, Object>> properties() throws ResolutionException {

        if (drivers.getDrivers().isEmpty()) {
            throw new ResolutionException("No drivers configured.");
        }

        Map<String, Map<String, Object>> properties = new HashMap<>();

        for (Entry<String, Driver> driver : drivers.getDrivers().entrySet()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Loading properties for driver " + driver.getKey() + " (" + driver.getValue().getClass().getSimpleName() + ")");
            }

            Map<String, Object> driverProperties = driver.getValue().properties();
            if (driverProperties == null) {
                driverProperties = Collections.emptyMap();
            }

            properties.put(driver.getKey(), driverProperties);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Loading properties: " + properties);
        }

        return properties;
    }

    public void resolveWithDrivers(String identifier, ResolveResult resolveResult) throws ResolutionException {

        ResolveResult driverResolveResult = null;
        String usedDriverId = null;

        for (Entry<String, Driver> driver : drivers.getDrivers().entrySet()) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("Attemping to resolve " + identifier + " with driver " + driver.getValue().getClass());
            }
            driverResolveResult = driver.getValue().resolve(identifier);

            if (driverResolveResult != null && driverResolveResult.getDidDocument() != null && driverResolveResult.getDidDocument().getJsonObject().isEmpty()) {
                driverResolveResult.setDidDocument((DIDDocument) null);
            }

            if (driverResolveResult != null) {
                usedDriverId = driver.getKey();
                resolveResult.setDidDocument(driverResolveResult.getDidDocument());
                resolveResult.setDidDocumentMetadata(driverResolveResult.getDidDocumentMetadata());
                break;
            }
        }

        if (usedDriverId != null) {
            resolveResult.getDidResolutionMetadata().put("driverId", usedDriverId);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolved " + identifier + " with driver " + usedDriverId);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No result with " + drivers.getDrivers().size() + " drivers.");
            }
        }

        resolveResult.getDidResolutionMetadata().put("identifier", identifier);
    }

 
    @SuppressWarnings("unchecked")
    public <T extends Driver> T getDriver(Class<T> driverClass) {

        for (Driver driver : drivers.getDrivers().values()) {
            if (driverClass.isAssignableFrom(driver.getClass())) {
                return (T) driver;
            }
        }

        return null;
    }

    public List<Extension> getExtensions() {
        return this.extensions;
    }

    public void setExtensions(List<Extension> extensions) {
        this.extensions = extensions;
    }
}
