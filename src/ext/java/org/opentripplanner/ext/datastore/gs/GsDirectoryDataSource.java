package org.opentripplanner.ext.datastore.gs;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import org.opentripplanner.datastore.CatalogDataSource;
import org.opentripplanner.datastore.DataSource;
import org.opentripplanner.datastore.FileType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;


/**
 * This is a an adapter to to simulate a file directory on a GCS. Files created using an instance of this
 * class wil have a common namespace. It does only support creating new output sources, it can not
 * be used to list files with the common namespace (directory path).
 */
public class GsDirectoryDataSource extends AbstractGsDataSource implements CatalogDataSource {

    private final Storage storage;

    GsDirectoryDataSource(Storage storage, ObjectId objectId, FileType type) {
        super(objectId, type);
        this.storage = storage;
    }

    @Override
    public boolean exists() {
        return getBucket().list(
                Storage.BlobListOption.prefix(name()),
                Storage.BlobListOption.pageSize(1),
                Storage.BlobListOption.currentDirectory()
        ).getValues().iterator().hasNext();
    }

    @Override
    public DataSource entry(String name) {
        Blob blob = childBlob(name);
        // If file exist
        if(blob != null) {
            ObjectId childId = new ObjectId(uriScheme(), blob.getBlobId());
            return new GsFileDataSource(blob, childId, type());
        }
        // New file
        BlobId childBlobId = BlobId.of(bucketName(), childPath(name));
        return new GsOutFileDataSource(storage, objectId(childBlobId), type());
    }

    @Override
    public Collection<DataSource> content() {
        Collection<DataSource> content = new ArrayList<>();
        forEachChildBlob(blob -> content.add(
                new GsFileDataSource(blob, objectId(blob.getBlobId()), type()))
        );
        return content;
    }

    @Override
    public void delete() {
        forEachChildBlob(Blob::delete);
    }

    @Override
    public void close() { }


    /* private methods */

    private Bucket getBucket() {
        Bucket bucket = storage.get(bucketName());
        if(bucket == null) {
            throw new IllegalArgumentException("Bucket not found: " + bucketName());
        }
        return bucket;
    }

    private ObjectId objectId(BlobId blobId) {
        return new ObjectId(uriScheme(), blobId);
    }

    private Blob childBlob(String name) {
        return getBucket().get(childPath(name));
    }

    private String childPrefix() {
        return isRoot() ? "" : name() + "/";
    }

    private String childPath(String name) {
        return childPrefix() + name;
    }

    private void forEachChildBlob(Consumer<Blob>  consumer) {
        int pathIndex = childPrefix().length();
        for (Blob blob : listBlobs().iterateAll()) {
            String name = blob.getName().substring(pathIndex);
            // Skip nested content
            if(!name.contains("/")) {
                consumer.accept(blob);
            }
        }
    }

    private Page<Blob> listBlobs() {
        return getBucket().list(Storage.BlobListOption.prefix(childPrefix()));
    }
}
