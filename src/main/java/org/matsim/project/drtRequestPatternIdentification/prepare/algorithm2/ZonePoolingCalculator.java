package org.matsim.project.drtRequestPatternIdentification.prepare.algorithm2;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.project.drtRequestPatternIdentification.basicStructures.DrtDemand;
import org.matsim.project.drtRequestPatternIdentification.basicStructures.Tools;
import org.matsim.project.drtRequestPatternIdentification.prepare.DrtDemandsList;
import org.matsim.project.utils.LinkToLinkTravelTimeMatrix;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;


//use travel time matrix, but there is a bug from Zone.zone.getId();
@Deprecated
public class ZonePoolingCalculator {
    private final Network network;
    private final double alpha;
    private final double beta;
    private final double maxWaitTime;
    private final double stopDuration;
    private final TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
    private LinkToLinkTravelTimeMatrix travelTimeMatrix;

    public ZonePoolingCalculator(DrtConfigGroup drtConfigGroup, Network network, LinkToLinkTravelTimeMatrix travelTimeMatrix) {
        this.network = network;
        this.alpha = drtConfigGroup.maxTravelTimeAlpha;
        this.beta = drtConfigGroup.maxTravelTimeBeta;
        this.maxWaitTime = drtConfigGroup.maxWaitTime;
        this.stopDuration = drtConfigGroup.stopDuration;
        this.travelTimeMatrix = travelTimeMatrix;
    }

    public ZonePoolingCalculator(DrtConfigGroup drtConfigGroup, Network network) {
        this.network = network;
        this.alpha = drtConfigGroup.maxTravelTimeAlpha;
        this.beta = drtConfigGroup.maxTravelTimeBeta;
        this.maxWaitTime = drtConfigGroup.maxWaitTime;
        this.stopDuration = drtConfigGroup.stopDuration;
        this.travelTimeMatrix = null;
    }

    public double quantifyDemands(Config config) {

        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();
        Population population = scenario.getPopulation();

        // Get drt demands
        List<DrtDemand> drtDemands = DrtDemandsList.getDrtDemandsList(network, population);

        Map<String, Object> tripInfoMap = DRTPathZoneSequence.drtPathZoneMap(network, population);//得到trip的两个map
        Map<Integer, List<Integer>> tripPathZoneMap = (Map<Integer, List<Integer>>) tripInfoMap.get("tripPathZoneMap");//得到trip和其经过path的map
        Map<Integer, DrtDemand> tripNumberMap = (Map<Integer, DrtDemand>) tripInfoMap.get("tripNumberMap");

        // initialize travelTimeMatrix if it is null
        if (travelTimeMatrix == null) {
            Set<Id<Link>> relevantLinks = Tools.collectRelevantLink(drtDemands);
//            System.out.println(relevantLinks.size());
            travelTimeMatrix = new LinkToLinkTravelTimeMatrix(network, travelTime, relevantLinks, 0);
        }

        // Go through each pair of drt demand and calculate share-ability and the total trip length (time).
//        int numOfTrips = drtDemands.size();

        // Initialize other statistics
        int numberOfMatch = 0;

        for (int i = 1; i <= tripPathZoneMap.size(); i++) {
            List<Integer> demand1PathZoneList = tripPathZoneMap.get(i);//获得demand 1的行驶路径
            DrtDemand demand1 = tripNumberMap.get(i);//get demand 1
            double directTravelTime1 = travelTimeMatrix.getTravelTime(demand1.fromLink(), demand1.toLink(), demand1.departureTime());//获得demand 1的直接路程时间
            for (int j = 1; j <= tripPathZoneMap.size(); j++) {
                List<Integer> demand2PathZoneList = tripPathZoneMap.get(j);//获得demand 2的行驶路径
                DrtDemand demand2 = tripNumberMap.get(j);//get demand2
                //去掉自身匹配
                if (demand1 == demand2) {
                    continue;
                }

//                //demand 中的link
//                Id<Link> testDemand1FromLink = demand1.fromLink().getId();
//                Id<Link> testDemand2FromLink = demand2.fromLink().getId();
//                Id<Link> testDemand1ToLink = demand1.toLink().getId();
//                Id<Link> testDemand2ToLink = demand2.toLink().getId();
//                Node node = network.getLinks().get(testDemand2ToLink).getToNode();
//                System.out.println(node);

                if (Collections.indexOfSubList(demand1PathZoneList, demand2PathZoneList) != -1) {//判断trip1的行程是否包含或等于trip2的行程 -> 空间上两个trip可以match

//                    double directTravelTime1 = travelTimeMatrix.getTravelTime(demand1.fromLink(), demand1.toLink(), demand1.departureTime());
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
                        double o2d2 = travelTimeMatrix.getTravelTime(demand2.fromLink(), demand2.toLink(), now);
                        double arrivalTimeD2 = now + o2d2;
                        //o2到d2的时间 <= d2的最晚到达
                        if (arrivalTimeD2 <= latestArrivalTime2) {
                            now = arrivalTimeD2 + stopDuration;
                            double d2d1 = travelTimeMatrix.getTravelTime(demand2.toLink(), demand1.toLink(), now);
                            double arrivalTimeD1 = now + d2d1;
                            //d2到d1的时间 <= d1的最晚到达
                            if (arrivalTimeD1 <= latestArrivalTime1) {
                                numberOfMatch++;
                            }
                        }
                    }
                }
            }
        }
        int numberOfTotalPairs = (drtDemands.size() * drtDemands.size()- 1)/2;
        double shareability = numberOfMatch/numberOfTotalPairs;
        return shareability;
    }
}
