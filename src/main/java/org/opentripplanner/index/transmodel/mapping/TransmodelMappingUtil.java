package org.opentripplanner.index.transmodel.mapping;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.index.transmodel.model.TransmodelPlaceType;
import org.opentripplanner.model.AgencyAndId;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.calendar.ServiceDate;
import org.opentripplanner.routing.graph.GraphIndex;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * Utility methods for mapping transmddel API values to and from internal formats.
 */
public class TransmodelMappingUtil {

    private static final String LIST_VALUE_SEPARATOR = ",";

    private static final String GTFS_LIBRARY_ID_SEPARATOR = ":";

    private String fixedAgencyId;

    private TimeZone timeZone;

    public TransmodelMappingUtil(String fixedAgencyId, TimeZone timeZone) {
        this.fixedAgencyId = fixedAgencyId;
        this.timeZone = timeZone;
    }

    public String toIdString(AgencyAndId agencyAndId) {
        if (agencyAndId == null) {
            return null;
        }
        if (fixedAgencyId != null) {
            return agencyAndId.getId();
        }
        return GtfsLibrary.convertIdToString(agencyAndId);
    }

    /**
     * Create a new {@link AgencyAndId} for the given String input {@code id}. If
     * the {@code id} is {@code null}, an empty string or blank string then {@code null} is returned.
     */
    public AgencyAndId fromIdStringOrBlank(@Nullable String id) {
        return (id == null || id.isBlank()) ? null : fromIdString(id);
    }

    public AgencyAndId fromIdString(String id) {
        if (fixedAgencyId != null) {
            return new AgencyAndId(fixedAgencyId, id);
        }
        return GtfsLibrary.convertIdFromString(id);
    }

    public List<AgencyAndId> idsFromStrings(Collection<String> ids) {
        return ids == null ? null : ids.stream().map(this::fromIdString).collect(Collectors.toList());
    }

    /**
     * Add agency id prefix to vertexIds if fixed agency is set.
     */
    public String preparePlaceRef(String input) {
        if (fixedAgencyId != null && input != null) {
            GenericLocation location = GenericLocation.fromOldStyleString(input);

            if (location.hasVertexId()) {
                String prefixedPlace = prepareAgencyAndId(location.place, GTFS_LIBRARY_ID_SEPARATOR);
                return new GenericLocation(location.name, prefixedPlace).toString();
            }

        }
        return input;
    }

    public String prepareListOfAgencyAndId(List<String> ids) {
        return mapCollectionOfValues(ids, this::prepareAgencyAndId);
    }

    public String prepareListOfAgencyAndId(List<String> ids, String separator) {
        return mapCollectionOfValues(ids, value -> prepareAgencyAndId(value, separator));
    }

    public String prepareAgencyAndId(String id) {
        return prepareAgencyAndId(id, null);
    }

    public String prepareAgencyAndId(String id, String separator) {
        if (fixedAgencyId != null && id != null) {
            return separator == null
                           ? AgencyAndId.concatenateId(fixedAgencyId, id)
                           : fixedAgencyId + separator + id;
        }
        return id;
    }

    public String mapCollectionOfValues(Collection<String> values, Function<String, String> mapElementFunction) {
        if (values == null) {
            return null;
        }
        List<String> otpModelModes = values.stream().map(value -> mapElementFunction.apply(value)).collect(Collectors.toList());

        return Joiner.on(LIST_VALUE_SEPARATOR).join(otpModelModes);
    }

    public Object mapVehicleTypeToTraverseMode(int vehicleType) {
        try {
            return GtfsLibrary.getTraverseMode(vehicleType);
        } catch (IllegalArgumentException iae) {
            return "unknown";
        }
    }


    public Long serviceDateToSecondsSinceEpoch(ServiceDate serviceDate) {
        if (serviceDate == null) {
            return null;
        }

        return LocalDate.of(serviceDate.getYear(), serviceDate.getMonth(), serviceDate.getDay())
                .atStartOfDay(timeZone.toZoneId()).toEpochSecond();
    }

    public ServiceDate secondsSinceEpochToServiceDate(Long secondsSinceEpoch) {
        if (secondsSinceEpoch == null) {
            return new ServiceDate();
        }
        return new ServiceDate(new Date(secondsSinceEpoch * 1000));
    }


    public List<GraphIndex.PlaceType> mapPlaceTypes(List<TransmodelPlaceType> inputTypes) {
        if (inputTypes == null) {
            return null;
        }

        return inputTypes.stream().map(pt -> mapPlaceType(pt)).distinct().collect(Collectors.toList());
    }

    private GraphIndex.PlaceType mapPlaceType(TransmodelPlaceType transmodelType){
        if (transmodelType!=null) {
            switch (transmodelType) {
                case QUAY:
                case STOP_PLACE:
                    return GraphIndex.PlaceType.STOP;
                case BICYCLE_RENT:
                    return GraphIndex.PlaceType.BICYCLE_RENT;
                case BIKE_PARK:
                    return GraphIndex.PlaceType.BIKE_PARK;
                case CAR_PARK:
                    return GraphIndex.PlaceType.CAR_PARK;
            }
        }
        return null;
    }
}
