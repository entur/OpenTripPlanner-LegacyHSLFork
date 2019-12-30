package org.opentripplanner.ext.datastore.gs;


import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.opentripplanner.datastore.CatalogDataSource;
import org.opentripplanner.datastore.DataSource;
import org.opentripplanner.datastore.FileType;
import org.opentripplanner.datastore.base.DataSourceRepository;
import org.opentripplanner.datastore.base.ZipStreamDataSourceDecorator;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;

/**
 * This data store uses the local file system to access in-/out- data files.
 */
public class GsDataSourceRepository implements DataSourceRepository {
    private final GoogleRepositoryConfig config;
    private Storage storage;

    public GsDataSourceRepository(GoogleRepositoryConfig config) {
        this.config = config;
    }

    @Override
    public void open() {
        this.storage = connectToStorage();
    }

    @Override
    public String description() {
        return "Google Cloud Storage";
    }

    @Override
    public DataSource findSource(URI uri, FileType type) {
        return createSource(ObjectId.toObjectId(uri), type);
    }

    @Override
    public CatalogDataSource findCatalogSource(URI uri, FileType type) {
        return createCatalogSource(ObjectId.toObjectId(uri), type);
    }

    /* private methods */

    private DataSource createSource(ObjectId objectId, FileType type) {
        Blob blob = storage.get(objectId.blobId());

        if(blob != null) {
            return new GsFileDataSource(blob, objectId, type);
        }
        else {
            return new GsOutFileDataSource(storage, objectId, type);
        }
    }

    private CatalogDataSource createCatalogSource(ObjectId objectId, FileType type) {
        if(objectId.isRoot()) {
            return new GsDirectoryDataSource(storage, objectId, type);
        }

        if(objectId.isZipFile()) {
            Blob blob = storage.get(objectId.blobId());

            if(blob == null) {
                throw new IllegalArgumentException(type.text() +  " not found: " + objectId);
            }
            DataSource gsSource = new GsFileDataSource(blob, objectId, type);
            return new ZipStreamDataSourceDecorator(gsSource);
        }
        return new GsDirectoryDataSource(storage, objectId, type);
    }

    private Storage connectToStorage() {
        try {
            StorageOptions.Builder builder = StorageOptions.getDefaultInstance().toBuilder();

            if(config.credentialsFilename() != null) {
                GoogleCredentials credentials = GoogleCredentials
                        .fromStream(new FileInputStream(config.credentialsFilename()))
                        .createScoped(Collections.singletonList(
                                "https://www.googleapis.com/auth/cloud-platform"
                        ));
                builder.setCredentials(credentials);
            }
            return builder.build().getService();
        }
        catch (IOException e) {
            throw new RuntimeException(e.getLocalizedMessage(), e);
        }
    }
}
