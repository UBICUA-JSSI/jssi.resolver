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
package jssi.resolver.driver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import jssi.resolver.driver.sov.SovConfig;
import jssi.resolver.driver.sov.SovDriver;
import uniresolver.ResolutionException;
import uniresolver.result.ResolveResult;

/**
 * REST Web Service
 *
 * @author UBICUA
 */
@Path("/")
@RequestScoped
public class Resolver {
    
    private static final Logger LOG = LoggerFactory.getLogger(Resolver.class);

    @Context
    private UriInfo context;
    
    @Inject 
    SovConfig config;

    /**
     * Creates a new instance of Resolver
     */
    public Resolver() {
    }

    /**
     * Retrieves representation of an instance of ubicua.resolver.driver.service.Resolver
     * @param identifier
     * @return an instance of java.lang.String
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("identifiers/{identifier}")
    public Response getDidDocument(@PathParam("identifier") String identifier) {
        
        SovDriver driver = new SovDriver(config);
        try {
            ResolveResult result = driver.resolve(identifier);
            if(result == null){
                return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(String.format("Resolver result is null for %s", identifier))
                    .build();
            }
            
            return Response
                .status(Response.Status.OK)
                .entity(result.toJson())
                .build();
        } catch (ResolutionException | JsonProcessingException ex){
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(String.format("Resolver problem for %s: %s", identifier, ex.getMessage()))
                    .build();
        }
    }
}
