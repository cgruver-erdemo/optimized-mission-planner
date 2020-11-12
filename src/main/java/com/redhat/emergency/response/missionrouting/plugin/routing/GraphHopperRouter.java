/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.emergency.response.missionrouting.plugin.routing;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.StreamSupport;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.redhat.emergency.response.missionrouting.domain.Coordinates;
import com.redhat.emergency.response.missionrouting.service.distance.DistanceCalculationException;
import com.redhat.emergency.response.missionrouting.service.distance.DistanceCalculator;
import com.redhat.emergency.response.missionrouting.service.region.BoundingBox;
import com.redhat.emergency.response.missionrouting.service.region.Region;
import com.redhat.emergency.response.missionrouting.service.route.Router;

import io.quarkus.arc.properties.IfBuildProperty;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;

/**
 * Provides geographical information needed for route optimization.
 */
@ApplicationScoped
@IfBuildProperty(name = "app.routing.engine", stringValue = "graphhopper", enableIfMissing = true)
class GraphHopperRouter implements Router, DistanceCalculator, Region {

    @Inject
    GraphHopperOSM graphHopper;

    @Override
    public List<Coordinates> getPath(Coordinates from, Coordinates to) {
        GHRequest ghRequest = new GHRequest(
                from.latitude().doubleValue(),
                from.longitude().doubleValue(),
                to.latitude().doubleValue(),
                to.longitude().doubleValue());
        PointList points = graphHopper.route(ghRequest).getBest().getPoints();
        return StreamSupport.stream(points.spliterator(), false)
                .map(ghPoint3D -> Coordinates.valueOf(ghPoint3D.lat, ghPoint3D.lon))
                .collect(toList());
    }

    @Override
    public long travelTimeMillis(Coordinates from, Coordinates to) {
        GHRequest ghRequest = new GHRequest(
                from.latitude().doubleValue(),
                from.longitude().doubleValue(),
                to.latitude().doubleValue(),
                to.longitude().doubleValue());
        GHResponse ghResponse = graphHopper.route(ghRequest);
        // TODO return wrapper that can hold both the result and error explanation instead of throwing exception
        if (ghResponse.hasErrors()) {
            throw new DistanceCalculationException("No route from " + from + " to " + to, ghResponse.getErrors().get(0));
        }
        return ghResponse.getBest().getTime();
    }

    @Override
    public BoundingBox getBounds() {
        BBox bounds = graphHopper.getGraphHopperStorage().getBounds();
        return new BoundingBox(
                Coordinates.valueOf(bounds.minLat, bounds.minLon),
                Coordinates.valueOf(bounds.maxLat, bounds.maxLon));
    }
}
