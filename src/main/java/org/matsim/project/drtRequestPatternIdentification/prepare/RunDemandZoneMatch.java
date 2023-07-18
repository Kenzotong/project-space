package org.matsim.project.drtRequestPatternIdentification.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;


//use travel time matrix, but there is a bug from Zone.zone.getId();
@Deprecated
public class RunDemandZoneMatch {
    public static void main(String[] args) {

        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\Vulkaneifel\\vulkaneifel-v1.0-25pct.config.xml";
        if (args.length != 0) {
            configPath = args[0];
        }

        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        DrtConfigGroup drtConfigGroup = multiModeDrtConfig.getModalElements().iterator().next();
        Network network = scenario.getNetwork();
//
//        // Get drt demands
//        List<DrtDemand> drtDemands = DrtDemandsSet.getDrtDemandsSet(config);
//
//        Map<String, Object> tripInfoMap = DRTPathZoneSequence.drtPathZoneMap(config);//得到trip的两个map
//        Map<Integer, List<Integer>> tripPathZoneMap = (Map<Integer, List<Integer>>) tripInfoMap.get("tripPathZoneMap");//得到trip和其经过path的map
//        Map<Integer, DrtDemand> tripNumberMap = (Map<Integer, DrtDemand>) tripInfoMap.get("tripNumberMap");

        ZonePoolingCalculator calculator = new ZonePoolingCalculator(drtConfigGroup, network);
        double shareability = calculator.quantifyDemands(config);

        System.out.println(shareability);

    }
}
