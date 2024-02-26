package org.matsim.project.drtRequestPatternIdentification.prepare.algorithm2;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.project.drtRequestPatternIdentification.basicStructures.DrtDemand;
import org.matsim.project.drtRequestPatternIdentification.prepare.algorithm1.DrtDemandsList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class LinkZoneMap {

//    public static void main(String[] args) {
//        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\New-York-Manhattan\\nyc-drt.config.xml";
//        if (args.length != 0) {
//            configPath = args[0];
//        }
//        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
//        linkZoneMap(config);
//    }

    private static final Logger log = LogManager.getLogger(LinkZoneMap.class);

    public static Map<Id<Link>,Integer> linkZoneMap(Network network, Population population){

        log.info("---------------------------------------");
        log.info("trip average distance is started calculating...");
        double totalDistance = 0;
        List<DrtDemand> drtDemands = DrtDemandsList.getDrtDemandsList(network, population);
//        System.out.println("there are " + drtDemands.size() + " demands");
        //Get the total distance traveled by drt for the scene
        for (DrtDemand demand : drtDemands){
            double distance = CoordUtils.calcEuclideanDistance(demand.fromLink().getToNode().getCoord(), demand.toLink().getToNode().getCoord());//该demand的行程距离
//            System.out.println("this demand is: " + distance + " meters");
            totalDistance += distance;
        }
        double averageDistance = totalDistance / drtDemands.size();
        log.info("trip average distance was successfully created");
        log.info("---------------------------------------");

        // Get the boundary coordinates of the network
        Coord minCoord = getMinCoord(network);
        Coord maxCoord = getMaxCoord(network);

        double zoneSize = averageDistance * 0.1; // 10% of average mileage as size of zone
        if(zoneSize > 1000){
            zoneSize = 1000;
        }
        log.info("zone size of this network is: " + zoneSize + " meters");
        double minX = minCoord.getX();
        double minY = minCoord.getY();
        double maxX = maxCoord.getX();
        double maxY = maxCoord.getY();
        int zoneId = 1;

        Map<Id<Link>,Integer> linkZoneMap = new HashMap<>();//Create a map with Link as the key in the map and the Zone where Link is located as the value

        log.info("---------------------------------------");
        log.info("link zone map is started creating...");
        for (double x = minX; x < maxX; x += zoneSize) {
            for (double y = minY; y < maxY; y += zoneSize) {
                Coord zoneMinCoord = new Coord(x, y);
                Coord zoneMaxCoord = new Coord(x + zoneSize, y + zoneSize);

                //Iterate through all the links, and determine whether the link is in the current zone by the to node of the link, and categorize the links according to the zone.
                for(Link link: network.getLinks().values()){
//                    String linkIdString = link.getId().toString();//linkId as a string
                    Id<Link> linkId = link.getId();//linkId as id class
                    Coord startCoord = link.getToNode().getCoord();
                    if(zoneMaxCoord.getX() >= startCoord.getX() && startCoord.getX() >= zoneMinCoord.getX()
                            && zoneMaxCoord.getY() >= startCoord.getY() && startCoord.getY() >= zoneMinCoord.getY()){
                        linkZoneMap.put(linkId,zoneId);
                    }
                }
                zoneId++;
            }
        }

//        Verify that the map is correctly populated
//        for (Map.Entry<Link, Integer> entry : linkZoneMap.entrySet()) {
//            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
//            System.out.println(linkZoneMap.get(entry.getKey()));
//        }

        log.info("link zone map was successfully created");
        log.info("---------------------------------------");
        return linkZoneMap;
    }

    private static Coord getMinCoord(Network network) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;

        for (Node node : network.getNodes().values()) {
            Coord coord = node.getCoord();
            if (coord.getX() < minX) {
                minX = coord.getX();
            }
            if (coord.getY() < minY) {
                minY = coord.getY();
            }
        }

        return new Coord(minX, minY);
    }

    private static Coord getMaxCoord(Network network) {
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        for (Node node : network.getNodes().values()) {
            Coord coord = node.getCoord();
            if (coord.getX() > maxX) {
                maxX = coord.getX();
            }
            if (coord.getY() > maxY) {
                maxY = coord.getY();
            }
        }

        return new Coord(maxX, maxY);
    }

}
