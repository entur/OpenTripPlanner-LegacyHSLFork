package org.opentripplanner.routing.algorithm.raptor.transit.mappers;

import org.opentripplanner.routing.algorithm.raptor.transit.SlackProvider;
import org.opentripplanner.routing.algorithm.raptor.transit.TripSchedule;
import org.opentripplanner.routing.api.request.RoutingRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.transit.raptor.api.request.Optimization;
import org.opentripplanner.transit.raptor.api.request.RaptorProfile;
import org.opentripplanner.transit.raptor.api.request.RaptorRequest;
import org.opentripplanner.transit.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.transit.raptor.api.transit.RaptorTransfer;

import java.time.ZonedDateTime;
import java.util.Collection;

public class RaptorRequestMapper {

    public static RaptorRequest<TripSchedule> mapRequest(
            RoutingRequest request,
            ZonedDateTime startOfTime,
            Collection<? extends RaptorTransfer> accessPaths,
            Collection<? extends RaptorTransfer> egressPaths
    ) {
        RaptorRequestBuilder<TripSchedule> builder = new RaptorRequestBuilder<>();

        int time = DateMapper.secondsSinceStartOfTime(
                startOfTime,
                request.getDateTime().toInstant()
        );

        if (request.arriveBy) {
            builder.searchParams().latestArrivalTime(time);
        }
        else {
            builder.searchParams().earliestDepartureTime(time);
        }
        if(request.maxTransfers != null) {
            builder.searchParams().maxNumberOfTransfers(request.maxTransfers);
        }

        builder
                .profile(RaptorProfile.MULTI_CRITERIA)
                .enableOptimization(Optimization.PARETO_CHECK_AGAINST_DESTINATION)
                .slackProvider(new SlackProvider(
                        request.transferSlack,
                        request.boardSlack,
                        request.boardSlackForMode,
                        request.alightSlack,
                        request.alightSlackForMode
                ));

        builder
                .searchParams()
                .searchWindow(request.searchWindow)
                .addAccessPaths(accessPaths)
                .addEgressPaths(egressPaths)
                .timetableEnabled(true);

        builder.mcCostFactors()
                .waitReluctanceFactor(request.waitReluctance);

        if(request.modes.accessMode == StreetMode.WALK) {
            builder.mcCostFactors()
                    .walkReluctanceFactor(request.walkReluctance)
                    .boardCost(request.walkBoardCost)
                    .transferCost(request.transferCost);
        }
        else if(request.modes.accessMode == StreetMode.BIKE) {
            // TODO OTP2 - Is it ok to make bike the same as transit (factor=1.0)
            builder.mcCostFactors()
                    .walkReluctanceFactor(1.0)
                    .boardCost(request.bikeBoardCost);
        }

        return builder.build();
    }
}
