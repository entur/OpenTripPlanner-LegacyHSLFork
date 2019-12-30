package org.opentripplanner.graph_builder;

import com.google.common.collect.Lists;
import org.opentripplanner.datastore.CatalogDataSource;
import org.opentripplanner.datastore.DataSource;
import org.opentripplanner.ext.transferanalyzer.DirectTransferAnalyzer;
import org.opentripplanner.graph_builder.model.GtfsBundle;
import org.opentripplanner.graph_builder.module.DirectTransferGenerator;
import org.opentripplanner.graph_builder.module.EmbedConfig;
import org.opentripplanner.graph_builder.module.GtfsModule;
import org.opentripplanner.graph_builder.module.PruneFloatingIslands;
import org.opentripplanner.graph_builder.module.StreetLinkerModule;
import org.opentripplanner.graph_builder.module.TransitToTaggedStopsModule;
import org.opentripplanner.graph_builder.module.map.BusRouteStreetMatcher;
import org.opentripplanner.graph_builder.module.ned.DegreeGridNEDTileSource;
import org.opentripplanner.graph_builder.module.ned.ElevationModule;
import org.opentripplanner.graph_builder.module.ned.GeotiffGridCoverageFactoryImpl;
import org.opentripplanner.graph_builder.module.ned.NEDGridCoverageFactoryImpl;
import org.opentripplanner.graph_builder.module.osm.OpenStreetMapModule;
import org.opentripplanner.graph_builder.services.DefaultStreetEdgeFactory;
import org.opentripplanner.graph_builder.services.GraphBuilderModule;
import org.opentripplanner.graph_builder.services.ned.ElevationGridCoverageFactory;
import org.opentripplanner.openstreetmap.BinaryOpenStreetMapProvider;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.standalone.config.GraphBuildParameters;
import org.opentripplanner.standalone.config.S3BucketConfig;
import org.opentripplanner.util.OTPFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.opentripplanner.datastore.FileType.DEM;
import static org.opentripplanner.datastore.FileType.GTFS;
import static org.opentripplanner.datastore.FileType.NETEX;
import static org.opentripplanner.datastore.FileType.OSM;
import static org.opentripplanner.netex.configure.NetexConfig.netexModule;

/**
 * This makes a Graph out of various inputs like GTFS and OSM.
 * It is modular: GraphBuilderModules are placed in a list and run in sequence.
 */
public class GraphBuilder implements Runnable {

    private static Logger LOG = LoggerFactory.getLogger(GraphBuilder.class);

    private final List<GraphBuilderModule> graphBuilderModules = new ArrayList<>();

    private final DataSource graphOut;

    private final Graph graph;

    private GraphBuilder(Graph graph, DataSource graphOut) {
        this.graphOut = graphOut;
        this.graph = graph == null ? new Graph() : graph;
    }

    private void addModule(GraphBuilderModule loader) {
        graphBuilderModules.add(loader);
    }

    public Graph getGraph() {
        return graph;
    }

    public void run() {
         // Record how long it takes to build the graph, purely for informational purposes.
        long startTime = System.currentTimeMillis();

        if (graphOut != null) {
            // Abort building a graph if the file can not be saved
            if (graphOut.exists()) {
                LOG.info("Graph already exists and will be overwritten at the end of the build process.");
            }
            if (!graphOut.isWritable()) {
                throw new RuntimeException(
                        "Cannot create or write to graph at: " + graphOut.path()
                );
            }
        }

        // Check all graph builder inputs, and fail fast to avoid waiting until the build process
        // advances.
        for (GraphBuilderModule builder : graphBuilderModules) {
            builder.checkInputs();
        }

        DataImportIssueStore issueStore = new DataImportIssueStore(true);
        HashMap<Class<?>, Object> extra = new HashMap<Class<?>, Object>();

        for (GraphBuilderModule load : graphBuilderModules) {
            load.buildGraph(graph, extra, issueStore);
        }
        issueStore.summarize();

        if (graphOut != null) {
            graph.save(graphOut);
        } else {
            LOG.info("Not saving graph to disk, as requested.");
        }
        long endTime = System.currentTimeMillis();
        LOG.info(String.format("Graph building took %.1f minutes.", (endTime - startTime) / 1000 / 60.0));
    }

