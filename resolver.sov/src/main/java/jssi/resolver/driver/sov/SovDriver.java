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
package jssi.resolver.driver.sov;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import foundation.identity.did.Authentication;
import foundation.identity.did.DIDDocument;
import foundation.identity.did.Service;
import foundation.identity.did.VerificationMethod;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.ledger.Ledger;
import org.hyperledger.indy.sdk.pool.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.leonard.Base58;
import uniresolver.ResolutionException;
import uniresolver.driver.Driver;

import uniresolver.result.ResolveResult;

public class SovDriver implements Driver {

    private static Logger LOG = LoggerFactory.getLogger(SovDriver.class);

    public static final Pattern DID_SOV_PATTERN = Pattern.compile("^did:sov:(?:(\\w[-\\w]*(?::\\w[-\\w]*)*):)?([1-9A-HJ-NP-Za-km-z]{21,22})$");

    public static final String[] DIDDOCUMENT_PUBLICKEY_TYPES = new String[]{"Ed25519VerificationKey2018"};
    public static final String[] DIDDOCUMENT_AUTHENTICATION_TYPES = new String[]{"Ed25519SignatureAuthentication2018"};

    private static final Gson gson = new Gson();
    private SovConfig config = null;
    
    public SovDriver(SovConfig config) {
        this.config = config;
    }

