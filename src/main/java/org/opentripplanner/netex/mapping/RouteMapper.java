package org.opentripplanner.netex.mapping;

import org.opentripplanner.model.Agency;
import org.opentripplanner.model.impl.OtpTransitBuilder;
import org.rutebanken.netex.model.Line;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteMapper {

    private static final Logger LOG = LoggerFactory.getLogger(RouteMapper.class);

    TransportTypeMapper transportTypeMapper = new TransportTypeMapper();

    public org.opentripplanner.model.Route mapRoute(Line line, OtpTransitBuilder transitBuilder){

        org.opentripplanner.model.Route otpRoute = new org.opentripplanner.model.Route();

        if (line.getOperatorRef() == null) {
            LOG.warn("Line " + line.getId() + " does not have an operator.");
        } else {
            String agencyId = line.getOperatorRef().getRef();
            Agency agency = transitBuilder.getAgencies().stream().filter(a -> a.getId().equals(agencyId)).findFirst().get();
            otpRoute.setAgency(agency);
        }
        otpRoute.setId(AgencyAndIdFactory.createAgencyAndId(line.getId()));
        otpRoute.setLongName(line.getName().getValue());
        otpRoute.setShortName(line.getPublicCode());
        otpRoute.setType(transportTypeMapper.mapTransportType(line.getTransportMode().value()));

        return otpRoute;
    }
}