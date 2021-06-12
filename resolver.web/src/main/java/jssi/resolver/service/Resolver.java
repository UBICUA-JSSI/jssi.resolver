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
package jssi.resolver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jssi.resolver.local.Drivers;
import jssi.resolver.local.LocalUniResolver;
import uniresolver.ResolutionException;
import uniresolver.UniResolver;
import uniresolver.result.ResolveResult;

/**
 * REST Web Service
 *
 * @author UBICUA
 */

@RequestScoped
@Path("/")
public class Resolver implements UniResolver{
    
    private static final Logger LOG = LoggerFactory.getLogger(Resolver.class);

    @Context
    private UriInfo context;
    
    @Inject
    private Drivers drivers;

    /**
     * Creates a new instance of Identifier
     */
    public Resolver() {
    }

    /**
     * Retrieves representation of an instance of ubicua.resolver.service.Identifier
     * @param identifier
     * @return an instance of DidDocument
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("identifiers/{identifier}")
    public Response getDidDocument(@PathParam("identifier") String identifier) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Processing identifier %s", identifier));
        }
        
        try {
            identifier = URLDecoder.decode(identifier, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            
            if (LOG.isErrorEnabled()) {
                LOG.error(String.format("Request problem: %s", ex.getMessage()), ex);
            }
            
            return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ex.getLocalizedMessage())
                .build();
        }
        
        ResolveResult resolveResult;
        
        try {
            resolveResult = resolve(identifier);
        } catch (ResolutionException ex) {
            if (LOG.isErrorEnabled()) {
                LOG.error(String.format("Resolver problem for %s: %s", identifier, ex.getMessage()), ex);
            }
    
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(String.format("Resolver problem for %s: %s", identifier, ex.getMessage()))
                    .build();
        }

        if (LOG.isInfoEnabled()) {
            LOG.info(String.format("Resolver result for %s: %s", identifier, resolveResult));
        }
        
        if (resolveResult == null || (resolveResult.getDidDocument() == null && resolveResult.getContent() == null)) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(String.format("No resolve result for %s: %s", identifier, resolveResult))
                    .build();
        }

        String message;
        try {
            message = resolveResult.toJson();
        } catch (JsonProcessingException ex){
            message = String.format("Resolver problem for %s: %s", identifier, ex.getMessage());
        }
        return Response
                .status(Response.Status.OK)
                .entity(message)
                .build();
    }

    @Override
    public ResolveResult resolve(String identifier) throws ResolutionException {
        UniResolver resolver = new LocalUniResolver(drivers);
        return resolver.resolve(identifier);
    }

    @Override
    public ResolveResult resolve(String identifier, Map<String, String> options) throws ResolutionException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Map<String, Map<String, Object>> properties() throws ResolutionException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
