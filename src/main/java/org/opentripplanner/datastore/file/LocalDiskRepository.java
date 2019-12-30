package org.opentripplanner.datastore.file;


import org.opentripplanner.datastore.CatalogDataSource;
import org.opentripplanner.datastore.DataSource;
import org.opentripplanner.datastore.FileType;
import org.opentripplanner.datastore.base.LocalDataSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.datastore.FileType.CONFIG;
import static org.opentripplanner.datastore.FileType.DEM;
import static org.opentripplanner.datastore.FileType.GRAPH;
import static org.opentripplanner.datastore.FileType.GTFS;
import static org.opentripplanner.datastore.FileType.NETEX;
import static org.opentripplanner.datastore.FileType.OSM;
import static org.opentripplanner.datastore.FileType.OTP_STATUS;
import static org.opentripplanner.datastore.FileType.REPORT;
import static org.opentripplanner.datastore.FileType.UNKNOWN;
import static org.opentripplanner.datastore.OtpDataStore.BUILD_REPORT_DIR;
import static org.opentripplanner.datastore.base.LocalDataSourceRepository.isCurrentDir;
import static org.opentripplanner.standalone.config.ConfigLoader.isConfigFile;


/**
 * This data store uses the local file system to access in-/out- data files.
 */
public class LocalDiskRepository implements LocalDataSourceRepository {
    private static final Logger LOG = LoggerFactory.getLogger(LocalDiskRepository.class);

    private final File baseDir;

    public LocalDiskRepository(File baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Use for unit testing
     */
    @NotNull
    public static CatalogDataSource catalogSource(File file, FileType type) {
        // The cast is safe
        return createCatalogSource(file, type);
    }

    @Override
    public String description() {
        return baseDir.getPath();
    }

    @Override
    public void open() { /* Nothing to do */ }

    @Override
    public DataSource findSource(URI uri, FileType type) {
        return new FileDataSource(new File(uri), type);
    }

    @Override
    public DataSource findSource(String filename, FileType type) {
        return new FileDataSource(new File(baseDir, filename), type);
    }

    @Override
    public CatalogDataSource findCatalogSource(URI uri, FileType type) {
        return createCatalogSource(new File(uri), type);
    }

    @Override
    public CatalogDataSource findCatalogSource(String localFilename, FileType type) {
        // If the local file name is '.' then use the 'baseDir', if not create a new file directory.
        File file = isCurrentDir(localFilename) ? baseDir : new File(baseDir, localFilename);
        return createCatalogSource(file, type);
    }

    @Override
    public List<DataSource> listExistingSources(FileType type) {
        // Return ALL resources of the given type, this is
        // auto-detecting matching files on the local file system
        List<DataSource> existingFiles = new ArrayList<>();
        File[] files = baseDir.listFiles();

        if (files == null) {
            LOG.error("'{}' is not a readable input directory.", baseDir);
            return existingFiles;
        }

        for (File file : files) {
            if(type == resolveFileType(file)) {
                if (isCatalogDataSource(file)) {
                    existingFiles.add(createCatalogSource(file, type));
                }
                else {
                    existingFiles.add(new FileDataSource(file, type));
                }
            }
        }
        return existingFiles;
    }

    @Override
    public String toString() {
        return "FileDataSourceRepository{" + "baseDir=" + baseDir + '}';
    }

    /* private methods */

    private boolean isCatalogDataSource(File file) {
       return file.isDirectory() || file.getName().endsWith(".zip");
    }

    private static CatalogDataSource createCatalogSource(File file, FileType type) {
        if (file.exists() && file.isDirectory()) {
            return new DirectoryDataSource(file, type);
        }
        if (file.getName().endsWith(".zip")) {
            return new ZipFileDataSource(file, type);
        }
        // If writing to a none-existing directory
        if (!file.exists() && type.isOutputDataSource()) {
            return new DirectoryDataSource(file, type);
        }
        throw new IllegalArgumentException("The " + file + " is not recognized as a zip-file or "
                + "directory. Unable to create catalog data source for file type " + type + ".");
    }



    private static FileType resolveFileType(File file) {
        String name = file.getName();
        if (isTransitFile(file, "gtfs")) { return GTFS; }
        if (isTransitFile(file, "netex")) { return NETEX; }
        if (name.endsWith(".pbf")) { return OSM; }
        if (name.endsWith(".osm")) { return OSM; }
        if (name.endsWith(".osm.xml")) { return OSM; }
        // Digital elevation model (elevation raster)
        if (name.endsWith(".tif") || name.endsWith(".tiff")) { return DEM; }
        if (name.matches("(streetG|g)raph.obj")) { return GRAPH; }
        if (name.matches("otp-status.(inProgress|ok|failed)")) { return OTP_STATUS; }
        if (name.equals(BUILD_REPORT_DIR)) { return REPORT; }
        if (isConfigFile(name)) { return CONFIG;}
        return UNKNOWN;
    }

    private static boolean isTransitFile(File file, String subName) {
        return file.getName().toLowerCase().contains(subName)
                && (file.isDirectory() || file.getName().endsWith(".zip"));
    }
}
