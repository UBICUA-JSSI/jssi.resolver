/*
 * The MIT License
 *
 * Copyright 2020 UBICUA.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
import jssi.resolver.driver.dns.DnsConfig;
import jssi.resolver.driver.dns.DnsDriver;
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
    DnsConfig config;

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
        
        DnsDriver driver = new DnsDriver(config);
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
