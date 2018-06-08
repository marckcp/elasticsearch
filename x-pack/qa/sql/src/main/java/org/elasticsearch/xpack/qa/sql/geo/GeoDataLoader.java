/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.qa.sql.geo;

import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.xpack.qa.sql.jdbc.SqlSpecTestCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.elasticsearch.xpack.qa.sql.jdbc.DataLoader.createString;
import static org.elasticsearch.xpack.qa.sql.jdbc.DataLoader.readFromJarUrl;

public class GeoDataLoader {

    public static void main(String[] args) throws Exception {
        try (RestClient client = RestClient.builder(new HttpHost("localhost", 9200)).build()) {
            loadDatasetIntoEs(client);
            Loggers.getLogger(GeoDataLoader.class).info("Geo data loaded");
        }
    }

    protected static void loadDatasetIntoEs(RestClient client) throws Exception {
        loadDatasetIntoEs(client, "ogc");
        makeFilteredAlias(client, "lakes", "ogc", "\"term\" : { \"ogc_type\" : \"lakes\" }");
        makeFilteredAlias(client, "road_segments", "ogc", "\"term\" : { \"ogc_type\" : \"road_segments\" }");
        makeFilteredAlias(client, "divided_routes", "ogc", "\"term\" : { \"ogc_type\" : \"divided_routes\" }");
        makeFilteredAlias(client, "forests", "ogc", "\"term\" : { \"ogc_type\" : \"forests\" }");
        makeFilteredAlias(client, "bridges", "ogc", "\"term\" : { \"ogc_type\" : \"bridges\" }");
        makeFilteredAlias(client, "streams", "ogc", "\"term\" : { \"ogc_type\" : \"streams\" }");
        makeFilteredAlias(client, "buildings", "ogc", "\"term\" : { \"ogc_type\" : \"buildings\" }");
        makeFilteredAlias(client, "ponds", "ogc", "\"term\" : { \"ogc_type\" : \"ponds\" }");
        makeFilteredAlias(client, "named_places", "ogc", "\"term\" : { \"ogc_type\" : \"named_places\" }");
        makeFilteredAlias(client, "map_neatlines", "ogc", "\"term\" : { \"ogc_type\" : \"map_neatlines\" }");
    }

    protected static void loadDatasetIntoEs(RestClient client, String index) throws Exception {
        XContentBuilder createIndex = JsonXContent.contentBuilder().startObject();
        createIndex.startObject("settings");
        {
            createIndex.field("number_of_shards", 1);
        }
        createIndex.endObject();
        createIndex.startObject("mappings");
        {
            createIndex.startObject("doc");
            {
                createIndex.startObject("properties");
                {
                    // Common
                    createIndex.startObject("ogc_type").field("type", "keyword").endObject();
                    createIndex.startObject("fid").field("type", "integer").endObject();
                    createString("name", createIndex);

                    // Type specific
                    createIndex.startObject("shore").field("type", "geo_shape").endObject(); // lakes

                    createString("aliases", createIndex); // road_segments
                    createIndex.startObject("num_lanes").field("type", "integer").endObject(); // road_segments, divided_routes
                    createIndex.startObject("centerline").field("type", "geo_shape").endObject(); // road_segments, streams

                    createIndex.startObject("centerlines").field("type", "geo_shape").endObject(); // divided_routes

                    createIndex.startObject("boundary").field("type", "geo_shape").endObject(); // forests, named_places

                    createIndex.startObject("position").field("type", "geo_shape").endObject(); // bridges, buildings

                    createString("address", createIndex); // buildings
                    createIndex.startObject("footprint").field("type", "geo_shape").endObject(); // buildings

                    createIndex.startObject("type").field("type", "keyword").endObject(); // ponds
                    createIndex.startObject("shores").field("type", "geo_shape").endObject(); // ponds

                    createIndex.startObject("neatline").field("type", "geo_shape").endObject(); // map_neatlines

                }
                createIndex.endObject();
            }
            createIndex.endObject();
        }
        createIndex.endObject().endObject();

        client.performRequest("PUT", "/" + index, emptyMap(), new StringEntity(Strings.toString(createIndex),
                        ContentType.APPLICATION_JSON));

        String bulk = readResource("/ogc/ogc.json");

        Response response = client.performRequest("POST", "/" + index + "/doc/_bulk", singletonMap("refresh", "true"),
                new StringEntity(bulk, ContentType.APPLICATION_JSON));

        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new RuntimeException("Cannot load data " + response.getStatusLine());
        }

        String bulkResponseStr = EntityUtils.toString(response.getEntity());
        Map<String, Object> bulkResponseMap =  XContentHelper.convertToMap(JsonXContent.jsonXContent, bulkResponseStr, false);

        if ((boolean) bulkResponseMap.get("errors")) {
            throw new RuntimeException("Failed to load bulk data " + bulkResponseStr);
        }
    }


    public static void makeFilteredAlias(RestClient client, String aliasName, String index, String filter) throws Exception {
        client.performRequest("POST", "/" + index + "/_alias/" + aliasName, Collections.emptyMap(),
                new StringEntity("{\"filter\" : { " + filter + " } }", ContentType.APPLICATION_JSON));
    }

    private static String readResource(String location) throws IOException {
        URL dataSet = SqlSpecTestCase.class.getResource(location);
        if (dataSet == null) {
            throw new IllegalArgumentException("Can't find [" + location + "]");
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(readFromJarUrl(dataSet), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while(line != null) {
                if (line.trim().startsWith("//") == false) {
                    builder.append(line);
                    builder.append('\n');
                }
                line = reader.readLine();
            }
            return builder.toString();
        }
    }

}