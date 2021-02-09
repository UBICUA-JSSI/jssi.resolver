/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jssi.resolver.driver.dns;

import java.io.Serializable;
import java.util.Arrays;
import javax.enterprise.context.ApplicationScoped;

/**
 *
 * @author UBICUA
 */
@ApplicationScoped
public class DnsConfig implements Serializable{
    
    private String[] dnsServers;
    
    public void init(String dnsServers){
        if(dnsServers != null){
            this.dnsServers = Arrays.stream(dnsServers.split(";")).map(String::trim).toArray(String[]::new);
        } else {}
            this.dnsServers = new String[0];
        
            
           
    }

    public String[] getDnsServers() {
        return dnsServers;
    }
    
}
