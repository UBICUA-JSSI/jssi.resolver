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
package jssi.resolver.local;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jssi.resolver.driver.http.HttpDriver;
import uniresolver.driver.Driver;

/**
 *
 * @author UBICUA
 */
@ApplicationScoped
public class Drivers {
    
    private static final Logger LOG = LoggerFactory.getLogger(Drivers.class);

    private final Map<String, Driver> drivers = new HashMap<String, Driver>();

    public void init(String path) throws FileNotFoundException, IOException {

        final Gson gson = new Gson();

        try (Reader reader = new FileReader(new File(path))) {

            JsonObject root = gson.fromJson(reader, JsonObject.class);
            JsonArray jsonArrayDrivers = root.getAsJsonArray("drivers");

            int i = 0;

            for (Iterator<JsonElement> jsonElementsDrivers = jsonArrayDrivers.iterator(); jsonElementsDrivers.hasNext();) {

                i++;

                JsonObject item = (JsonObject) jsonElementsDrivers.next();

                String id = item.has("id") ? item.get("id").getAsString() : null;
                String pattern = item.has("pattern") ? item.get("pattern").getAsString() : null;
                String image = item.has("image") ? item.get("image").getAsString() : null;
                String imagePort = item.has("imagePort") ? item.get("imagePort").getAsString() : null;
                String imageProperties = item.has("imageProperties") ? item.get("imageProperties").getAsString() : null;
                String url = item.has("url") ? item.get("url").getAsString() : null;

                if (pattern == null) {
                    throw new IllegalArgumentException("Missing 'pattern' entry in driver configuration.");
                }
                if (image == null && url == null) {
                    throw new IllegalArgumentException("Missing 'image' and 'url' entry in driver configuration (need either one).");
                }

                HttpDriver driver = new HttpDriver();
                driver.setPattern(pattern);

                if (url != null) {
                    driver.setResolveUri(url);
                } else {

                    String httpDriverUri = image.substring(image.indexOf("/"));
                    if (httpDriverUri.contains(":")) {
                        httpDriverUri = httpDriverUri.substring(0, httpDriverUri.indexOf(":"));
                    }
                    httpDriverUri = String.format("http://localhost:%s/%s/", (imagePort != null ? imagePort : "8080"), httpDriverUri);

                    driver.setResolveUri(httpDriverUri + "1.0/identifiers/$1");

                    if ("true".equals(imageProperties)) {
                        driver.setPropertiesUri(httpDriverUri + "1.0/properties");
                    }
                }

                if (id == null) {
                    id = "driver";
                    if (image != null) {
                        id += "-" + image;
                    }
                    if (image == null || drivers.containsKey(id)) {
                        id += "-" + Integer.toString(i);
                    }
                }

                drivers.put(id, driver);

                if (LOG.isInfoEnabled()) {
                    LOG.info("Added driver '" + id + "' at " + driver.getResolveUri() + " (" + driver.getPropertiesUri() + ")");
                }
            }
        }
    }

    public Map<String, Driver> getDrivers() {
        return drivers;
    }
    
}
