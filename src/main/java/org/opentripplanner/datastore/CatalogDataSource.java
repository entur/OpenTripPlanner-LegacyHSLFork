package org.opentripplanner.datastore;

import java.io.Closeable;
import java.util.Collection;

/**
 * A data source containing a collection of other {@link DataSource}s.
 * <p>
 * Example are file directories and zip files with GTFS or NeTEx data.
 */
public interface CatalogDataSource extends DataSource, Closeable {

    /**
     * Open the data source and read the content. The implementation should try to fetch
     * meta-data for the content, not the entire files. For a zip-file stored in the cloud
     * this might not be possible and the entire zip file might get downloaded when this
     * method is called.
     */
    Collection<DataSource> content();

    /**
     * Retrieve a single entry by name, or {@code null} if not found.
     * <p>
     * Example:
     * <p>
     * {@code DataSource routesSrc = gtfsSource.entry("routes.txt")}
     */
    DataSource entry(String name);

    /**
     * Delete content and container.
     */
    default void delete() {
        throw new UnsupportedOperationException(
                "This datasource type " + getClass().getSimpleName()
                + " do not support DELETE. Can not delete: " + path()
        );
    }
}
