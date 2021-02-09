/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jssi.resolver.driver.btcr;

import info.weboftrust.btctxlookup.Chain;
import info.weboftrust.btctxlookup.bitcoinconnection.BTCDRPCBitcoinConnection;
import info.weboftrust.btctxlookup.bitcoinconnection.BitcoinConnection;
import info.weboftrust.btctxlookup.bitcoinconnection.BitcoindRPCBitcoinConnection;
import info.weboftrust.btctxlookup.bitcoinconnection.BlockcypherAPIBitcoinConnection;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import javax.enterprise.context.ApplicationScoped;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author UBICUA
 */
@ApplicationScoped
public class BtcrConfig implements Serializable{
    
    private static final Logger LOG = LoggerFactory.getLogger(BtcrConfig.class);
    
    private String connection = "blockcypherapi";
    private String rpcUrlMainnet = "";
    private String rpcUrlTestnet = "";
    private String rpcUrlCertMainnet = "";
    private String rpcUrlCertTestnet = "";
    
    private BitcoinConnection bitcoinConnectionMainnet;
    private BitcoinConnection bitcoinConnectionTestnet;
    
    public void configure() {

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Configuring from properties: \n Connection: %s\n Mainnet: %s\n Testnet: %s\n CertMainnet:%s\n CertTestnet %s\n",
                    connection,
                    rpcUrlMainnet,
                    rpcUrlTestnet,
                    rpcUrlCertMainnet,
                    rpcUrlCertTestnet));
        }

        try {
            if ("bitcoind".equalsIgnoreCase(connection)) {
                if (rpcUrlMainnet != null && !rpcUrlMainnet.isBlank()) {
                    bitcoinConnectionMainnet = new BitcoindRPCBitcoinConnection(rpcUrlMainnet, Chain.MAINNET);
                }
                if (rpcUrlTestnet != null && !rpcUrlTestnet.isBlank()) {
                    bitcoinConnectionTestnet = new BitcoindRPCBitcoinConnection(rpcUrlTestnet, Chain.TESTNET);
                }
            } else if ("btcd".equalsIgnoreCase(connection)) {
                if (rpcUrlMainnet != null && !rpcUrlMainnet.isBlank()) {
                    BTCDRPCBitcoinConnection btcdrpcBitcoinConnection = new BTCDRPCBitcoinConnection(rpcUrlMainnet,
                            Chain.MAINNET);
                    if (rpcUrlCertMainnet != null && !rpcUrlCertMainnet.isBlank()) {
                        btcdrpcBitcoinConnection.getBitcoindRpcClient()
                                .setSslSocketFactory(getSslSocketFactory(rpcUrlCertMainnet));
                    }
                    bitcoinConnectionMainnet = btcdrpcBitcoinConnection;
                }
                if (rpcUrlTestnet != null && !rpcUrlTestnet.isBlank()) {
                    BTCDRPCBitcoinConnection btcdrpcBitcoinConnection = new BTCDRPCBitcoinConnection(rpcUrlTestnet, Chain.TESTNET);
                    if (rpcUrlCertTestnet != null && !rpcUrlCertTestnet.isBlank()) {
                        btcdrpcBitcoinConnection.getBitcoindRpcClient()
                                .setSslSocketFactory(getSslSocketFactory(rpcUrlCertTestnet));
                    }
                    bitcoinConnectionTestnet = btcdrpcBitcoinConnection;
                }
            } else if ("bitcoinj".equalsIgnoreCase(connection)) {
                throw new RuntimeException("bitcoinj is not implemented yet");
            } else if ("blockcypherapi".equalsIgnoreCase(connection)) {
                bitcoinConnectionMainnet = new BlockcypherAPIBitcoinConnection();
                bitcoinConnectionTestnet = new BlockcypherAPIBitcoinConnection();
            } else {
                throw new IllegalArgumentException("Invalid bitcoinConnection: " + connection);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException | MalformedURLException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }
    
        private static SSLSocketFactory getSslSocketFactory(String certString) {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            byte[] decoded = Base64.getDecoder().decode(certString.trim().getBytes(StandardCharsets.UTF_8));
            Certificate certificate = certificateFactory.generateCertificate(new ByteArrayInputStream(decoded));
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca-cert", certificate);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            SSLContext context = SSLContext.getInstance("SSL");
            context.init(null, trustManagerFactory.getTrustManagers(), null);
            return context.getSocketFactory();

        } catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | KeyManagementException  | IOException e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    public void setConnection(String connection) {
        this.connection = connection;
    }

    public void setRpcUrlMainnet(String rpcUrlMainnet) {
        this.rpcUrlMainnet = rpcUrlMainnet;
    }

    public void setRpcUrlTestnet(String rpcUrlTestnet) {
        this.rpcUrlTestnet = rpcUrlTestnet;
    }

    public void setRpcUrlCertMainnet(String rpcUrlCertMainnet) {
        this.rpcUrlCertMainnet = rpcUrlCertMainnet;
    }

    public void setRpcUrlCertTestnet(String rpcUrlCertTestnet) {
        this.rpcUrlCertTestnet = rpcUrlCertTestnet;
    }

    public BitcoinConnection getBitcoinConnectionMainnet() {
        return bitcoinConnectionMainnet;
    }

    public BitcoinConnection getBitcoinConnectionTestnet() {
        return bitcoinConnectionTestnet;
    }
}
