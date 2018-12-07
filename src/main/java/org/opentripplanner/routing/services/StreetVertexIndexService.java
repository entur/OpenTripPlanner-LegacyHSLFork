/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.services;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.vertextype.BikeRentalStationVertex;
import org.opentripplanner.routing.vertextype.TransitStop;

import java.util.Collection;
import java.util.List;

public interface StreetVertexIndexService {

    /**
     * Returns the vertices intersecting with the specified envelope.
     * 
     * @param envelope
     * @return
     */
    public Collection<Vertex> getVerticesForEnvelope(Envelope envelope);

    /**
     * Return the edges whose geometry intersect with the specified envelope. Warning: edges w/o
     * geometry will not be indexed.
     * 
     * @param envelope
     * @return
     */
    public Collection<Edge> getEdgesForEnvelope(Envelope envelope);

    /**
     * @param coordinate
     * @param radiusMeters
     * @return The transit stops within a certain radius of the given location.
     */
    public List<TransitStop> getNearbyTransitStops(Coordinate coordinate, double radiusMeters);

    /**
     * @param envelope
     * @return The transit stops within an envelope.
     */
    public List<TransitStop> getTransitStopForEnvelope(Envelope envelope);

    /**
     * @param envelope
     * @return The bike parks within an envelope.
     */
    public List<BikeRentalStationVertex> getBikeRentalStationForEnvelope(Envelope envelope);

    public void addToSpatialIndex(Vertex v);

    /**
     * Finds the appropriate vertex for this location.
     * 
     * @param place
     * @param options
     * @param endVertex: whether this is a start vertex (if it's false) or end vertex (if it's true)
     * @return
     */
    public Vertex getVertexForLocation(GenericLocation place, RoutingRequest options,
                                       boolean endVertex);

}
