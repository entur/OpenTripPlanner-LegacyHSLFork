package org.opentripplanner.ext.datastore.gs;

import com.google.cloud.storage.BlobId;
import org.opentripplanner.datastore.DataSource;
import org.opentripplanner.datastore.FileType;

abstract class AbstractGsDataSource implements DataSource {
    private final ObjectId objectId;
    private final FileType type;

    AbstractGsDataSource(ObjectId objectId, FileType type) {
        this.objectId = objectId;
        this.type = type;
    }

    BlobId blobId() {
        return objectId.blobId();
    }

    String bucketName() {
        return objectId.blobId().getBucket();
    }

    String uriScheme() {
        return objectId.uriScheme();
    }

    boolean isRoot() {
        return objectId.isRoot();
    }



    @Override
    public final String name() {
        return objectId.blobId().getName();
    }

    @Override
    public final String path() {
        return objectId.toUriString();
    }

    @Override
    public final FileType type() {
        return type;
    }

    @Override
    public final String toString() {
        return type + " " + path();
    }
}
