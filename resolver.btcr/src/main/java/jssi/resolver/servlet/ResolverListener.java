/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jssi.resolver.servlet;

import javax.inject.Inject;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jssi.resolver.driver.btcr.BtcrConfig;

/**
 * Web application lifecycle listener.
 *
 * @author UBICUA
 */
public class ResolverListener implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(ResolverListener.class);
    
    @Inject
    BtcrConfig config;
    
    @Override
    public void contextInitialized(ServletContextEvent sce) {
       String connection = sce.getServletContext().getInitParameter("connection");
       config.setConnection(connection);
       config.configure();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        
    }
}
