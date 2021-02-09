package jssi.resolver.driver.btcr;

import com.apicatalog.jsonld.lang.Keywords;
import foundation.identity.did.Authentication;
import foundation.identity.did.DIDDocument;
import foundation.identity.did.Service;
import foundation.identity.did.VerificationMethod;
import foundation.identity.jsonld.JsonLDObject;
import info.weboftrust.btctxlookup.Chain;
import info.weboftrust.btctxlookup.ChainAndLocationData;
import info.weboftrust.btctxlookup.ChainAndTxid;
import info.weboftrust.btctxlookup.DidBtcrData;
import info.weboftrust.btctxlookup.bitcoinconnection.BitcoinConnection;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uniresolver.ResolutionException;
import uniresolver.driver.Driver;
import uniresolver.result.ResolveResult;

public class BtcrDriver implements Driver {

    private static final Logger LOG = LoggerFactory.getLogger(BtcrDriver.class);

    public static final Pattern DID_BTCR_PATTERN_METHOD = Pattern.compile("^did:btcr:(.*)$");
    public static final Pattern DID_BTCR_PATTERN_METHOD_SPECIFIC = Pattern.compile("^[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-(?:[a-z0-9]{3}|[a-z0-9]{4}-[a-z0-9]{2})$");
    public static final String[] DIDDOCUMENT_VERIFICATIONMETHOD_TYPES = new String[]{"EcdsaSecp256k1VerificationKey2019"};
    public static final String[] DIDDOCUMENT_AUTHENTICATION_TYPES = new String[]{"EcdsaSecp256k1SignatureAuthentication2019"};

    private HttpClient httpClient = HttpClients.createDefault();
    private final BtcrConfig config;
    

    public BtcrDriver(BtcrConfig config) {
        this.config = config;
    }

