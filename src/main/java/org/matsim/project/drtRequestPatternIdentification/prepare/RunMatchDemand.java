package org.matsim.project.drtRequestPatternIdentification.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.project.drtRequestPatternIdentification.basicStructures.DrtDemand;

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
        Network network = scenario.getNetwork();

        Map<String, Object> tripInfoMap = DRTPathZoneSequence.drtPathZoneMap(config);//得到trip的两个map
        Map<Integer, List<Integer>> tripPathZoneMap = (Map<Integer, List<Integer>>) tripInfoMap.get("tripPathZoneMap");//得到trip和其经过path的map
        Map<Integer, DrtDemand> tripNumberMap = (Map<Integer, DrtDemand>) tripInfoMap.get("tripNumberMap");

        // Getting drt setups
        double alpha = drtConfigGroup.maxTravelTimeAlpha;
        double beta = drtConfigGroup.maxTravelTimeBeta;
        double maxWaitTime = drtConfigGroup.maxWaitTime;
        double stopDuration = drtConfigGroup.stopDuration;

//        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();
//        List<DrtDemand> drtDemands = new ArrayList<>();
//        for (Person person : population.getPersons().values()) {
//            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
//            for (TripStructureUtils.Trip trip : trips) {
//                if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(TransportMode.drt)) {
//                    double departureTime = trip.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
//                    Link fromLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
//                    Link toLink = network.getLinks().get(trip.getDestinationActivity().getLinkId());
//                    drtDemands.add(new DrtDemand(person.getId().toString(), fromLink, toLink, departureTime));
//                }
//            }
//        }


        // Get drt demands
        List<DrtDemand> drtDemands = DrtDemandsSet.getDrtDemandsSet(config);

        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

