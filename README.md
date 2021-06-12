# JSSI Resolver Server
Java web application forked from [DIF Universal Resolver](https://github.com/decentralized-identity/universal-resolver)

## Description
JSSI Resolver is aimed at resoving decentralized identifiers. To cover different storage systems, the driver-based architecure is used. The default functionality is the [DID to DID document resolution](https://w3c-ccg.github.io/did-resolution/) provided by [Hyperledger Indy](https://github.com/hyperledger/indy-sdk).

## Prerequisites
- [Open JDK 15+](https://openjdk.java.net/)
- [Apache Netbeans 12+](https://netbeans.apache.org/)
- [Wildfly 21+](https://www.wildfly.org/)

## Dependencies
- [Hyperledger.lib](https://github.com/hyperledger/indy-sdk)

## Configuration
The application directory must be configured in the resolver.web file that can be found in: 
<install_dir>/resolver/resolver.web/src/main/webapp/WEB-INF/web.xml. Modify the context-param property to adjust it to your install_dir:

```
<context-param>
     <param-name>jssi.driver.config</param-name>
     <param-value><install_dir>/resolver/resolver.assets/config.json</param-value>
</context-param>
```

The config.json file contains the driver configurations that will be included in the deployable resolver.ear archive. The driver configuration file is specified in the web.xml file:

 ```
 <context-param>
      <param-name>jssi.driver.config</param-name>
      <param-value><install_dir>/resolver/resolver.assets/driver.properties</param-value>
 </context-param>
```
 
As an example, the driver.properties fle contains the Ubicua test parameters:
 ```
// Array of genesis
resolver.config=<install_dir>/resolver/resolver.assets/ubicua.genesis

// Libindy path
resolver.native=<install_dir>/hyperledger.native

// Resolver wallet
wallet.resolver.id=resolver_wallet
wallet.resolver.key=resolver_wallet_key
 ```
For testing purposes, the Resolver DID has the assigned value of V4SGRU86Z58d6TV7PBUe6f. It means that the Resolver wallet contains the necessary cryptographic material to sign their requests to DLT. Before testing, it is necessary to check if the wallet has been created and if the Resolver DID has been registered.

## Logging

To enable the logging service, open the Widfly configuration file, i.e. <wildfly_install_dir>/standalone/configuration/standalone-full.xml, and modify the profile/subsystem xmlns="urn:jboss:domain:logging:8.0" property as follows:
 
 ```
<logger category="jssi">
     <level name="DEBUG"/>
 </logger>
 ```
 
## Execution
 
It is required to compile, package and deploy the resolver.ear application on the Wildfly server. To test the application, open your browser in:
http://localhost:8080/resolver/1.0/identifiers/did:sov:ubicua:V4SGRU86Z58d6TV7PBUe6f
