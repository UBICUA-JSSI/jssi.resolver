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
package jssi.resolver.servlet;

import java.io.IOException;
import javax.inject.Inject;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jssi.resolver.local.Drivers;

/**
 * Web application lifecycle listener.
 *
 * @author UBICUA
 */
public class ResolverListener implements ServletContextListener {
    
    private static final Logger LOG = LoggerFactory.getLogger(ResolverListener.class);
    
    @Inject
    private Drivers drivers;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        String path = sce.getServletContext().getInitParameter("jssi.resolver.config");
        try {
            drivers.init(path);
        } catch (IOException ex) {
            LOG.error(String.format("Initialization exception: %s", ex.getMessage()));
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}