    /**
     * Factory method to create and configure a GraphBuilder with all the appropriate modules to
     * build a graph from the given data source and configuration directory.
     */
    public static GraphBuilder create(
            GraphBuildParameters config,
            GraphBuilderDataSources dataSources,
            EmbedConfig embedConfig,
            Graph baseGraph
    ) {

        boolean hasOsm  = dataSources.has(OSM);
        // TODO OTP2 - Refactor fetchElevationUS to be used as a regular datasource
        //           - By supporting AWS buckets and NED downloads as a datasource,
        //           - the '|| config.fetchElevationUS' would become obsolete.
        boolean hasDem  = dataSources.has(DEM) || config.fetchElevationUS;
        boolean hasGtfs = dataSources.has(GTFS);
        boolean hasNetex = dataSources.has(NETEX);
        boolean hasTransitData = hasGtfs || hasNetex;

        GraphBuilder graphBuilder = new GraphBuilder(baseGraph, dataSources.getOutputGraph());


        if ( hasOsm ) {
            List<BinaryOpenStreetMapProvider> osmProviders = Lists.newArrayList();
            for (DataSource osmFile : dataSources.get(OSM)) {
                osmProviders.add(
                        new BinaryOpenStreetMapProvider(osmFile, config.osmCacheDataInMem)
                );
            }
            OpenStreetMapModule osmModule = new OpenStreetMapModule(osmProviders);
            DefaultStreetEdgeFactory streetEdgeFactory = new DefaultStreetEdgeFactory();
            streetEdgeFactory.useElevationData = hasDem;
            osmModule.edgeFactory = streetEdgeFactory;
            osmModule.customNamer = config.customNamer;
            osmModule.setDefaultWayPropertySetSource(config.wayPropertySet);
            osmModule.skipVisibility = !config.areaVisibility;
            osmModule.platformEntriesLinking = config.platformEntriesLinking;
            osmModule.staticBikeRental = config.staticBikeRental;
            osmModule.staticBikeParkAndRide = config.staticBikeParkAndRide;
            osmModule.staticParkAndRide = config.staticParkAndRide;
            osmModule.banDiscouragedWalking = config.banDiscouragedWalking;
            osmModule.banDiscouragedBiking = config.banDiscouragedBiking;
            graphBuilder.addModule(osmModule);
            PruneFloatingIslands pruneFloatingIslands = new PruneFloatingIslands();
            pruneFloatingIslands.setPruningThresholdIslandWithoutStops(config.pruningThresholdIslandWithoutStops);
            pruneFloatingIslands.setPruningThresholdIslandWithStops(config.pruningThresholdIslandWithStops);
            graphBuilder.addModule(pruneFloatingIslands);
        }
        if ( hasGtfs ) {
            List<GtfsBundle> gtfsBundles = Lists.newArrayList();
            for (DataSource gtfsData : dataSources.get(GTFS)) {

                GtfsBundle gtfsBundle = new GtfsBundle((CatalogDataSource)gtfsData);

                // TODO OTP2 - In OTP2 we have deleted the transfer edges from the street graph.
                //           - The new transfer generation do not take this config param into
                //           - account any more. This needs some investigation and probably
                //           - a fix, but we are unsure if this is used any more. The Pathways.txt
                //           - and osm import replaces this functionality.
                gtfsBundle.setTransfersTxtDefinesStationPaths(config.useTransfersTxt);

                if (config.parentStopLinking) {
                    gtfsBundle.linkStopsToParentStations = true;
                }
                gtfsBundle.parentStationTransfers = config.stationTransfers;
                gtfsBundle.subwayAccessTime = config.getSubwayAccessTimeSeconds();
                gtfsBundle.maxInterlineDistance = config.maxInterlineDistance;
                gtfsBundles.add(gtfsBundle);
            }
            GtfsModule gtfsModule = new GtfsModule(gtfsBundles);
            gtfsModule.setFareServiceFactory(config.fareServiceFactory);
            graphBuilder.addModule(gtfsModule);
        }

        if( hasNetex ) {
            graphBuilder.addModule(netexModule(config, dataSources.get(NETEX)));
        }

        if(hasTransitData && hasOsm) {
            if (config.matchBusRoutesToStreets) {
                graphBuilder.addModule(new BusRouteStreetMatcher());
            }
            graphBuilder.addModule(new TransitToTaggedStopsModule());
        }

        // This module is outside the hasGTFS conditional block because it also links things like bike rental
        // which need to be handled even when there's no transit.
        StreetLinkerModule streetLinkerModule = new StreetLinkerModule();
        streetLinkerModule.setAddExtraEdgesToAreas(config.areaVisibility);
        graphBuilder.addModule(streetLinkerModule);
        // Load elevation data and apply it to the streets.
        // We want to do run this module after loading the OSM street network but before finding transfers.
        if (config.elevationBucket != null) {
            // Download the elevation tiles from an Amazon S3 bucket
            S3BucketConfig bucketConfig = config.elevationBucket;
            File cacheDirectory = new File(dataSources.getCacheDirectory(), "ned");
            DegreeGridNEDTileSource awsTileSource = new DegreeGridNEDTileSource();
            awsTileSource.awsAccessKey = bucketConfig.accessKey;
            awsTileSource.awsSecretKey = bucketConfig.secretKey;
            awsTileSource.awsBucketName = bucketConfig.bucketName;
            NEDGridCoverageFactoryImpl gcf = new NEDGridCoverageFactoryImpl(cacheDirectory);
            gcf.tileSource = awsTileSource;
            GraphBuilderModule elevationBuilder = new ElevationModule(
                    gcf,
                    config.elevationUnitMultiplier,
                    config.distanceBetweenElevationSamples
            );
            graphBuilder.addModule(elevationBuilder);
        } else if (config.fetchElevationUS) {
            // Download the elevation tiles from the official web service
            File cacheDirectory = new File(dataSources.getCacheDirectory(), "ned");
            ElevationGridCoverageFactory gcf = new NEDGridCoverageFactoryImpl(cacheDirectory);
            GraphBuilderModule elevationBuilder = new ElevationModule(
                    gcf,
                    config.elevationUnitMultiplier,
                    config.distanceBetweenElevationSamples
            );
            graphBuilder.addModule(elevationBuilder);
        } else if (dataSources.has(DEM)) {
            // Load the elevation from a file in the graph inputs directory
            for (DataSource demSource : dataSources.get(DEM)) {
                ElevationGridCoverageFactory gcf = new GeotiffGridCoverageFactoryImpl(demSource);
                GraphBuilderModule elevationBuilder = new ElevationModule(
                        gcf,
                        config.elevationUnitMultiplier,
                        config.distanceBetweenElevationSamples
                );
                graphBuilder.addModule(elevationBuilder);
            }
        }
        if ( hasTransitData ) {
            // The stops can be linked to each other once they are already linked to the street network.
            if ( ! config.useTransfersTxt) {
                // This module will use streets or straight line distance depending on whether OSM data is found in the graph.
                graphBuilder.addModule(new DirectTransferGenerator(config.maxTransferDistance));
            }
            // Analyze routing between stops to generate report
            if (OTPFeature.TransferAnalyzer.isOn()) {
                graphBuilder.addModule(new DirectTransferAnalyzer(config.maxTransferDistance));
            }
        }
        graphBuilder.addModule(embedConfig);

        if (config.dataImportReport) {
            graphBuilder.addModule(
                    new DataImportIssuesToHTML(
                            dataSources.getBuildReportDir(),
                            config.maxDataImportIssuesPerFile
                    )
            );
        }
        return graphBuilder;
    }
}

