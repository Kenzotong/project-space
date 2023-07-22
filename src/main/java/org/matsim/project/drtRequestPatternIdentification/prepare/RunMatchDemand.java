package org.matsim.project.drtRequestPatternIdentification.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
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
        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\Berlin-DRT-random-selection\\berlin_drt_config.xml";
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
        Population population = scenario.getPopulation();

        Map<String, Object> tripInfoMap = DRTPathZoneSequence.drtPathZoneMap(network, population);//得到trip的两个map
        Map<Integer, List<Integer>> tripPathZoneMap = (Map<Integer, List<Integer>>) tripInfoMap.get("tripPathZoneMap");//得到trip和其经过path的map
        Map<Integer, DrtDemand> tripNumberMap = (Map<Integer, DrtDemand>) tripInfoMap.get("tripNumberMap");

        // Getting drt setups
        double alpha = drtConfigGroup.maxTravelTimeAlpha;
        double beta = drtConfigGroup.maxTravelTimeBeta;
        double maxWaitTime = drtConfigGroup.maxWaitTime;
        double stopDuration = drtConfigGroup.stopDuration;

        // Get drt demands
        List<DrtDemand> drtDemands = DrtDemandsList.getDrtDemandsList(network, population);

        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        int numOfMatch = 0;
        double totalTravelTime = 0;
        double pooledTravelTime = 0;
        System.out.println(tripPathZoneMap.size());

        HashSet<Integer> pooledDemand = new HashSet<>();

        //空间上和时间上判断两个行程是否match
        for (int i = 1; i <= tripPathZoneMap.size(); i++){
            List<Integer> supplierPathZoneList = tripPathZoneMap.get(i);
            DrtDemand supplier = tripNumberMap.get(i);//get demand 1 （supplier）
            VrpPathWithTravelData pathO1D1 = VrpPaths.calcAndCreatePath(supplier.fromLink(),supplier.toLink(),supplier.departureTime(),router,travelTime);
//            LeastCostPathCalculator.Path pathO1D1 = router.calcLeastCostPath(supplier.fromLink().getToNode(), supplier.toLink().getFromNode(),supplier.departureTime(), null, null);
            double directTravelTime1 = pathO1D1.getTravelTime();
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
                    VrpPathWithTravelData pathO2D2 = VrpPaths.calcAndCreatePath(demander.fromLink(),demander.toLink(),demander.departureTime(),router,travelTime);
//                    LeastCostPathCalculator.Path pathO2D2 = router.calcLeastCostPath(demander.fromLink().getToNode(), demander.toLink().getFromNode(),demander.departureTime(), null, null);
                    double directTravelTime2 = pathO2D2.getTravelTime();

                    double supplierLatestDepartureTime = supplier.departureTime() + maxWaitTime;
                    double demanderLatestDepartureTime = demander.departureTime() + maxWaitTime;

                    double supplierLatestArrivalTime = supplier.departureTime() + alpha * directTravelTime1 + beta;
                    double demanderLatestArrivalTime = demander.departureTime() + alpha * directTravelTime2 + beta;

                    //判断这两个trip在时间上是否match （o1,o2,d2,d1）
                    //O2的最晚出发时间 > O1出发从O1到O2的到达时间（理想情况）
                    double now = supplier.departureTime() + stopDuration;//supplier是o1和d1，demander是o2和d2
                    VrpPathWithTravelData pathO1O2 = VrpPaths.calcAndCreatePath(supplier.fromLink(),demander.fromLink(),now,router,travelTime);
//                    LeastCostPathCalculator.Path pathO1O2 = router.calcLeastCostPath(supplier.fromLink().getToNode(), demander.fromLink().getFromNode(),now, null, null);
                    double o1o2 = pathO1O2.getTravelTime();
                    double arrivalTimeO2 = now + o1o2; //o2 实际到达时间
                    if (arrivalTimeO2 <= demanderLatestDepartureTime) {
                        now = arrivalTimeO2 + stopDuration;
                        double arrivalTimeD2 = now + directTravelTime2;
                        //o2到d2的时间 <= demander的最晚到达
                        if (arrivalTimeD2 <= demanderLatestArrivalTime) {
                            now = arrivalTimeD2 + stopDuration;
                            VrpPathWithTravelData pathD2D1 = VrpPaths.calcAndCreatePath(demander.toLink(),supplier.toLink(),now,router,travelTime);
//                            LeastCostPathCalculator.Path pathD2D1 = router.calcLeastCostPath(demander.toLink().getToNode(), supplier.toLink().getFromNode(),now, null, null);
                            double d2d1 = pathD2D1.getTravelTime();
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
//        CoordUtils.calcEuclideanDistance(from Coord, toCoord) //计算两点之间的距离 to node到to node

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
