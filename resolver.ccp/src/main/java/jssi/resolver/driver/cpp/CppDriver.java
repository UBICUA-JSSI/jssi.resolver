package jssi.resolver.driver.cpp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import foundation.identity.did.Authentication;
import foundation.identity.did.DIDDocument;
import foundation.identity.did.PublicKey;
import foundation.identity.did.Service;
import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uniresolver.ResolutionException;
import uniresolver.driver.Driver;
import uniresolver.result.ResolveResult;

public class CppDriver implements Driver {

    private static final Logger LOG = LoggerFactory.getLogger(CppDriver.class);

    private static final Pattern DID_CCP_PATTERN = Pattern.compile("^did:ccp:([123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{25,34})$");
    private static final String[] DIDDOCUMENT_PUBLICKEY_TYPES = new String[]{"Secp256k1"};
    private static final String[] DIDDOCUMENT_AUTHENTICATION_TYPES = new String[]{"Secp256k1"};
    private static final String DEFAULT_CCP_URL = "https://did.baidu.com";

    private static final Gson gson = new Gson();
    private static final HttpClient DEFAULT_HTTP_CLIENT = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()).build();
    private final String ccpUrl = DEFAULT_CCP_URL;
    private final HttpClient httpClient = DEFAULT_HTTP_CLIENT;

    public CppDriver() {
    }

    @Override
    public ResolveResult resolve(String identifier) throws ResolutionException {
        // match
        Matcher matcher = DID_CCP_PATTERN.matcher(identifier);
        if (!matcher.matches()) {
            return null;
        }

        // fetch data from CCP
        String resolveUrl = ccpUrl + "/v1/did/resolve/" + identifier;
        HttpGet httpGet = new HttpGet(resolveUrl);

        // find the DDO
        JsonObject didDocumentDO;
        try {
            CloseableHttpResponse httpResponse = (CloseableHttpResponse) httpClient.execute(httpGet);
            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                throw new ResolutionException("Cannot retrieve DDO for `" + identifier + "` from `" + ccpUrl + ": " + httpResponse.getStatusLine());
            }

            // extract payload
            HttpEntity httpEntity = httpResponse.getEntity();
            String entityString = EntityUtils.toString(httpEntity);
            EntityUtils.consume(httpEntity);

            // get DDO
            JsonObject jo = gson.fromJson(entityString, JsonObject.class);
            didDocumentDO = jo.getAsJsonObject("content").getAsJsonObject("didDocument");
        } catch (IOException ex) {
            throw new ResolutionException("Cannot retrieve DDO info for `" + identifier + "` from `" + ccpUrl + "`: " + ex.getMessage(), ex);
        } 

        // DDO id
        String id = identifier;

        // DDO publicKeys
        List<PublicKey> publicKeys = new ArrayList<>();
        // index 0 is auth key
        JsonElement firstKeyJO = didDocumentDO.getAsJsonArray("publicKey").get(0);
        
        publicKeys.add(PublicKey.builder()
                .id(URI.create(firstKeyJO.getAsJsonObject().get("id").getAsString()))
                .type(firstKeyJO.getAsJsonObject().get("id").getAsString())
                .publicKeyHex(firstKeyJO.getAsJsonObject().get("publicKeyHex").getAsString())
                .build());
                
        // index 1 is recovery key
        JsonElement secondKeyJO = didDocumentDO.getAsJsonArray("publicKey").get(1);
        publicKeys.add(PublicKey.builder()
                .id(URI.create(secondKeyJO.getAsJsonObject().get("id").getAsString()))
                .types(Arrays.asList(DIDDOCUMENT_PUBLICKEY_TYPES))
                .publicKeyHex(secondKeyJO.getAsJsonObject().get("publicKeyHex").getAsString())
                .build());

        // DDO Authentications
        List<Authentication> authentications = new ArrayList<>();
        JsonElement authentication = didDocumentDO.getAsJsonArray("authentication").get(0);
        authentications.add(Authentication.builder()
                .id(URI.create(authentication.getAsString()))
                .types(Arrays.asList(DIDDOCUMENT_AUTHENTICATION_TYPES))
                .build());

        
        // DDO services
        List<Service> services = new ArrayList<>();
        JsonArray serviceJA = didDocumentDO.getAsJsonArray("service");
        for (int i = 0; i < serviceJA.size(); i++) {
            JsonElement service = serviceJA.get(i);
            services.add(Service.builder()
                    .type(service.getAsJsonObject().get("type").getAsString())
                    .serviceEndpoint(service.getAsJsonObject().get("serviceEndpoint").getAsString())
                    .build());
        }

        // create DDO
        DIDDocument didDocument = DIDDocument.builder()
                .id(URI.create(id))
                .publicKeys(publicKeys)
                .authentications(authentications)
                .services(services)
                .build();

        // create Method METADATA
        Map<String, Object> methodMetadata = new LinkedHashMap<>();
        methodMetadata.put("version", didDocumentDO.getAsJsonPrimitive("version").getAsInt());
        methodMetadata.put("proof", gson.fromJson(didDocumentDO.getAsJsonObject("proof"), Map.class));
        methodMetadata.put("created", didDocumentDO.getAsJsonPrimitive("created").getAsString());
        methodMetadata.put("updated", didDocumentDO.getAsJsonPrimitive("updated").getAsString());

        // create RESOLVE RESULT
        ResolveResult resolveResult = ResolveResult.build(didDocument, null, DIDDocument.MIME_TYPE_JSON_LD, null, methodMetadata);
        // done
        return resolveResult;
    }

    @Override
    public Map<String, Object> properties() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
