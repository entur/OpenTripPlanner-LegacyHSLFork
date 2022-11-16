package org.opentripplanner.standalone.config;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V1_5;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_0;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_1;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.opentripplanner.common.geometry.CompactElevationProfile;
import org.opentripplanner.datastore.api.OtpDataStoreConfig;
import org.opentripplanner.ext.dataoverlay.configuration.DataOverlayConfig;
import org.opentripplanner.ext.fares.FaresConfiguration;
import org.opentripplanner.graph_builder.module.ned.parameter.DemExtractParametersList;
import org.opentripplanner.graph_builder.module.osm.parameters.OsmDefaultParameters;
import org.opentripplanner.graph_builder.module.osm.parameters.OsmExtractParametersList;
import org.opentripplanner.graph_builder.services.osm.CustomNamer;
import org.opentripplanner.model.calendar.ServiceDateInterval;
import org.opentripplanner.netex.config.NetexFeedParameters;
import org.opentripplanner.routing.api.request.RegularRouteRequest;
import org.opentripplanner.routing.fares.FareServiceFactory;
import org.opentripplanner.standalone.config.buildconfig.DemConfig;
import org.opentripplanner.standalone.config.buildconfig.NetexConfig;
import org.opentripplanner.standalone.config.buildconfig.OsmConfig;
import org.opentripplanner.standalone.config.buildconfig.S3BucketConfig;
import org.opentripplanner.standalone.config.buildconfig.TransferRequestConfig;
import org.opentripplanner.standalone.config.buildconfig.TransitFeedConfig;
import org.opentripplanner.standalone.config.buildconfig.TransitFeeds;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;
import org.opentripplanner.standalone.config.sandbox.DataOverlayConfigMapper;
import org.opentripplanner.util.lang.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is an object representation of the 'build-config.json'.
 * <p>
 * These are parameters that when changed, necessitate a Graph rebuild. They are distinct from the
 * RouterParameters which can be applied to a pre-built graph or on the fly at runtime. Eventually
 * both classes may be initialized from the same config file so make sure there is no overlap in the
 * JSON keys used.
 * <p>
 * These used to be command line parameters, but there were getting to be too many of them and
 * besides, we want to allow different graph build configuration for each Graph.
 * <p>
 * ones trigger a rebuild ...or just feed the same JSON tree to two different classes, one of which
 * is the build configuration and the other is the router configuration.
 */
public class BuildConfig implements OtpDataStoreConfig {

  private static final Logger LOG = LoggerFactory.getLogger(BuildConfig.class);

  public static final BuildConfig DEFAULT = new BuildConfig(
    MissingNode.getInstance(),
    "DEFAULT",
    false
  );

  private static final double DEFAULT_SUBWAY_ACCESS_TIME_MINUTES = 2.0;

  /**
   * Match all filenames that contains "gtfs". The pattern is NOT Case sensitive.
   */
  private static final String DEFAULT_GTFS_PATTERN = "(?i)gtfs";

  /**
   * Match all filenames that contain "netex". The pattern is NOT Case sensitive.
   */
  private static final String DEFAULT_NETEX_PATTERN = "(?i)netex";

  /**
   * Match all filenames that ends with suffix {@code .pbf}, {@code .osm} or {@code .osm.xml}. The
   * pattern is NOT Case sensitive.
   */
  private static final String DEFAULT_OSM_PATTERN = "(?i)(\\.pbf|\\.osm|\\.osm\\.xml)$";

  /**
   * Default: {@code (?i).tiff?$} - Match all filenames that ends with suffix {@code .tif} or
   * {@code .tiff}. The pattern is NOT Case sensitive.
   */
  private static final String DEFAULT_DEM_PATTERN = "(?i)\\.tiff?$";

  /**
   * The root adaptor kept for reference and (de)serialization.
   */
  private final NodeAdapter root;

  public final String configVersion;

  public final boolean dataImportReport;

  public final int maxDataImportIssuesPerFile;

  public final double subwayAccessTime;

  public final boolean embedRouterConfig;

  public final boolean areaVisibility;

  public final boolean platformEntriesLinking;

  public final boolean matchBusRoutesToStreets;

  /** See {@link S3BucketConfig}. */
  public final S3BucketConfig elevationBucket;

  public final double elevationUnitMultiplier;

  /**
   * A specific fares service to use.
   */
  public final FareServiceFactory fareServiceFactory;

