package org.matsim.project.drtRequestPatternIdentification.prepare;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class caculateShareability {

    public static void main(String[] args) {
        // Create scenario based on config file
        String configPath = "D:/github/project-space/scenarios/vulkaneifel/config.xml";
        if (args.length != 0) {
            configPath = args[0];
        }

        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        DrtConfigGroup drtConfigGroup = multiModeDrtConfig.getModalElements().iterator().next();
        Network network = scenario.getNetwork();

        // Create router (based on free speed)
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        // Getting drt setups
        double alpha = drtConfigGroup.maxTravelTimeAlpha;
        double beta = drtConfigGroup.maxTravelTimeBeta;
        double maxWaitTime = drtConfigGroup.maxWaitTime;
        double stopDuration = drtConfigGroup.stopDuration;
        System.out.println("DRT setups: alpha, beta, maxWaitTime, stopDuration " + alpha + ", " + beta + ", " + maxWaitTime + ", " + stopDuration);

        int numberOfShare = 0;

        List<TripStructureUtils.Trip> drtTripSet = createDrtTripsSet.getDrtTripSet();
        //number of drt trips
        System.out.println(drtTripSet.size());

        //get drt trip set 1
        for (TripStructureUtils.Trip trip1 : drtTripSet) {
            //get drt trip set 2
            for (TripStructureUtils.Trip trip2 : drtTripSet) {

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

        System.out.println("number of pairs is: " + numberOfShare);

    }
}

