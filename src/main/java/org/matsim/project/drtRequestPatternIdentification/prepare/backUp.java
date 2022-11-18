package org.matsim.project.drtRequestPatternIdentification.prepare;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.TripStructureUtils;

public class backUp {

            for (
    TripStructureUtils.Trip trip1 : drtTripSet) {
        //get drt trip set 2
        for (TripStructureUtils.Trip trip2 : drtTripSet) {

            // get OD link
            // When link id is written in the plan
//                Link fromLink1 = network.getLinks().get(trip1.getOriginActivity().getLinkId());
//                Link toLink1 = network.getLinks().get(trip1.getDestinationActivity().getLinkId());
//                Link fromLink2 = network.getLinks().get(trip2.getOriginActivity().getLinkId());
//                Link toLink2 = network.getLinks().get(trip2.getDestinationActivity().getLinkId());

            // When link id is not provided (coordinate is provided instead)
            Link fromLink1 = NetworkUtils.getNearestLink(network, trip1.getOriginActivity().getCoord());
            Link toLink1 = NetworkUtils.getNearestLink(network, trip1.getDestinationActivity().getCoord());
            Link fromLink2 = NetworkUtils.getNearestLink(network, trip2.getOriginActivity().getCoord());
            Link toLink2 = NetworkUtils.getNearestLink(network, trip2.getDestinationActivity().getCoord());

            //get origin destination time
            double departureTime1 = trip1.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
            double departureTime2 = trip2.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);

            //calculate every route
            //o1 to o2
            VrpPathWithTravelData pathO1ToO2 = VrpPaths.calcAndCreatePath(fromLink1, fromLink2, departureTime1, router, travelTime);
            double tripTimeO1ToO2 = pathO1ToO2.getTravelTime();
            //o2 to o1
            VrpPathWithTravelData pathO2ToO1 = VrpPaths.calcAndCreatePath(fromLink2, fromLink1, departureTime2, router, travelTime);
            double tripTimeO2ToO1 = pathO2ToO1.getTravelTime();
            //o2 to d1
            VrpPathWithTravelData pathO2ToD1 = VrpPaths.calcAndCreatePath(fromLink2, toLink1, departureTime1 + tripTimeO1ToO2, router, travelTime);
            double tripTimeO2ToD1 = pathO2ToD1.getTravelTime();
            //o2 to d2
            VrpPathWithTravelData pathO2ToD2 = VrpPaths.calcAndCreatePath(fromLink2, toLink2, departureTime1 + tripTimeO1ToO2, router, travelTime);
            double tripTimeO2ToD2 = pathO2ToD2.getTravelTime();
            //o1 to d1
            VrpPathWithTravelData pathO1ToD1 = VrpPaths.calcAndCreatePath(fromLink1, toLink1, departureTime2 + tripTimeO2ToO1, router, travelTime);
            double tripTimeO1ToD1 = pathO1ToD1.getTravelTime();
            //o1 to d2
            VrpPathWithTravelData pathO1ToD2 = VrpPaths.calcAndCreatePath(fromLink1, toLink2, departureTime2 + tripTimeO2ToO1, router, travelTime);
            double tripTimeO1ToD2 = pathO1ToD2.getTravelTime();
            //d1 to d2, from plan o1,o2,d1,d2
            VrpPathWithTravelData pathD1ToD2_1 = VrpPaths.calcAndCreatePath(toLink1, toLink2, departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD1, router, travelTime);
            double tripTimeD1ToD2_1 = pathD1ToD2_1.getTravelTime();
            //d2 to d1, from plan o1,o2,d2,d1
            VrpPathWithTravelData pathD2ToD1_1 = VrpPaths.calcAndCreatePath(toLink2, toLink1, departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD2, router, travelTime);
            double tripTimeD2ToD1_1 = pathD2ToD1_1.getTravelTime();
            //d1 to d2, from plan o2,o1,d1,d2
            VrpPathWithTravelData pathD1ToD2_2 = VrpPaths.calcAndCreatePath(toLink1, toLink2, departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD1, router, travelTime);
            double tripTimeD1ToD2_2 = pathD1ToD2_2.getTravelTime();
            //d2 to d1, from plan o2,o1,d2,d1
            VrpPathWithTravelData pathD2ToD1_2 = VrpPaths.calcAndCreatePath(toLink2, toLink1, departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD2, router, travelTime);
            double tripTimeD2ToD1_2 = pathD2ToD1_2.getTravelTime();

            //determine if it is shareable
            //if trip 1 is the same as trip 2
            if (trip1 == trip2) {
                continue;
            }
            //o1,o2,d1,d2
            if (departureTime2 + maxWaitTime > departureTime1 + tripTimeO1ToO2) {
                if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD1) {
                    if (departureTime2 + (alpha * tripTimeO2ToD2) + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD1 + tripTimeD1ToD2_1) {
                        numberOfShare ++;
                        continue;
                    }
                }
            }
            //o1,o2,d2,d1
            if (departureTime2 + maxWaitTime > departureTime1 + tripTimeO1ToO2) {
                if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD2) {
                    if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD2 + tripTimeD2ToD1_1) {
                        numberOfShare ++;
                        continue;
                    }
                }
                //o2,o1,d1,d2
                if (departureTime1 + maxWaitTime > departureTime2 + tripTimeO2ToO1) {
                    if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD1) {
                        if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD1 + tripTimeD1ToD2_2) {
                            numberOfShare ++;
                            continue;
                        }
                    }
                }
                //o2,o1,d2,d1
                if (departureTime1 + maxWaitTime > departureTime2 + tripTimeO2ToO1) {
                    if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD2) {
                        if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD2 + tripTimeD2ToD1_2) {
                            numberOfShare ++;
                        }
                    }
                }
            }
        }
    }


}