  private final Pattern netexLocalFilePattern;

  private final Pattern gtfsLocalFilePattern;

  private final Pattern osmLocalFilePattern;

  private final Pattern demLocalFilePattern;

  private final String gsCredentials;

  private final URI streetGraph;

  private final URI graph;

  private final URI buildReportDir;

  /**
   * A custom OSM namer to use.
   */
  public final CustomNamer customNamer;

  public final boolean osmCacheDataInMem;
  public final int pruningThresholdIslandWithoutStops;
  public final int pruningThresholdIslandWithStops;
  public final boolean banDiscouragedWalking;
  public final boolean banDiscouragedBiking;
  public final double maxTransferDurationSeconds;
  public final Boolean extraEdgesStopPlatformLink;
  public final NetexFeedParameters netexDefaults;

  public final OsmDefaultParameters osmDefaults;

  public final List<RegularRouteRequest> transferRequests;

  public final int maxAreaNodes;

  public final DataOverlayConfig dataOverlay;
  public final double maxStopToShapeSnapDistance;
  public final Set<String> boardingLocationTags;
  public final DemExtractParametersList dem;
  public final OsmExtractParametersList osm;
  public final TransitFeeds transitFeeds;
  public boolean staticParkAndRide;
  public boolean staticBikeParkAndRide;
  public int maxInterlineDistance;
  public double distanceBetweenElevationSamples;
  public double maxElevationPropagationMeters;
  public boolean readCachedElevations;
  public boolean writeCachedElevations;

  public boolean includeEllipsoidToGeoidDifference;

  public boolean multiThreadElevationCalculations;

  public LocalDate transitServiceStart;

  public LocalDate transitServiceEnd;
  public boolean discardMinTransferTimes;
  public ZoneId transitModelTimeZone;
  public boolean blockBasedInterlining;

  /**
   * Set all parameters from the given Jackson JSON tree, applying defaults. Supplying
   * MissingNode.getInstance() will cause all the defaults to be applied. This could be done
   * automatically with the "reflective query scraper" but it's less type safe and less clear. Until
   * that class is more type safe, it seems simpler to just list out the parameters by name here.
   */
  public BuildConfig(JsonNode node, String source, boolean logUnusedParams) {
    this(new NodeAdapter(node, source), logUnusedParams);
  }

