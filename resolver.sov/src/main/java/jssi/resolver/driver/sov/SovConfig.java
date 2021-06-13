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

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import javax.enterprise.context.ApplicationScoped;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.builder.fluent.PropertiesBuilderParameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.LibIndy;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.pool.PoolJSONParameters;
import org.hyperledger.indy.sdk.pool.PoolLedgerConfigExistsException;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.hyperledger.indy.sdk.wallet.WalletExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uniresolver.ResolutionException;

/**
 *
 * @author UBICUA
 */
@ApplicationScoped
public class SovConfig implements Serializable{
    
    private static final Logger LOG = LoggerFactory.getLogger(SovConfig.class);
    
    private final FileBasedConfigurationBuilder<FileBasedConfiguration> builder
            = new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class);
    
    private final Map<String, Pool> poolMap = new HashMap<>();
    private final Map<String, Integer> poolVersionMap = new HashMap<>();
    private Wallet wallet;
    private Configuration config = null;
    
    public void init(String path){

        LOG.debug(String.format("Configure from properties: %s", path));
        
        PropertiesBuilderParameters properties = new Parameters().properties();
        properties.setPath(path);
        builder.configure(properties);
        
        try {
            config = builder.getConfiguration();
        } catch (ConfigurationException ex) {
            LOG.error(String.format("Configuration error: %s", ex.getMessage()));
        }
        
        String indy = config.getString("resolver.native");
        LOG.info(String.format("Initializing libindy: %s", indy));
        LibIndy.init(indy);
        
        String[] configs = config.getStringArray("resolver.config");
        
        for(String item : configs){
            String[] items = item.split(",");
            String name = items[0].strip();
            int version = Integer.parseInt(items[1].strip());
            String genesis = items[2].strip();
            Pool pool = openPool(version, name, genesis);
            poolMap.put(name, pool);
            poolVersionMap.put(name, version);
        }
        
        LOG.info(String.format("Opened %d pools: %s", poolMap.size(), poolMap.keySet()));
        
        String walletId = config.getString("wallet.resolver.id");
        String walletKey = config.getString("wallet.resolver.key");
        
        try {
            configWallet(walletId, walletKey);
        } catch(ResolutionException ex){
            LOG.error(String.format("Configuration wallet error: %s", ex.getMessage()));
        }
    }
    
    private Pool openPool(int version, String name, String genesis){
        LOG.debug(String.format("Pool name: %s", name));

        Pool pool = null;
        File file = new File(genesis);
        StringBuilder builder = new StringBuilder();
        try {
            Stream<String> stream = Files.lines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8);
            stream.forEach(s -> builder.append(s).append("\n"));
            LOG.debug(String.format("Genesis pool definition:\n%s", builder.toString()));
            configPool(version, name, file);
            pool = Pool.openPoolLedger(name, null).get();
            LOG.debug(String.format("Open pool: %s", name));
        } catch (IOException | IndyException | InterruptedException | ExecutionException e) {
            LOG.debug("Open pool exception", e);
        }
        return pool;
    }
    
    public void closePools() {
        try {
            for (Pool pool : poolMap.values()) {
                if(pool != null){
                    pool.close();
                }
            }
        } catch (ExecutionException | IndyException | InterruptedException e) {
            LOG.debug("Pool close exception", e);
        }
    }
    
    public void closeWallet(){
        if(wallet != null) {
            try {
                wallet.close();
            } catch(InterruptedException | ExecutionException | IndyException ex){}
        }
    }
    
    private void configPool(int version, String name, File genesis) {

        try {
            Pool.setProtocolVersion(version).get();
            PoolJSONParameters.CreatePoolLedgerConfigJSONParameter params
                    = new PoolJSONParameters.CreatePoolLedgerConfigJSONParameter(genesis.getAbsolutePath());
    
            Pool.createPoolLedgerConfig(name, params.toJson()).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PoolLedgerConfigExistsException) {
                LOG.debug(String.format("Pool config already exist: (%s) %s", name, e.getCause().getMessage()));
            }
        } catch (IndyException | InterruptedException e) {
            LOG.debug("Pool config exception", e);
        }
    }

    private void configWallet(String walletId, String walletKey) throws ResolutionException{
        
        JsonObject walletConfig = new JsonObject();
        JsonObject walletCredentials = new JsonObject();
        
        
        try {
            walletConfig.addProperty("id", walletId);
            walletCredentials.addProperty("key", walletKey);

            Wallet.createWallet(walletConfig.toString(), walletCredentials.toString()).get();
            if (LOG.isInfoEnabled()) {
                LOG.info(String.format("Wallet '%s' successfully created.", walletId));
            }
        } catch (IndyException | InterruptedException | ExecutionException ex) {

            IndyException iex = null;
            if (ex instanceof IndyException) {
                iex = (IndyException) ex;
            }
            if (ex instanceof ExecutionException && ex.getCause() instanceof IndyException) {
                iex = (IndyException) ex.getCause();
            }
            if (iex instanceof WalletExistsException) {
                if (LOG.isInfoEnabled()){
                    LOG.info(String.format("Wallet '%s' has already been create.", walletId));
                }
            } else {
                throw new ResolutionException(String.format("Cannot create wallet '%s:' %s", walletId,  ex.getMessage(), ex));
            }
        }
        
        openWallet(walletConfig, walletCredentials, walletId);
    }
    
    private void openWallet(JsonObject walletConfig, JsonObject walletCredentials, String walletId) throws ResolutionException{
        
        try {
            wallet = Wallet.openWallet(walletConfig.toString(), walletCredentials.toString()).get();
            LOG.info(String.format("Wallet '%s' has been open.", walletId));
        } catch (IndyException | InterruptedException | ExecutionException ex) {
            throw new ResolutionException(String.format("Cannot open wallet '%s:' %s", walletId,  ex.getMessage(), ex));
        }
    }
    
    public Map<String, Pool> getPoolMap() {
        return poolMap;
    }

    public Map<String, Integer> getPoolVersionMap() {
        return poolVersionMap;
    }

    public Wallet getWallet() {
        return wallet;
    }
    
    public String getResolverDid(){
        return config.getString("resolver.did");
    }
}
