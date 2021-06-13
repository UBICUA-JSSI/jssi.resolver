# JSSI Resolution Server
Java web application forked from [DIF Universal Resolver](https://github.com/decentralized-identity/universal-resolver)

## Description
JSSI Resolution Server is aimed at resolving decentralized identifiers. The driver-based architecure is used to support different storage systems. The default driver.sov functionality is related to the [DID resolution](https://w3c-ccg.github.io/did-resolution/) using [Hyperledger Indy DLT](https://github.com/hyperledger/indy-sdk).

## Quick start
### Prerequisites
- [Open JDK 15+](https://openjdk.java.net/)
- [Apache Netbeans 12+](https://netbeans.apache.org/)
- [Wildfly 21+](https://www.wildfly.org/)

### Dependencies
- [Hyperledger.lib](https://github.com/hyperledger/indy-sdk)

### Configuration
The application directory must be configured in the resolver.web file that can be found in: 
<install_dir>/resolver/resolver.web/src/main/webapp/WEB-INF/web.xml. Modify the context-param property to adjust it to your install_dir:

```
<context-param>
     <param-name>jssi.driver.config</param-name>
     <param-value><install_dir>/resolver/resolver.assets/config.json</param-value>
</context-param>
```

The config.json file contains the driver configurations that will be included in the deployable resolver.ear archive. The driver.sov configuration file is specified in the web.xml file:

 ```
 <context-param>
      <param-name>jssi.driver.config</param-name>
      <param-value><install_dir>/resolver/resolver.assets/driver.properties</param-value>
 </context-param>
```
 
As an example, the driver.properties fle contains the Ubicua test parameters:
 ```
// Array of genesis
resolver.config=ubicua,2,<install_dir>/resolver/resolver.assets/ubicua.genesis

// Libindy path
resolver.native=<install_dir>/hyperledger.native

// Resolver wallet
wallet.resolver.id=resolver_wallet
wallet.resolver.key=resolver_wallet_key

// Resolver did
resolver.did=V4SGRU86Z58d6TV7PBUe6f
 ```
JSSI Resolution Server must possess a DID and a wallet with necessary cryptographic material in order to sign requests to the [Hyperledger Indy DLT](https://github.com/hyperledger/indy-sdk). Before testing, check if the Resolver wallet has been created and the Resolver DID has been duly registered.

### Logging

To enable the logging service, open the Widfly configuration file, i.e. <wildfly_install_dir>/standalone/configuration/standalone-full.xml, and modify the profile/subsystem xmlns="urn:jboss:domain:logging:8.0" property as follows:
 
 ```
<logger category="jssi">
     <level name="DEBUG"/>
 </logger>
 ```
 
### Testing
 
It is required to compile, package and deploy the resolver.ear application on the Wildfly server. To test the application, open your browser in:
http://localhost:8080/resolver/1.0/identifiers/did:sov:ubicua:V4SGRU86Z58d6TV7PBUe6f