  /**
   * @see #BuildConfig(JsonNode, String, boolean)
   */
  public BuildConfig(NodeAdapter root, boolean logUnusedParams) {
    this.root = root;
    // Keep this list of BASIC parameters sorted alphabetically on config PARAMETER name
    areaVisibility =
      root
        .of("areaVisibility")
        .since(V1_5)
        .summary("Perform visibility calculations.")
        .description(
          """
            If this is `true` OTP attempts to calculate a path straight through an OSM area using the 
            shortest way rather than around the edge of it. (These calculations can be time consuming).
            """
        )
        .asBoolean(false);
    banDiscouragedWalking =
      root
        .of("banDiscouragedWalking")
        .since(V2_0)
        .summary("Should walking be allowed on OSM ways tagged with `foot=discouraged`")
        .asBoolean(false);
    banDiscouragedBiking =
      root
        .of("banDiscouragedBiking")
        .since(V2_0)
        .summary("Should biking be allowed on OSM ways tagged with `bicycle=discouraged`")
        .asBoolean(false);
    configVersion =
      root
        .of("configVersion")
        .since(V2_1)
        .summary("Deployment version of the *build-config.json*.")
        .description(OtpConfig.CONFIG_VERSION_DESCRIPTION)
        .asString(null);
    dataImportReport =
      root
        .of("dataImportReport")
        .since(V2_0)
        .summary("Generate nice HTML report of Graph errors/warnings")
        .description("The reports are stored in the same location as the graph.")
        .asBoolean(false);
    distanceBetweenElevationSamples =
      root
        .of("distanceBetweenElevationSamples")
        .since(V2_0)
        .summary("The distance between elevation samples in meters.")
        .description(
          "The default is the approximate resolution of 1/3 arc-second NED data. This should not " +
          "be smaller than the horizontal resolution of the height data used."
        )
        .asDouble(CompactElevationProfile.DEFAULT_DISTANCE_BETWEEN_SAMPLES_METERS);
    elevationBucket = S3BucketConfig.fromConfig(root, "elevationBucket");
    elevationUnitMultiplier =
      root
        .of("elevationUnitMultiplier")
        .since(V2_0)
        .summary("Specify a multiplier to convert elevation units from source to meters.")
        .description(
          """
            Unit conversion multiplier for elevation values. No conversion needed if the elevation
            values are defined in meters in the source data. If, for example, decimetres are used
            in the source data, this should be set to 0.1.
            """
        )
        .asDouble(1);
    embedRouterConfig =
      root
        .of("embedRouterConfig")
        .since(V2_0)
        .summary(
          "Embed the Router config in the graph, which allows it to be sent to a server fully " +
          "configured over the wire."
        )
        .asBoolean(true);
    extraEdgesStopPlatformLink =
      root
        .of("extraEdgesStopPlatformLink")
        .since(V2_0)
        .summary(
          "Add extra edges when linking a stop to a platform, to prevent detours along the " +
          "platform edge."
        )
        .asBoolean(false);
    includeEllipsoidToGeoidDifference =
      root
        .of("includeEllipsoidToGeoidDifference")
        .since(V2_0)
        .summary(
          "Include the Ellipsoid to Geoid difference in the calculations of every point along " +
          "every StreetWithElevationEdge."
        )
        .description(
          """
When set to true (it is false by default), the elevation module will include the Ellipsoid to
Geoid difference in the calculations of every point along every StreetWithElevationEdge in the
graph.
  
NOTE: if this is set to true for graph building, make sure to not set the value of
`RoutingResource#geoidElevation` to true otherwise OTP will add this geoid value again to
all of the elevation values in the street edges.
"""
        )
        .asBoolean(false);
    pruningThresholdIslandWithStops =
      root
        .of("islandWithStopsMaxSize")
        .since(V2_1)
        .summary("When a graph island with stops in it should be pruned.")
        .description(
          """
        This field indicates the pruning threshold for islands with stops. Any such island under this
        size will be pruned.
        """
        )
        .asInt(5);
    pruningThresholdIslandWithoutStops =
      root
        .of("islandWithoutStopsMaxSize")
        .since(V2_1)
        .summary("When a graph island without stops should be pruned.")
        .description(
          """
        This field indicates the pruning threshold for islands without stops. Any such island under
        this size will be pruned.
        """
        )
        .asInt(40);
    matchBusRoutesToStreets =
      root
        .of("matchBusRoutesToStreets")
        .since(V1_5)
        .summary(
          "Based on GTFS shape data, guess which OSM streets each bus runs on to improve stop linking."
        )
        .asBoolean(false);
    maxDataImportIssuesPerFile =
      root
        .of("maxDataImportIssuesPerFile")
        .since(V2_0)
        .summary("When to split the import report.")
        .description(
          """
              If the number of issues is larger then `maxDataImportIssuesPerFile`, then the files will
              be split in multiple files. Since browsers have problems opening large HTML files.
            """
        )
        .asInt(1000);
    maxInterlineDistance =
      root
        .of("maxInterlineDistance")
        .since(V1_5)
        .summary(
          "Maximal distance between stops in meters that will connect consecutive trips that are made with same vehicle."
        )
        .asInt(200);
    blockBasedInterlining =
      root
        .of("blockBasedInterlining")
        .since(V2_2)
        .summary(
          "Whether to create stay-seated transfers in between two trips with the same block id."
        )
        .asBoolean(true);
    maxTransferDurationSeconds =
      root
        .of("maxTransferDurationSeconds")
        .since(V2_1)
        .summary(
          "Transfers up to this duration with the default walk speed value will be pre-calculated and included in the Graph."
        )
        .asDouble((double) Duration.ofMinutes(30).toSeconds());
    maxStopToShapeSnapDistance =
      root
        .of("maxStopToShapeSnapDistance")
        .since(V2_1)
        .summary("Maximum distance between route shapes and their stops.")
        .description(
          """
        This field is used for mapping routes geometry shapes. It determines max distance between shape
        points and their stop sequence. If mapper cannot find any stops within this radius it will
        default to simple stop-to-stop geometry instead.
        """
        )
        .asDouble(150);
    multiThreadElevationCalculations =
      root
        .of("multiThreadElevationCalculations")
        .since(V2_0)
        .summary("Configuring multi-threading during elevation calculations.")
        .description(
          """
          For unknown reasons that seem to depend on data and machine settings, it might be faster
          to use a single processor. If multi-threading is activated, parallel streams will be used
          to calculate the elevations.
        """
        )
        .asBoolean(false);
    osmCacheDataInMem =
      root
        .of("osmCacheDataInMem")
        .since(V2_0)
        .summary("If OSM data should be cached in memory during processing.")
        .description(
          """
      When loading OSM data, the input is streamed 3 times - one phase for processing RELATIONS, one
      for WAYS and last one for NODES. Instead of reading the data source 3 times it might be faster
      to cache the entire osm file im memory. The trade off is of course that OTP might use more
      memory while loading osm data. You can use this parameter to choose what is best for your
      deployment depending on your infrastructure. Set the parameter to `true` to cache the
      data, and to `false` to read the stream from the source each time.
      """
        )
        .asBoolean(false);
    platformEntriesLinking =
      root
        .of("platformEntriesLinking")
        .since(V2_0)
        .summary("Link unconnected entries to public transport platforms.")
        .asBoolean(false);
    readCachedElevations =
      root
        .of("readCachedElevations")
        .since(V2_0)
        .summary("Whether to read cached elevation data.")
        .description(
          """
        When set to true, the elevation module will attempt to read this file in
        order to reuse calculations of elevation data for various coordinate sequences instead of
        recalculating them all over again.
        """
        )
        .asBoolean(true);
    staticBikeParkAndRide =
      root
        .of("staticBikeParkAndRide")
        .since(V1_5)
        .summary("Whether we should create bike P+R stations from OSM data.")
        .asBoolean(false);
    staticParkAndRide =
      root
        .of("staticParkAndRide")
        .since(V1_5)
        .summary("Whether we should create car P+R stations from OSM data.")
        .asBoolean(true);
    subwayAccessTime =
      root
        .of("subwayAccessTime")
        .since(V1_5)
        .summary(
          "Minutes necessary to reach stops served by trips on routes of route_type=1 (subway) from the street."
        )
        .description(
          """
Note! The preferred way to do this is to update the OSM data.
See [Transferring within stations](#transferring-within-stations).

The ride locations for some modes of transport such as subways can be slow to reach from the street.
When planning a trip, we need to allow additional time to reach these locations to properly inform
the passenger. For example, this helps avoid suggesting short bus rides between two subway rides as
a way to improve travel time. You can specify how long it takes to reach a subway platform.

This setting does not generalize to other modes like airplanes because you often need much longer time
to check in to a flight (2-3 hours for international flights) than to alight and exit the airport
(perhaps 1 hour). Use [`boardSlackForMode`](RouteRequest.md#rd_boardSlackForMode) and
[`alightSlackForMode`](RouteRequest.md#rd_alightSlackForMode) for this.
"""
        )
        .asDouble(DEFAULT_SUBWAY_ACCESS_TIME_MINUTES);

    // Time Zone dependent config
    {
      // We need a time zone for setting transit service start and end. Getting the wrong time-zone
      // will just shift the period with one day, so the consequences is limited.
      transitModelTimeZone =
        root
          .of("transitModelTimeZone")
          .since(V2_2)
          .summary("Time zone for the graph.")
          .description(
            "This is used to store the timetables in the transit model, and to interpret times in incoming requests."
          )
          .asZoneId(null);
      var confZone = ObjectUtils.ifNotNull(transitModelTimeZone, ZoneId.systemDefault());
      transitServiceStart =
        root
          .of("transitServiceStart")
          .since(V2_0)
          .summary("Limit the import of transit services to the given START date.")
          .description(
            """
See [Limit the transit service period](#limit-transit-service-period) for an introduction.

The date is inclusive. If set, any transit service on a day BEFORE the given date is dropped and
will not be part of the graph. Use an absolute date or a period relative to the date the graph is
build(BUILD_DAY).

Use an empty string to make unbounded.
"""
          )
          .asDateOrRelativePeriod("-P1Y", confZone);
      transitServiceEnd =
        root
          .of("transitServiceEnd")
          .since(V2_0)
          .summary("Limit the import of transit services to the given end date.")
          .description(
            """
See [Limit the transit service period](#limit-transit-service-period) for an introduction.

The date is inclusive. If set, any transit service on a day AFTER the given date is dropped and
will not be part of the graph. Use an absolute date or a period relative to the date the graph is
build(BUILD_DAY).

Use an empty string to make it unbounded.
"""
          )
          .asDateOrRelativePeriod("P3Y", confZone);
    }

    writeCachedElevations =
      root
        .of("writeCachedElevations")
        .since(V2_0)
        .summary("Reusing elevation data from previous builds")
        .description(
          """
When set to true, the elevation module will create a file cache for calculated elevation data.
Subsequent graph builds can reuse the data in this file.
  
After building the graph, a file called `cached_elevations.obj` will be written to the cache
directory. By default, this file is not written during graph builds. There is also a graph build
parameter called `readCachedElevations` which is set to `true` by default.

In graph builds, the elevation module will attempt to read the `cached_elevations.obj` file from
the cache directory. The cache directory defaults to `/var/otp/cache`, but this can be overridden
via the CLI argument `--cache <directory>`. For the same graph build for multiple Northeast US
states, the time it took with using this pre-downloaded and precalculated data became roughly 9
minutes.

The cached data is a lookup table where the coordinate sequences of respective street edges are
used as keys for calculated data. It is assumed that all of the other input data except for the
OpenStreetMap data remains the same between graph builds. Therefore, if the underlying elevation
data is changed, or different configuration values for `elevationUnitMultiplier` or
`includeEllipsoidToGeoidDifference` are used, then this data becomes invalid and all elevation data
should be recalculated. Over time, various edits to OpenStreetMap will cause this cached data to
become stale and not include new OSM ways. Therefore, periodic update of this cached data is
recommended.
"""
        )
        .asBoolean(false);
    maxAreaNodes =
      root
        .of("maxAreaNodes")
        .since(V2_1)
        .summary(
          "Visibility calculations for an area will not be done if there are more nodes than this limit."
        )
        .asInt(500);
    maxElevationPropagationMeters =
      root
        .of("maxElevationPropagationMeters")
        .since(V1_5)
        .summary("The maximum distance to propagate elevation to vertices which have no elevation.")
        .asInt(2000);
    boardingLocationTags =
      root
        .of("boardingLocationTags")
        .since(V2_2)
        .summary(
          "What OSM tags should be looked on for the source of matching stops to platforms and stops."
        )
        .description("[Detailed documentation](BoardingLocations.md)")
        .asStringSet(List.copyOf(Set.of("ref")));
    discardMinTransferTimes =
      root
        .of("discardMinTransferTimes")
        .since(V2_2)
        .summary("Should minimum transfer times in GTFS files be discarded.")
        .description(
          """
          This is useful eg. when the minimum transfer time is only set for ticketing purposes,
          but we want to calculate the transfers always from OSM data.
          """
        )
        .asBoolean(false);

    var localFileNamePatternsConfig = root
      .of("localFileNamePatterns")
      .since(V2_0)
      .summary("Patterns for matching OTP file types in the base directory")
      .description(
        """
When scanning the base directory for inputs, each file's name is checked against patterns to
detect what kind of file it is.

OTP1 used to peek inside ZIP files and read the CSV tables to guess if a ZIP was indeed GTFS. Now
that we support remote input files (cloud storage or arbitrary URLs) not all data sources allow
seeking within files to guess what they are. Therefore, like all other file types GTFS is now
detected from a filename pattern. It is not sufficient to look for the `.zip` extension because
Netex data is also often supplied in a ZIP file.        
"""
      )
      .asObject();
    gtfsLocalFilePattern =
      localFileNamePatternsConfig
        .of("gtfs")
        .since(V2_0)
        .summary("Patterns for matching GTFS zip-files or directories.")
        .description(
          """
            If the filename contains the given pattern it is considered a match. 
            Any legal Java Regular expression is allowed.
            """
        )
        .asPattern(DEFAULT_GTFS_PATTERN);
    netexLocalFilePattern =
      localFileNamePatternsConfig
        .of("netex")
        .since(V2_0)
        .summary("Patterns for matching NeTEx zip files or directories.")
        .description(
          """
            If the filename contains the given
            pattern it is considered a match. Any legal Java Regular expression is allowed.
            """
        )
        .asPattern(DEFAULT_NETEX_PATTERN);
    osmLocalFilePattern =
      localFileNamePatternsConfig
        .of("osm")
        .since(V2_0)
        .summary("Pattern for matching Open Street Map input files.")
        .description(
          """
            If the filename contains the given pattern
            it is considered a match. Any legal Java Regular expression is allowed.
            """
        )
        .asPattern(DEFAULT_OSM_PATTERN);
    demLocalFilePattern =
      localFileNamePatternsConfig
        .of("dem")
        .since(V2_0)
        .summary("Pattern for matching elevation DEM files.")
        .description(
          """
            If the filename contains the given pattern it is
            considered a match. Any legal Java Regular expression is allowed.
            """
        )
        .asPattern(DEFAULT_DEM_PATTERN);

    gsCredentials =
      root
        .of("gsCredentials")
        .since(V2_0)
        .summary(
          "Local file system path to Google Cloud Platform service accounts credentials file."
        )
        .description(
          """
            The credentials is used to access GCS urls. When using GCS from outside of Google Cloud you
            need to provide a path the the service credentials. Environment variables in the path are
            resolved.
                  
            This is a path to a file on the local file system, not an URI.
            """
        )
        .asString(null);
    graph =
      root
        .of("graph")
        .since(V2_0)
        .summary("URI to the graph object file for reading and writing.")
        .description("The file is created or overwritten if OTP saves the graph to the file.")
        .asUri(null);
    streetGraph =
      root
        .of("streetGraph")
        .since(V2_0)
        .summary("URI to the street graph object file for reading and writing.")
        .description("The file is created or overwritten if OTP saves the graph to the file")
        .asUri(null);
    buildReportDir =
      root
        .of("buildReportDir")
        .since(V2_0)
        .summary("URI to the directory where the graph build report should be written to.")
        .description(
          """
      The html report is written into this directory. If the directory exist, any existing files are deleted. 
      If it does not exist, it is created.
      """
        )
        .asUri(null);

    osmDefaults = OsmConfig.mapOsmDefaults(root, "osmDefaults");
    osm = OsmConfig.mapOsmConfig(root, "osm");
    dem = DemConfig.mapDemConfig(root, "dem");

    netexDefaults = NetexConfig.mapNetexDefaultParameters(root, "netexDefaults");
    transitFeeds = TransitFeedConfig.mapTransitFeeds(root, "transitFeeds", netexDefaults);

    // List of complex parameters
    fareServiceFactory = FaresConfiguration.fromConfig(root, "fares");
    customNamer = CustomNamer.CustomNamerFactory.fromConfig(root, "osmNaming");
    dataOverlay = DataOverlayConfigMapper.map(root, "dataOverlay");

    transferRequests = TransferRequestConfig.map(root, "transferRequests");

    if (logUnusedParams && LOG.isWarnEnabled()) {
      root.logAllUnusedParameters(LOG::warn);
    }
  }

