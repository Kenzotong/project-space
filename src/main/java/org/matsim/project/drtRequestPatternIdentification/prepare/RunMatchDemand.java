package org.matsim.project.drtRequestPatternIdentification.prepare;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.project.drtRequestPatternIdentification.basicStructures.DrtDemand;
import org.matsim.project.drtRequestPatternIdentification.basicStructures.Tools;
import org.matsim.project.utils.LinkToLinkTravelTimeMatrix;
import java.util.*;

public class RunMatchDemand {
    public static void main(String[] args) {

        // Create scenario based on config file
        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\New-York-Manhattan\\nyc-drt.config.xml";
        if (args.length != 0) {
            configPath = args[0];
        }
        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());

        matchDemand(config);
    }

    public static void matchDemand(Config config){

        Scenario scenario = ScenarioUtils.loadScenario(config);
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        DrtConfigGroup drtConfigGroup = multiModeDrtConfig.getModalElements().iterator().next();
        Population population = scenario.getPopulation();
        Network network = scenario.getNetwork();

        Map<String, Object> tripInfoMap = DRTPathZoneSequence.DRTPathZoneMap(config);//得到trip的两个map
        Map<Integer, List<Integer>> tripPathZoneMap = (Map<Integer, List<Integer>>) tripInfoMap.get("tripPathZoneMap");//得到trip和其经过path的map
        Map<Integer, DrtDemand> tripNumberMap = (Map<Integer, DrtDemand>) tripInfoMap.get("tripNumberMap");



        // Getting drt setups
        double alpha = drtConfigGroup.maxTravelTimeAlpha;
        double beta = drtConfigGroup.maxTravelTimeBeta;
        double maxWaitTime = drtConfigGroup.maxWaitTime;
        double stopDuration = drtConfigGroup.stopDuration;

        // Get drt demands
        List<DrtDemand> drtDemands = DrtTripsSet.getDrtDemandsSet(config);
        System.out.println(drtDemands.size());

        // initialize travelTimeMatrix (based on free speed)
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        Set<Id<Link>> relevantLinks = Tools.collectRelevantLink(drtDemands);
        System.out.println(relevantLinks);
        LinkToLinkTravelTimeMatrix travelTimeMatrix = new LinkToLinkTravelTimeMatrix(network, travelTime, relevantLinks, 0);


        int numberOfMatch = 0;

        //空间上和时间上判断两个行程是否match
        for (int demand1Number = 1; demand1Number <= tripPathZoneMap.size(); demand1Number++){
            List<Integer> demand1PathZoneList = tripPathZoneMap.get(demand1Number);
            DrtDemand demand1 = tripNumberMap.get(demand1Number);//get demand 1
            for (int demand2Number = 1; demand2Number <= tripPathZoneMap.size(); demand2Number++){
                List<Integer> demand2PathZoneList = tripPathZoneMap.get(demand2Number);
                DrtDemand demand2 = tripNumberMap.get(demand2Number);//get demand2
                if(demand1PathZoneList == demand2PathZoneList){
                    continue;
                }
                if(Collections.indexOfSubList(demand1PathZoneList, demand2PathZoneList) != -1){//如果trip1的行程包含trip2的行程 -> 空间上两个trip可以match

                    double directTravelTime1 = travelTimeMatrix.getTravelTime(demand1.fromLink(), demand1.toLink(), demand1.departureTime());
                    double directTravelTime2 = travelTimeMatrix.getTravelTime(demand2.fromLink(), demand2.toLink(), demand2.departureTime());

                    double latestDepartureTime1 = demand1.departureTime() + maxWaitTime;
                    double latestDepartureTime2 = demand2.departureTime() + maxWaitTime;

                    double latestArrivalTime1 = demand1.departureTime() + alpha * directTravelTime1 + beta;
                    double latestArrivalTime2 = demand2.departureTime() + alpha * directTravelTime2 + beta;

                    //判断这两个trip在时间上是否match （o1,o2,d2,d1）
                    //O2的最晚出发时间 > O1出发从O1到O2的到达时间（理想情况）
                    double now = demand1.departureTime() + stopDuration;
                    double o1o2 = travelTimeMatrix.getTravelTime(demand1.fromLink(), demand2.fromLink(), now);
                    double arrivalTimeO2 = now + o1o2; //o2 实际到达时间
                    if (arrivalTimeO2 <= latestDepartureTime2) {
                        now = arrivalTimeO2 + stopDuration;
                        double o2d2 = travelTimeMatrix.getTravelTime(demand2.fromLink(),demand2.toLink(),now);
                        double arrivalTimeD2 = now + o2d2;
                        //o2到d2的时间 <= d2的最晚到达
                        if (arrivalTimeD2 <= latestArrivalTime2) {
                            now = arrivalTimeD2 + stopDuration;
                            double d2d1 = travelTimeMatrix.getTravelTime(demand2.toLink(),demand1.toLink(),now);
                            double arrivalTimeD1 = now + d2d1;
                            //d2到d1的时间 <= d1的最晚到达
                            if (arrivalTimeD1 <= latestArrivalTime1){
                                numberOfMatch++;
                            }
                        }
                    }
                }
            }
        }

        int numberOfTotalPairs = (drtDemands.size() * drtDemands.size()- 1)/2;
        System.out.println("number of total pairs is: " + numberOfTotalPairs);
        System.out.println("number of match is: " + numberOfMatch);
    }
}
