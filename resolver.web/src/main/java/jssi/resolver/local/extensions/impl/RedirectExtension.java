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
package jssi.resolver.local.extensions.impl;

import java.util.Map;

import foundation.identity.did.DIDURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uniresolver.ResolutionException;
import jssi.resolver.local.LocalUniResolver;
import jssi.resolver.local.extensions.Extension;
import jssi.resolver.local.extensions.ExtensionStatus;
import jssi.resolver.local.extensions.Extension.AbstractExtension;
import uniresolver.result.ResolveResult;

public class RedirectExtension extends AbstractExtension implements Extension {

    private static final Logger LOG = LoggerFactory.getLogger(RedirectExtension.class);

    @Override
    public ExtensionStatus afterResolve(String identifier, DIDURL didUrl, Map<String, String> options, ResolveResult resolveResult, LocalUniResolver localUniResolver) throws ResolutionException {

        if (!resolveResult.getDidDocumentMetadata().containsKey("redirect")) {
            return ExtensionStatus.DEFAULT;
        }

        while (resolveResult.getDidDocumentMetadata().containsKey("redirect")) {

            String resolveIdentifier = (String) resolveResult.getDidDocumentMetadata().get("redirect");
            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolving identifier: " + resolveIdentifier);
            }

            ResolveResult previousResolveResult = resolveResult.copy();
            resolveResult.reset();
            resolveResult.getDidResolutionMetadata().put("previous", previousResolveResult);

            ResolveResult driverResolveResult = ResolveResult.build();
            localUniResolver.resolveWithDrivers(resolveIdentifier, driverResolveResult);

            resolveResult.setDidDocument(driverResolveResult.getDidDocument());
            resolveResult.setDidDocumentMetadata(driverResolveResult.getDidDocumentMetadata());

            resolveResult.getDidResolutionMetadata().putAll(driverResolveResult.getDidResolutionMetadata());
        }

        return ExtensionStatus.DEFAULT;
    }
}