  @Override
  public URI reportDirectory() {
    return buildReportDir;
  }

  @Override
  public String gsCredentials() {
    return gsCredentials;
  }

  @Override
  public List<URI> osmFiles() {
    return osm.osmFiles();
  }

  @Override
  public List<URI> demFiles() {
    return dem.demFiles();
  }

  @Nonnull
  @Override
  public List<URI> gtfsFiles() {
    return transitFeeds.gtfsFiles();
  }

  @Nonnull
  @Override
  public List<URI> netexFiles() {
    return transitFeeds.netexFiles();
  }

  @Override
  public URI graph() {
    return graph;
  }

  @Override
  public URI streetGraph() {
    return streetGraph;
  }

  @Override
  public Pattern gtfsLocalFilePattern() {
    return gtfsLocalFilePattern;
  }

  @Override
  public Pattern netexLocalFilePattern() {
    return netexLocalFilePattern;
  }

  @Override
  public Pattern osmLocalFilePattern() {
    return osmLocalFilePattern;
  }

  @Override
  public Pattern demLocalFilePattern() {
    return demLocalFilePattern;
  }

  /**
   * If {@code true} the config is loaded from file, in not the DEFAULT config is used.
   */
  public boolean isDefault() {
    return root.isEmpty();
  }

  public String toJson() {
    return root.isEmpty() ? "" : root.toJson();
  }

  public ServiceDateInterval getTransitServicePeriod() {
    return new ServiceDateInterval(transitServiceStart, transitServiceEnd);
  }

  public int getSubwayAccessTimeSeconds() {
    // Convert access time in minutes to seconds
    return (int) (subwayAccessTime * 60.0);
  }

  public NodeAdapter asNodeAdapter() {
    return root;
  }
}