//        // initialize travelTimeMatrix (based on free speed)
//        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
//        Set<Id<Link>> relevantLinks = Tools.collectRelevantLink(drtDemands);//收集所有trip的起终link Id
//        System.out.println(relevantLinks.size());
//        LinkToLinkTravelTimeMatrix travelTimeMatrix = new LinkToLinkTravelTimeMatrix(network, travelTime, relevantLinks, 0);
//        String a = travelTimeMatrix.getTravelTime(relevantLinks.)
//        System.out.println(a);

        int numOfMatch = 0;
        double totalTravelTime = 0;
        double pooledTravelTime = 0;
        System.out.println(tripPathZoneMap.size());

        HashSet<Integer> pooledDemand = new HashSet<>();

        //空间上和时间上判断两个行程是否match
        for (int i = 1; i <= tripPathZoneMap.size(); i++){
            List<Integer> supplierPathZoneList = tripPathZoneMap.get(i);
            DrtDemand supplier = tripNumberMap.get(i);//get demand 1 （supplier）
            LeastCostPathCalculator.Path pathO1D1 = router.calcLeastCostPath(supplier.fromLink().getToNode(), supplier.toLink().getFromNode(),supplier.departureTime(), null, null);
            double directTravelTime1 = pathO1D1.travelTime;
            totalTravelTime += directTravelTime1;
            for (int j = 1; j <= tripPathZoneMap.size(); j++){
                List<Integer> demand2PathZoneList = tripPathZoneMap.get(j);
                DrtDemand demander = tripNumberMap.get(j);//get demand2 （demander）
                if(supplier == demander){
                    continue;
                }
                //检查demander(短)是否作为demander之前已经被pick up了
                if(pooledDemand.contains(j)){
                    continue;
                }

                if(Collections.indexOfSubList(supplierPathZoneList, demand2PathZoneList) != -1){//判断supplier的行程是否包含demander的行程 -> 空间上两个demand可以match -> 进而再从时间上判断

                    LeastCostPathCalculator.Path pathO2D2 = router.calcLeastCostPath(demander.fromLink().getToNode(), demander.toLink().getFromNode(),demander.departureTime(), null, null);
                    double directTravelTime2 = pathO2D2.travelTime;

//                    VrpPathWithTravelData pathO1D1 = VrpPaths.calcAndCreatePath(demand1.fromLink(), demand1.toLink(), demand1.departureTime(), router, travelTime);
//                    double directTravelTime1 = pathO1D1.getTravelTime();

//                    VrpPathWithTravelData pathO2D2 = VrpPaths.calcAndCreatePath(demand2.fromLink(), demand2.toLink(), demand2.departureTime(), router, travelTime);
//                    double directTravelTime2 = pathO2D2.getTravelTime();

                    double supplierLatestDepartureTime = supplier.departureTime() + maxWaitTime;
                    double demanderLatestDepartureTime = demander.departureTime() + maxWaitTime;

                    double supplierLatestArrivalTime = supplier.departureTime() + alpha * directTravelTime1 + beta;
                    double demanderLatestArrivalTime = demander.departureTime() + alpha * directTravelTime2 + beta;

//                    double a = demand1.departureTime();
//                    //demand 中的link
//                    Id<Link> testDemand1FromLink = demand1.fromLink().getId();
//                    Id<Link> testDemand2FromLink = demand2.fromLink().getId();
//                    Id<Link> testDemand1ToLink = demand1.toLink().getId();
//                    Id<Link> testDemand2ToLink = demand2.toLink().getId();
//                    Node node = network.getLinks().get(testDemand1ToLink).getToNode();
//                    System.out.println(node);
//
//                    //判断是否在relevant link里面
//                    boolean test1 = relevantLinks.contains(testDemand1FromLink);
//                    boolean test2 = relevantLinks.contains(testDemand2FromLink);
//                    boolean test3 = relevantLinks.contains(testDemand1ToLink);
//                    boolean test4 = relevantLinks.contains(testDemand2ToLink);
//
//                    System.out.println(test1 + "==" + test2 + "==" + test3 + "==" + test4 + "==" + counter);
//                    counter++;
//
//                    double directTravelTime1 = travelTimeMatrix.getTravelTime(demand1.fromLink(), demand1.toLink(), demand1.departureTime());
//                    double directTravelTime2 = travelTimeMatrix.getTravelTime(demand2.fromLink(), demand2.toLink(), demand2.departureTime());
//
//                    double latestDepartureTime1 = demand1.departureTime() + maxWaitTime;
//                    double latestDepartureTime2 = demand2.departureTime() + maxWaitTime;
//
//                    double latestArrivalTime1 = demand1.departureTime() + alpha * directTravelTime1 + beta;
//                    double latestArrivalTime2 = demand2.departureTime() + alpha * directTravelTime2 + beta;

                    //判断这两个trip在时间上是否match （o1,o2,d2,d1）
                    //O2的最晚出发时间 > O1出发从O1到O2的到达时间（理想情况）
                    double now = supplier.departureTime() + stopDuration;//supplier是o1和d1，demander是o2和d2
                    LeastCostPathCalculator.Path pathO1O2 = router.calcLeastCostPath(supplier.fromLink().getToNode(), demander.fromLink().getFromNode(),now, null, null);
                    double o1o2 = pathO1O2.travelTime;
                    double arrivalTimeO2 = now + o1o2; //o2 实际到达时间
                    if (arrivalTimeO2 <= demanderLatestDepartureTime) {
                        now = arrivalTimeO2 + stopDuration;
                        double arrivalTimeD2 = now + directTravelTime2;
                        //o2到d2的时间 <= demander的最晚到达
                        if (arrivalTimeD2 <= demanderLatestArrivalTime) {
                            now = arrivalTimeD2 + stopDuration;
                            LeastCostPathCalculator.Path pathD2D1 = router.calcLeastCostPath(demander.toLink().getToNode(), supplier.toLink().getFromNode(),now, null, null);
                            double d2d1 = pathD2D1.travelTime;
                            double arrivalTimeD1 = now + d2d1;
                            //d2到d1的时间 <= d1的最晚到达
                            if (arrivalTimeD1 <= supplierLatestArrivalTime){
                                numOfMatch++;
                                pooledDemand.add(j);//demand j作为demander 2已经被picked up，加入该列表，之后跳过
                                pooledTravelTime += directTravelTime2;//demand 2 (demander)成功拼车，行驶时间计算进pooled的时间中
                                break;
                            }
                        }
                    }
                }
            }
        }

//        int numOfTotalPairs = (drtDemands.size() * drtDemands.size() - 1)/2;
        int numOfTotalDemands = drtDemands.size();
//        double shareRate = (double) numOfMatch / numOfTotalPairs * 100;
        double rateOfSavingCar = (double) numOfMatch / numOfTotalDemands * 100;
        double rateOfSavingTime = pooledTravelTime / totalTravelTime * 100;

//        System.out.println("number of total pairs is: " + numOfTotalPairs);
        System.out.println("number of total demands is: " + numOfTotalDemands);
        System.out.println("number of match is: " + numOfMatch);
        System.out.println("---------------------------------------");
        System.out.println("total travel time is: " + totalTravelTime);
        System.out.println("saving travel time is: " + pooledTravelTime);
//        System.out.println("trip share rate is: " + shareRate + "%");
        System.out.println("---------------------------------------");
        System.out.println("rate of saving car is: " + rateOfSavingCar + "%");
        System.out.println("rate of saving travel time is:" + rateOfSavingTime + "%");


    }
}
