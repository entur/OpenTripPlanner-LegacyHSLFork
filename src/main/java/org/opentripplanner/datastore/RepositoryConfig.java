package org.opentripplanner.datastore;


/**
 * Each implementation of a data repository must provide a concrete implementation of this
 * interface. The config will be passed into the repository at startup time to initiate it.
 */
public interface RepositoryConfig {

    /**
     * Each repository instance should have a unique scheme (or namespace). This is used to resolve
     * which storage handles witch data source.
     * <p>
     * For example the default Google Storage scheme is "gs", any URI starting with "gs:" like
     * "gs://my-otp-bucket/a/b/c/gtfs.zip" would be handled by the Google Storage.
     */
    String uriScheme();
}
