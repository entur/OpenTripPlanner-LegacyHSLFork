package org.opentripplanner.datastore;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.opentripplanner.datastore.base.DataSourceRepository;
import org.opentripplanner.datastore.base.LocalDataSourceRepository;
import org.opentripplanner.datastore.configure.OtpDataStoreFactory;
import org.opentripplanner.util.OtpAppException;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.opentripplanner.datastore.FileType.CONFIG;
import static org.opentripplanner.datastore.FileType.DEM;
import static org.opentripplanner.datastore.FileType.GRAPH;
import static org.opentripplanner.datastore.FileType.GTFS;
import static org.opentripplanner.datastore.FileType.NETEX;
import static org.opentripplanner.datastore.FileType.OSM;
import static org.opentripplanner.datastore.FileType.REPORT;
import static org.opentripplanner.datastore.FileType.UNKNOWN;

/**
 * The responsibility of this class is to provide access to all data sources OTP uses like the
 * graph, including OSM data and transit data. The default is to use the the local disk, but other
 * "providers/repositories" can be implemented to access files elsewhere like in the cloud.
 * <p>
 * This class provide an abstraction layer for accessing OTP data input and output sources. In a
 * cloud ecosystem you might find it easier to access the data directly from the cloud storage,
 * rather than first copy the data into your node local disk, and then copy the build graph back
 * into cloud storage after building it. Depending on the source this might also offer enhanced
 * performance.
 * <p>
 * Use the {@link OtpDataStoreFactory} to obtain a new instance of this class.
 */
public class OtpDataStore {
    public static final String BUILD_REPORT_DIR = "report";
    private static final String STREET_GRAPH_FILENAME = "streetGraph.obj";
    private static final String GRAPH_FILENAME = "graph.obj";

    private final OtpDataStoreConfig config;
    private final List<String> repositoryDescriptions = new ArrayList<>();
    private final Map<String, DataSourceRepository> repositories;
    private final Multimap<FileType, DataSource> sources = ArrayListMultimap.create();

    /* Named resources available for both reading and writing. */
    private DataSource streetGraph;
    private DataSource graph;
    private CatalogDataSource buildReportDir;

    /**
     * Use the {@link OtpDataStoreFactory} to
     * create a new instance of this class.
     */
    public OtpDataStore(
            OtpDataStoreConfig config,
            Map<String, DataSourceRepository> repositories
    ) {
        this.config = config;
        this.repositoryDescriptions.addAll(
                repositories.values().stream()
                        .map(DataSourceRepository::description)
                        .collect(Collectors.toList())
        );
        this.repositories = repositories;
    }

    public void open() {
        repositories.values().forEach(DataSourceRepository::open);
        addAll(getLocalFilesRepo().listExistingSources(CONFIG));
        addAll(findMultipleSources(config.osmFiles(), OSM));
        addAll(findMultipleSources(config.demFiles(),  DEM));
        addAll(findMultipleCatalogSources(config.gtfsFiles(), GTFS));
        addAll(findMultipleCatalogSources(config.netexFiles(), NETEX));

        streetGraph = findSingleSource(config.streetGraph(), STREET_GRAPH_FILENAME, GRAPH);
        graph = findSingleSource(config.graph(), GRAPH_FILENAME, GRAPH);
        buildReportDir = findCatalogSource(config.reportDirectory(), BUILD_REPORT_DIR, REPORT);

        addAll(Arrays.asList(streetGraph, graph, buildReportDir));

        // Also read in unknown sources in case the data input source is miss-spelled,
        // We look for files on the local-file-system, other repositories ignore this call.
        addAll(findMultipleSources(Collections.emptyList(), UNKNOWN));
    }

    /**
     * @return a description(path) for each datasource used/enabled.
     */
    public List<String> getRepositoryDescriptions() {
        return repositoryDescriptions;
    }

    /**
     * List all existing data sources by file type. An empty list is returned if there is no files
     * of the given type.
     *
     * @return The collection may contain elements of type {@link DataSource} or {@link
     * CatalogDataSource}.
     */
    @NotNull
    public Collection<DataSource> listExistingSourcesFor(FileType type) {
        return sources.get(type).stream().filter(DataSource::exists).collect(Collectors.toList());
    }

    @NotNull
    public DataSource getStreetGraph() {
        return streetGraph;
    }

    @NotNull
    public DataSource getGraph() {
        return graph;
    }

    @NotNull
    public CatalogDataSource getBuildReportDir() {
        return buildReportDir;
    }


    /* private methods */

    private void add(DataSource source) {
        if(source != null) {
            sources.put(source.type(), source);
        }
    }

    private void addAll(List<? extends DataSource> list) {
        list.forEach(this::add);
    }

    private LocalDataSourceRepository getLocalFilesRepo() {
        return (LocalDataSourceRepository) repositories.get("file");
    }

    private DataSource findSingleSource(@Nullable URI uri, @NotNull String filename, @NotNull FileType type) {
        if(uri != null) {
            return repo(uri).findSource(uri, type);
        }
        return getLocalFilesRepo().findSource(filename, type);
    }

    private CatalogDataSource findCatalogSource(@Nullable URI uri, @NotNull String filename, @NotNull FileType type) {
        if(uri != null) {
            return repo(uri).findCatalogSource(uri, type);
        }
        else {
            return getLocalFilesRepo().findCatalogSource(filename, type);
        }
    }

    private List<DataSource> findMultipleSources(@NotNull Collection<URI> uris, @NotNull FileType type) {
        if(uris == null || uris.isEmpty()) {
            return getLocalFilesRepo().listExistingSources(type);
        }
        List<DataSource> result = new ArrayList<>();
        for (URI uri : uris) {
            result.add(repo(uri).findSource(uri, type));
        }
        return result;
    }

    private List<CatalogDataSource> findMultipleCatalogSources(
            @NotNull Collection<URI> uris, @NotNull FileType type
    ) {
        if(uris.isEmpty()) {
            return getLocalFilesRepo().listExistingSources(type)
                    .stream()
                    .map(it -> (CatalogDataSource)it)
                    .collect(Collectors.toList());
        }
        List<CatalogDataSource> result = new ArrayList<>();
        for (URI uri : uris) {
            result.add(repo(uri).findCatalogSource(uri, type));
        }
        return result;
    }

    private DataSourceRepository repo(URI uri) {
        String scheme = uri.getScheme();
        DataSourceRepository repo = repositories.get(scheme);
        if(repo == null) {
            throw new OtpAppException("No datasource storage configured in the 'build-config.json' "
                    + "file for URI prefix: " + scheme);
        }
        return repo;
    }
}
