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

package org.opentripplanner;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.onebusaway2.gtfs.model.calendar.CalendarServiceData;
import org.opentripplanner.graph_builder.module.StreetLinkerModule;
import org.opentripplanner.gtfs.GtfsContext;
import org.opentripplanner.routing.edgetype.factory.GTFSPatternHopFactory;
import org.opentripplanner.routing.edgetype.factory.TransferGraphLinker;
import org.opentripplanner.routing.graph.Graph;

import static org.opentripplanner.calendar.impl.CalendarServiceDataFactoryImpl.createCalendarServiceData;
import static org.opentripplanner.gtfs.GtfsContextBuilder.contextBuilder;

public class ConstantsForTests {

    public static final String CALTRAIN_GTFS = "src/test/resources/caltrain_gtfs.zip";

    public static final String PORTLAND_GTFS = "src/test/resources/google_transit.zip";

    public static final String KCM_GTFS = "src/test/resources/kcm_gtfs.zip";
    
    public static final String FAKE_GTFS = "src/test/resources/testagency.zip";

    public static final String FARE_COMPONENT_GTFS = "src/test/resources/farecomponent_gtfs.zip";

    private static ConstantsForTests instance = null;

    private Graph portlandGraph = null;

    private GtfsContext portlandContext = null;

    private ConstantsForTests() {

    }

    public static ConstantsForTests getInstance() {
        if (instance == null) {
            instance = new ConstantsForTests();
        }
        return instance;
    }

    public GtfsContext getPortlandContext() {
        if (portlandGraph == null) {
            setupPortland();
        }
        return portlandContext;
    }

    public Graph getPortlandGraph() {
        if (portlandGraph == null) {
            setupPortland();
        }
        return portlandGraph;
    }

    private void setupPortland() {
        try {
            portlandGraph = new Graph();
            portlandContext = contextBuilder(ConstantsForTests.PORTLAND_GTFS)
                    .withGraphBuilderAnnotationsAndDeduplicator(portlandGraph)
                    .build();
            GTFSPatternHopFactory factory = new GTFSPatternHopFactory(portlandContext);
            factory.run(portlandGraph);
            TransferGraphLinker linker = new TransferGraphLinker(portlandGraph);
            linker.run();
            // TODO: eliminate GTFSContext
            // this is now making a duplicate calendarservicedata but it's oh so practical
            portlandGraph.putService(
                    CalendarServiceData.class,
                    createCalendarServiceData(portlandContext.getDao())
            );
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        StreetLinkerModule ttsnm = new StreetLinkerModule();
        ttsnm.buildGraph(portlandGraph, new HashMap<Class<?>, Object>());
    }

    public static Graph buildGraph(String path) {
        Graph graph = new Graph();
        GtfsContext context;
        try {
            context = contextBuilder(path).build();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        GTFSPatternHopFactory factory = new GTFSPatternHopFactory(context);
        factory.run(graph);
        graph.putService(
                CalendarServiceData.class,
                createCalendarServiceData(context.getDao())
        );
        return graph;
    }

}
