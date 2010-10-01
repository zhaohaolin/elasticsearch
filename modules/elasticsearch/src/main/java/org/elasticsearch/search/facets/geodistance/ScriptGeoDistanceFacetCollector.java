/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.facets.geodistance;

import org.apache.lucene.index.IndexReader;
import org.elasticsearch.common.lucene.geo.GeoDistance;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.field.function.FieldsFunction;
import org.elasticsearch.index.field.function.script.ScriptFieldsFunction;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Map;

/**
 * @author kimchy (shay.banon)
 */
public class ScriptGeoDistanceFacetCollector extends GeoDistanceFacetCollector {

    private final FieldsFunction valueFunction;

    private final Map<String, Object> params;

    public ScriptGeoDistanceFacetCollector(String facetName, String fieldName, double lat, double lon, DistanceUnit unit, GeoDistance geoDistance,
                                           GeoDistanceFacet.Entry[] entries, SearchContext context,
                                           String scriptLang, String script, Map<String, Object> params) {
        super(facetName, fieldName, lat, lon, unit, geoDistance, entries, context);
        this.params = params;

        this.valueFunction = new ScriptFieldsFunction(scriptLang, script, context.scriptService(), context.mapperService(), context.fieldDataCache());
    }

    @Override protected void doSetNextReader(IndexReader reader, int docBase) throws IOException {
        super.doSetNextReader(reader, docBase);
        valueFunction.setNextReader(reader);
    }

    @Override protected void doCollect(int doc) throws IOException {
        if (!latFieldData.hasValue(doc) || !lonFieldData.hasValue(doc)) {
            return;
        }

        double value = ((Number) valueFunction.execute(doc, params)).doubleValue();

        if (latFieldData.multiValued()) {
            double[] lats = latFieldData.doubleValues(doc);
            double[] lons = latFieldData.doubleValues(doc);
            for (int i = 0; i < lats.length; i++) {
                double distance = geoDistance.calculate(lat, lon, lats[i], lons[i], unit);
                for (GeoDistanceFacet.Entry entry : entries) {
                    if (distance >= entry.getFrom() && distance < entry.getTo()) {
                        entry.count++;
                        entry.total += value;
                    }
                }
            }
        } else {
            double distance = geoDistance.calculate(lat, lon, latFieldData.doubleValue(doc), lonFieldData.doubleValue(doc), unit);
            for (GeoDistanceFacet.Entry entry : entries) {
                if (distance >= entry.getFrom() && distance < entry.getTo()) {
                    entry.count++;
                    entry.total += value;
                }
            }
        }
    }
}
