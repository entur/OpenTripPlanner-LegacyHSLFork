package org.opentripplanner.datastore.configure;

import org.opentripplanner.datastore.CatalogDataSource;
import org.opentripplanner.datastore.FileType;
import org.opentripplanner.datastore.OtpDataStore;
import org.opentripplanner.datastore.OtpDataStoreConfig;
import org.opentripplanner.datastore.RepositoryConfig;
import org.opentripplanner.datastore.base.DataSourceRepository;
import org.opentripplanner.datastore.file.LocalDiskRepository;
import org.opentripplanner.ext.datastore.gs.GoogleRepositoryConfig;
import org.opentripplanner.ext.datastore.gs.GsDataSourceRepository;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * This is the global access point to create a repositories and create datasource objects(in tests).
 * <p>
 * Note that opening a repository should not download or open any data sources, only fetch meta-data
 * to figure out what data is available. A data source is accessed (lazy) using streams.
 */
public class OtpDataStoreFactory {
    private final OtpDataStoreConfig config;

    /**
     * @param config is used to create and configure the otp store.
     */
    public OtpDataStoreFactory(OtpDataStoreConfig config) {
        this.config = config;
    }

    /* static factory methods, mostly used by tests */

    /**
     * For test only.
     * <p>
     * Use this to get a catalog data source, bypassing the {@link OtpDataStore}.
     */
    public static CatalogDataSource localCatalogSource(File file, FileType type) {
        return LocalDiskRepository.catalogSource(file, type);
    }

    /**
     * Connect to data source and prepare to retrieve data.
     */
    public OtpDataStore open() {
        Map<String, DataSourceRepository> repositories = new HashMap<>();

        for (RepositoryConfig c : config.stores()) {
            repositories.put(c.uriScheme(), createRepository(c));
        }
        // Add default repository for the local file system
        repositories.put("file", new LocalDiskRepository(config.baseDirectory()));

        OtpDataStore store = new OtpDataStore(config, repositories);

        store.open();
        return store;
    }


    /**
     * This method creates a repository based on the store config type.
     */
    private DataSourceRepository createRepository(RepositoryConfig config) {
        if(config instanceof GoogleRepositoryConfig) {
            GoogleRepositoryConfig gsCfg = (GoogleRepositoryConfig) config;
            return new GsDataSourceRepository(gsCfg);
        }
        // Support for other repository implementation should be added here...

        throw new IllegalStateException("Unknown config type: " + config.getClass().getName());
    }
}