    @Override
    public ResolveResult resolve(String identifier) throws ResolutionException {

        // open pool
        synchronized (this) {
            if (config.getPoolMap().isEmpty() || config.getPoolVersionMap().isEmpty() || config.getWallet() == null || config.getResolverDid() == null) {
                throw new ResolutionException("General error resolver initialization");
            }
        }

        // parse identifier
        Matcher matcher = DID_SOV_PATTERN.matcher(identifier);
        if (!matcher.matches()) {
            return null;
        }

        String network = matcher.group(1);
        String targetDid = matcher.group(2);
        if (network == null || network.trim().isEmpty()) {
            network = "ubicua";
        }

        // find pool version
        Integer poolVersion = config.getPoolVersionMap().get(network);
        if (poolVersion == null) {
            throw new ResolutionException("No pool version for network: " + network);
        }

        // find pool
        Pool pool = config.getPoolMap().get(network);
        if (pool == null) {
            throw new ResolutionException("No pool for network: " + network);
        }

        // send GET_NYM request
        String getNymResponse;

        try {
            synchronized (this) {
                String getNymRequest = Ledger.buildGetNymRequest(config.getResolverDid(), targetDid).get();
                getNymResponse = Ledger.signAndSubmitRequest(pool, config.getWallet(), config.getResolverDid(), getNymRequest).get();
            }
        } catch (IndyException | InterruptedException | ExecutionException ex) {
            throw new ResolutionException("Cannot send GET_NYM request: " + ex.getMessage(), ex);
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("GET_NYM for " + targetDid + ": " + getNymResponse);
        }

        // GET_NYM response data
        JsonObject jsonGetNymResponse = gson.fromJson(getNymResponse, JsonObject.class);
        JsonObject jsonGetNymResult = jsonGetNymResponse == null ? null : jsonGetNymResponse.getAsJsonObject("result");
        JsonElement jsonGetNymData = jsonGetNymResult == null ? null : jsonGetNymResult.get("data");
        JsonObject jsonGetNymDataContent = (jsonGetNymData == null || jsonGetNymData instanceof JsonNull) ? null : gson.fromJson(jsonGetNymData.getAsString(), JsonObject.class);

        if (jsonGetNymDataContent == null) {
            return null;
        }

        // send GET_ATTR request
        String getAttrResponse;

        try {
            synchronized (this) {
                String getAttrRequest = Ledger.buildGetAttribRequest(config.getResolverDid(), targetDid, "endpoint", null, null).get();
                getAttrResponse = Ledger.signAndSubmitRequest(pool, config.getWallet(), config.getResolverDid(), getAttrRequest).get();
            }
        } catch (IndyException | InterruptedException | ExecutionException ex) {
            throw new ResolutionException("Cannot send GET_NYM request: " + ex.getMessage(), ex);
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("GET_ATTR for " + targetDid + ": " + getAttrResponse);
        }

        // GET_ATTR response data
        JsonObject jsonGetAttrResponse = gson.fromJson(getAttrResponse, JsonObject.class);
        JsonObject jsonGetAttrResult = jsonGetAttrResponse == null ? null : jsonGetAttrResponse.getAsJsonObject("result");
        JsonElement jsonGetAttrData = jsonGetAttrResult == null ? null : jsonGetAttrResult.get("data");
        JsonObject jsonGetAttrDataContent = (jsonGetAttrData == null || jsonGetAttrData instanceof JsonNull) ? null : gson.fromJson(jsonGetAttrData.getAsString(), JsonObject.class);

        // DID DOCUMENT did
        String did = identifier;

        // DID DOCUMENT verificationMethods
        JsonPrimitive jsonGetNymVerkey = jsonGetNymDataContent.getAsJsonPrimitive("verkey");
        String verkey = jsonGetNymVerkey == null ? null : jsonGetNymVerkey.getAsString();

        String expandedVerkey = expandVerkey(did, verkey);

        int keyNum = 0;
        List<VerificationMethod> verificationMethods;
        List<Authentication> authentications;

        URI keyId = URI.create(did + "#key-" + (++keyNum));

        VerificationMethod verificationMethod = VerificationMethod.builder()
                .id(keyId)
                .types(Arrays.asList(DIDDOCUMENT_PUBLICKEY_TYPES))
                .publicKeyBase58(expandedVerkey)
                .build();
        verificationMethods = Collections.singletonList(verificationMethod);

        Authentication authentication = Authentication.builder()
                .types(Arrays.asList(DIDDOCUMENT_AUTHENTICATION_TYPES))
                .verificationMethod(keyId)
                .build();
        authentications = Collections.singletonList(authentication);

        // DID DOCUMENT services
        JsonObject jsonGetAttrEndpoint = jsonGetAttrDataContent == null ? null : jsonGetAttrDataContent.getAsJsonObject("endpoint");

        List<Service> services = new ArrayList<>();

        if (jsonGetAttrEndpoint != null) {
            for (Map.Entry<String, JsonElement> jsonService : jsonGetAttrEndpoint.entrySet()) {

                JsonPrimitive jsonGetAttrEndpointValue = jsonGetAttrEndpoint.getAsJsonPrimitive(jsonService.getKey());
                String value = jsonGetAttrEndpointValue == null ? null : jsonGetAttrEndpointValue.getAsString();

                Service service = Service.builder()
                        .type(jsonService.getKey())
                        .serviceEndpoint(value)
                        .build();

                services.add(service);
            }
        }
        // create DID DOCUMENT
        DIDDocument didDocument = DIDDocument.builder()
                .id(URI.create(did))
                .verificationMethods(verificationMethods)
                .authentications(authentications)
                .services(services)
                .build();

        // create DRIVER METADATA
        Map<String, Object> methodMetadata = new LinkedHashMap<>();
        methodMetadata.put("network", network);
        methodMetadata.put("poolVersion", poolVersion);
        methodMetadata.put("nymResponse", gson.fromJson(jsonGetNymResponse, Map.class));
        methodMetadata.put("attrResponse", gson.fromJson(jsonGetAttrResponse, Map.class));

        // create RESOLVE RESULT
        ResolveResult resolveResult = ResolveResult.build(didDocument, null, DIDDocument.MIME_TYPE_JSON_LD, null, methodMetadata);

        // done
        return resolveResult;
    }

    @Override
    public Map<String, Object> properties() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /*
     * Helper methods
     */
    private static String expandVerkey(String did, String verkey) {

        if (verkey == null || !did.startsWith("did:sov:") || !verkey.startsWith("~")) {
            return verkey;
        }

        byte[] didBytes = Base58.decode(did.substring(did.lastIndexOf(":") + 1));
        byte[] verkeyBytes = Base58.decode(verkey.substring(1));

        byte[] didVerkeyBytes = new byte[didBytes.length + verkeyBytes.length];
        System.arraycopy(didBytes, 0, didVerkeyBytes, 0, 16);
        System.arraycopy(verkeyBytes, 0, didVerkeyBytes, 16, 16);

        String didVerkey = Base58.encode(didVerkeyBytes);
        if (LOG.isInfoEnabled()) {
            LOG.info("Expanded " + did + " and " + verkey + " to " + didVerkey);
        }

        return didVerkey;
    }
}