    @Override
    public ResolveResult resolve(String identifier) throws ResolutionException {

        // parse identifier
        Matcher matcher = DID_BTCR_PATTERN_METHOD.matcher(identifier);
        if (!matcher.matches()) {
            return null;
        }

        String methodSpecificIdentifier = matcher.group(1);
        matcher = DID_BTCR_PATTERN_METHOD_SPECIFIC.matcher(methodSpecificIdentifier);
        if (!matcher.matches()) {
            throw new ResolutionException("DID does not match 4-4-4-3 or 4-4-4-4-2 pattern.");
        }

        LOG.debug("Method specific identifier: " + methodSpecificIdentifier);

        // retrieve BTCR data
        ChainAndLocationData initialChainAndLocationData;
        ChainAndLocationData chainAndLocationData;
        ChainAndTxid initialChainAndTxid;
        ChainAndTxid chainAndTxid;
        DidBtcrData btcrData;
        List<DidBtcrData> spentInChainAndTxids = new ArrayList<>();

        try {
            // decode txref
            chainAndLocationData = ChainAndLocationData.txrefDecode(methodSpecificIdentifier);

            if (chainAndLocationData.getLocationData().getTxoIndex() == 0 && chainAndLocationData.isExtended()) {

                String correctTxref = ChainAndLocationData.txrefEncode(chainAndLocationData);
                String correctDid = "did:btcr:" + correctTxref.substring(correctTxref.indexOf(":") + 1);
                throw new ResolutionException(
                        "Extended txref form not allowed if txoIndex == 0. You probably want to use " + correctDid
                        + " instead.");
            }

            // lookup txid
            BitcoinConnection connection = chainAndLocationData.getChain() == Chain.MAINNET
                    ? config.getBitcoinConnectionMainnet()
                    : config.getBitcoinConnectionTestnet();

            if (connection == null) {
                throw new ResolutionException("No connection is available for the chain " + chainAndLocationData.getChain().toString());
            }

            chainAndTxid = connection.lookupChainAndTxid(chainAndLocationData);

            // loop
            initialChainAndTxid = chainAndTxid;
            initialChainAndLocationData = chainAndLocationData;

            while (true) {

                btcrData = connection.getDidBtcrData(chainAndTxid);
                if (btcrData == null) {
                    throw new ResolutionException("No BTCR data found in transaction: " + chainAndTxid);
                }

                // check if we need to follow the tip
                if (btcrData.getSpentInChainAndTxid() == null) {

                    break;
                } else {

                    spentInChainAndTxids.add(btcrData);
                    chainAndTxid = btcrData.getSpentInChainAndTxid();
                    chainAndLocationData = connection.lookupChainAndLocationData(chainAndTxid);

                    // deactivated?
                    if (btcrData.isDeactivated()) {
                        LOG.debug("DID Document is deactivated with TX: " + chainAndTxid.getTxid());
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            throw new ResolutionException(
                    "Cannot retrieve BTCR data for " + methodSpecificIdentifier + ": " + ex.getMessage(), ex);
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Retrieved BTCR data for " + methodSpecificIdentifier + " (" + chainAndTxid + " on chain "
                    + chainAndLocationData.getChain() + "): " + btcrData);
        }

        // retrieve DID DOCUMENT CONTINUATION
        DIDDocument didDocumentContinuation = null;

        if (btcrData.getContinuationUri() != null) {

            HttpGet httpGet = new HttpGet(btcrData.getContinuationUri());

            try ( CloseableHttpResponse httpResponse = (CloseableHttpResponse) this.getHttpClient().execute(httpGet)) {

                if (httpResponse.getStatusLine().getStatusCode() > 200) {
                    throw new ResolutionException(
                            "Cannot retrieve DID DOCUMENT CONTINUATION for " + methodSpecificIdentifier + " from "
                            + btcrData.getContinuationUri() + ": " + httpResponse.getStatusLine());
                }

                HttpEntity httpEntity = httpResponse.getEntity();

                Map<String, Object> jsonLdObject = (Map<String, Object>) JsonLDObject.fromJson(EntityUtils.toString(httpEntity)).getJsonObject();

                final boolean emptyOrNull = !jsonLdObject.containsKey("didDocument")
                        || jsonLdObject.get("didDocument") == null
                        || ((Map) (jsonLdObject.get("didDocument"))).isEmpty();
                if (!emptyOrNull) {

                    Map<String, Object> outerJsonLdObject = jsonLdObject;
                    jsonLdObject = (Map<String, Object>) outerJsonLdObject.get("didDocument");

                    if ((!jsonLdObject.containsKey(Keywords.CONTEXT))
                            && outerJsonLdObject.containsKey(Keywords.CONTEXT)) {

                        jsonLdObject.put(Keywords.CONTEXT, outerJsonLdObject.get(Keywords.CONTEXT));
                    }
                }

                if (!emptyOrNull) {
                    didDocumentContinuation = DIDDocument.fromJsonObject(jsonLdObject);
                } else {
                    didDocumentContinuation = DIDDocument.builder().id(URI.create(identifier)).build();
                }
                EntityUtils.consume(httpEntity);
            } catch (IOException ex) {

                throw new ResolutionException("Cannot retrieve DID DOCUMENT CONTINUATION for "
                        + methodSpecificIdentifier + " from " + btcrData.getContinuationUri() + ": " + ex.getMessage(),
                        ex);
            }

            if (LOG.isInfoEnabled()) {
                LOG.info("Retrieved DID DOCUMENT CONTINUATION for " + methodSpecificIdentifier + " ("
                        + btcrData.getContinuationUri() + "): " + didDocumentContinuation.toString());
            }
        }

        // DID DOCUMENT contexts
        List<URI> contexts = null;

        if (didDocumentContinuation != null) {
            contexts = didDocumentContinuation.getContexts();
        }

        // DID DOCUMENT verificationMethods
        List<VerificationMethod> verificationMethods = new ArrayList<>();
        List<Authentication> authentications = new ArrayList<>();

        List<String> inputScriptPubKeys = new ArrayList<>();

        for (DidBtcrData spentInChainAndTxid : spentInChainAndTxids) {
            inputScriptPubKeys.add(spentInChainAndTxid.getInputScriptPubKey());
        }
        inputScriptPubKeys.add(btcrData.getInputScriptPubKey());

        int keyNum = 0;

        for (String inputScriptPubKey : inputScriptPubKeys) {
            String keyId = identifier + "#key-" + (keyNum++);

            VerificationMethod verificationMethod = VerificationMethod.builder()
                    .id(URI.create(keyId))
                    .types(Arrays.asList(DIDDOCUMENT_VERIFICATIONMETHOD_TYPES))
                    .publicKeyBase58(inputScriptPubKey)
                    .build();
            verificationMethods.add(verificationMethod);
        }

        VerificationMethod verificationMethod = VerificationMethod
                .builder()
                .id(URI.create(identifier + "#satoshi"))
                .types(Arrays.asList(DIDDOCUMENT_VERIFICATIONMETHOD_TYPES))
                .publicKeyBase58(inputScriptPubKeys.get(inputScriptPubKeys.size() - 1))
                .build();
        verificationMethods.add(verificationMethod);

        Authentication authentication = Authentication.builder()
                .types(Arrays.asList(DIDDOCUMENT_AUTHENTICATION_TYPES))
                .verificationMethod(URI.create("#satoshi"))
                .build();
        authentications.add(authentication);

        if (didDocumentContinuation != null) {
            if (didDocumentContinuation.getVerificationMethods() != null) {
                for (VerificationMethod didDocumentContinuationVerificationMethod : didDocumentContinuation.getVerificationMethods()) {

                    if (containsById(verificationMethods, didDocumentContinuationVerificationMethod)) {
                        continue;
                    }
                    verificationMethods.add(didDocumentContinuationVerificationMethod);
                }
            }

            if (didDocumentContinuation.getAuthentications() != null) {
                for (Authentication didDocumentContinuationAuthentication : didDocumentContinuation
                        .getAuthentications()) {

                    if (containsById(verificationMethods, didDocumentContinuationAuthentication)) {
                        continue;
                    }
                    authentications.add(didDocumentContinuationAuthentication);
                }
            }
        }

        // DID DOCUMENT services
        List<Service> services;

        if (didDocumentContinuation != null) {
            services = didDocumentContinuation.getServices();
        } else {
            services = Collections.emptyList();
        }

        // create DID DOCUMENT
        DIDDocument didDocument = DIDDocument.builder()
                .contexts(contexts)
                .id(URI.create(identifier))
                .verificationMethods(verificationMethods)
                .authentications(authentications)
                .services(services)
                .build();

        // create METHOD METADATA
        Map<String, Object> methodMetadata = new LinkedHashMap<>();
        methodMetadata.put("inputScriptPubKey", btcrData.getInputScriptPubKey());
        methodMetadata.put("continuationUri", btcrData.getContinuationUri());
        if (didDocumentContinuation != null) {
            methodMetadata.put("continuation", didDocumentContinuation);
        }
        if (chainAndLocationData != null) {
            methodMetadata.put("chain", chainAndLocationData.getChain());
        }
        methodMetadata.put("initialBlockHeight", initialChainAndLocationData.getLocationData().getBlockHeight());
        methodMetadata.put("initialTransactionPosition",
                initialChainAndLocationData.getLocationData().getTransactionPosition());
        methodMetadata.put("initialTxoIndex", initialChainAndLocationData.getLocationData().getTxoIndex());
        if (initialChainAndTxid != null) {
            methodMetadata.put("initialTxid", initialChainAndTxid);
        }
        if (chainAndLocationData != null) {
            methodMetadata.put("blockHeight", chainAndLocationData.getLocationData().getBlockHeight());
        }
        if (chainAndLocationData != null) {
            methodMetadata.put("transactionPosition", chainAndLocationData.getLocationData().getTransactionPosition());
        }
        if (chainAndLocationData != null) {
            methodMetadata.put("txoIndex", chainAndLocationData.getLocationData().getTxoIndex());
        }
        if (chainAndTxid != null) {
            methodMetadata.put("txid", chainAndTxid);
        }
        methodMetadata.put("spentInChainAndTxids", spentInChainAndTxids);
        methodMetadata.put("deactivated", btcrData.isDeactivated());

        // create RESOLVE RESULT
        // done
        return ResolveResult.build(didDocument, null, DIDDocument.MIME_TYPE_JSON_LD, null, methodMetadata);
    }

    @Override
    public Map<String, Object> properties() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public HttpClient getHttpClient() {
        return this.httpClient;
    }

    public boolean containsById(List<? extends JsonLDObject> jsonLdObjectList, JsonLDObject containsJsonLdObject) {
        for (JsonLDObject jsonLdObject : jsonLdObjectList) {
            if (jsonLdObject.getId() != null && jsonLdObject.getId().equals(containsJsonLdObject.getId())) {
                return true;
            }
        }
        return false;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
 }