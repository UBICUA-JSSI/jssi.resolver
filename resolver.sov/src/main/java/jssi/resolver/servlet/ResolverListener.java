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
import jssi.resolver.driver.sov.SovConfig;

/**
 * Web application lifecycle listener.
 *
 * @author UBICUA
 */
public class ResolverListener implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(ResolverListener.class);
    
    @Inject 
    SovConfig config;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        String path = sce.getServletContext().getInitParameter("jssi.driver.config");
        config.init(path);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        config.closePools();
        config.closeWallet();
    }
}
