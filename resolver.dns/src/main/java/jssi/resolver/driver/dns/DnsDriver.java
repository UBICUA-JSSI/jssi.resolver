package jssi.resolver.driver.dns;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.URIRecord;

import uniresolver.ResolutionException;
import uniresolver.driver.Driver;
import uniresolver.result.ResolveResult;

public class DnsDriver implements Driver {

    private static final Logger LOG = LoggerFactory.getLogger(DnsDriver.class);

    public static final Pattern DNS_PATTERN = Pattern.compile("^((?:(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*(?:[A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9]))$");

    private Resolver resolver = null;
    private final DnsConfig config;

    public DnsDriver(DnsConfig config) {
        this.config = config;
    }

    @Override
    public ResolveResult resolve(String identifier) throws ResolutionException {
        // open pool
        if (resolver == null) {
            openResolver();
        }
        // parse identifier
        Matcher matcher = DNS_PATTERN.matcher(identifier);
        if (!matcher.matches()) {
            return null;
        }
        // DNS lookup
        Lookup lookup = null;
        Record[] records;

        try {
            lookup = new Lookup("_did." + identifier, Type.URI);
            lookup.setResolver(resolver);
            records = lookup.run();
        } catch (TextParseException ex) {
            throw new ResolutionException("DNS resolution problem: " + ex.getMessage() + (lookup != null ? (" (" + lookup.getErrorString() + ")") : ""));
        }

        if (lookup.getErrorString() != null && !"successful".equals(lookup.getErrorString())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("For identifier " + identifier + " got error: " + lookup.getErrorString());
            }
            throw new ResolutionException("DNS resolution error: " + lookup.getErrorString());
        }

        if (records == null) {
            return null;
        }

        for (Record record : records) {
            URIRecord uri = (URIRecord) record;
            if (LOG.isDebugEnabled()) {
                LOG.debug("For identifier " + identifier + " found entry " + uri.getTarget() + " with preference " + uri.getPriority());
            }
        }

        String did = records.length > 0 ? ((URIRecord) records[0]).getTarget() : null;
        Integer priority = records.length > 0 ? ((URIRecord) records[0]).getPriority() : null;
        // create METHOD METADATA
        Map<String, Object> methodMetadata = new LinkedHashMap<>();
        if (did != null) {
            methodMetadata.put("redirect", did);
        }
        if (priority != null) {
            methodMetadata.put("priority", priority);
        }

        // create RESOLVE RESULT
        ResolveResult resolveResult = ResolveResult.build(null, null, null, null, methodMetadata);
        // done
        return resolveResult;
    }

    @Override
    public Map<String, Object> properties() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private void openResolver() throws ResolutionException {
        // create resolver
        try {

            if (config.getDnsServers().length != 0) {
                resolver = new ExtendedResolver(config.getDnsServers());
                if (LOG.isInfoEnabled()) {
                    LOG.info("Created DNS resolver with servers " + Arrays.asList(config.getDnsServers()) + ".");
                }
            } else {
                resolver = new ExtendedResolver();
                if (LOG.isInfoEnabled()) {
                    LOG.info("Created default DNS resolver.");
                }
            }
        } catch (UnknownHostException ex) {
            throw new ResolutionException("Unable to create DNS resolver: " + ex.getMessage(), ex);
        }
    }
}
