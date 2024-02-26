package org.matsim.project.drtRequestPatternIdentification.prepare.algorithm3;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.project.drtRequestPatternIdentification.prepare.DrtTripsSet;

import java.awt.geom.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeometryMatch {

    public static void main(String[] args) {
        // Create scenario based on config file
        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\Berlin\\berlin_drt_config.xml";
        if (args.length != 0) {
            configPath = args[0];
        }

        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        DrtConfigGroup drtConfigGroup = multiModeDrtConfig.getModalElements().iterator().next();
        Network network = scenario.getNetwork();

        // Getting drt setups
        double alpha = drtConfigGroup.maxTravelTimeAlpha;
        double beta = drtConfigGroup.maxTravelTimeBeta;
        double maxWaitTime = drtConfigGroup.maxWaitTime;
        double stopDuration = drtConfigGroup.stopDuration;
        System.out.println("DRT setups: alpha, beta, maxWaitTime, stopDuration " + alpha + ", " + beta + ", " + maxWaitTime + ", " + stopDuration);

        // Get drt trip set
        List<TripStructureUtils.Trip> drtTripSet = DrtTripsSet.getDrtTripsSet(config);
//        List<DrtDemand> drtDemandsSet = DrtDemandsList.getDrtDemandsList(network, population);

        Map<TripStructureUtils.Trip, Line2D> line2DMap = new HashMap<>();

        //Abstract each trip into an OD line and store it in a map
        for (TripStructureUtils.Trip trip : drtTripSet){

            double departureTime = trip.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);

            Link fromLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
            Link toLink = network.getLinks().get(trip.getDestinationActivity().getLinkId());

            Coord fromCoord = fromLink.getToNode().getCoord();
            Coord toCoord = toLink.getToNode().getCoord();

            Point2D.Double fromPoint = new Point2D.Double(fromCoord.getX(), fromCoord.getY());
            Point2D.Double toPoint = new Point2D.Double(toCoord.getX(), toCoord.getY());

            Line2D line = new Line2D.Double(fromPoint,toPoint);


            line2DMap.put(trip, line);
        }

        //Create a two-layer map, with the inner time bin as key and the corresponding rectangle as value; the outer line as key and the inner map as value
        //Map<Integer, Rectangle2D> timeBinAreaMap = new HashMap<>();
        Map<Line2D, Map<Integer, Rectangle2D>> lineTimeBinMap = new HashMap<>();

        int timeBin = 900;
        int width = 500;
        double sumArea1 = 0;
        double sumArea2 = 0;
        double sumLength = 0;

        //Store the area covered by the line formed by all trips according to the time bin
        for (TripStructureUtils.Trip trip : drtTripSet){
            //The time bin is the key and the corresponding rectangle is the value.
            Map<Integer, Rectangle2D> timeBinAreaMap = new HashMap<>();

            Line2D line = line2DMap.get(trip);
            //Get the coordinates of the start and end points of the line abstracted from this trip.
            double x1 = line.getX1();
            double y1 = line.getY1();
            Point2D fromPoint = new Point2D.Double(x1, y1);
            double x2 = line.getX2();
            double y2 = line.getY2();
            Point2D toPoint = new Point2D.Double(x2, y2);

            double length = fromPoint.distance(toPoint);
            sumLength = sumLength + length;

            //Get the angle (in radians) of a line segment
            double angle = Math.atan2(y2 - y1, x2 - x1);
            double absAngle = Math.abs(angle);

            //The time bin that determines the departure time of the line.
            double departureTime = trip.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
            int timeBinDeparture = (int) departureTime / timeBin + 1;
            //Calculate the distance that can be traveled in this time bin at the departure time (this distance is the height of the first rectangle), assuming a speed of 6m/s; first calculate the time
            double speed = 6;
            double travelTimeInFirstTimeBin = timeBinDeparture * timeBin - departureTime;
            double travelDistanceInFirstTimeBin = travelTimeInFirstTimeBin * speed;

            //Calculate arrival time
            double tripLength = fromPoint.distance(toPoint);
            double travelTime = tripLength / speed;
            double arrivalTime = departureTime + travelTime;
            //Determine the time bin in which the line will arrive.
            int timeBinArrival = (int) arrivalTime / timeBin + 1;
            //Calculate the distance that can be traveled in the time bin of arrival time
            double travelTimeInLastTimeBin = arrivalTime - (timeBinArrival - 1) * timeBin;
            double travelDistanceInLastTimeBin = travelTimeInLastTimeBin * speed;
            //Calculate the distance traveled in the time bin
            double travelDistanceInThisTimeBin = timeBin * speed;
            //Calculate the area covered
            double area1 = 2 * width * length;
            sumArea1 = sumArea1 + area1;

/*            //Check length
            double length1 = travelDistanceInLastTimeBin + travelDistanceInFirstTimeBin + (timeBinArrival - timeBinDeparture - 1) * timeBin * speed;
            System.out.println("length is: " + length + ", and length should be: " + length1);*/

            //quadrants to determine the rectangles for each time period.
            //The line segments are in the first and second quadrants
            if (angle >= 0) {
                //Coordinates of the upper-left corner of the first time bin
                double xNew = x1 - width * Math.sin(angle);
                double yNew = y1 + width * Math.cos(angle);
                for (int timeBinNum = timeBinDeparture; timeBinNum <= timeBinArrival; timeBinNum++) {
                    //Calculate the first time bin
                    if (timeBinNum == timeBinDeparture) {
                        //Creating Rectangles
                        Rectangle2D rectangle = new Rectangle2D.Double(xNew, yNew, 2 * width, travelDistanceInFirstTimeBin);
                        //Centered on the upper-left coordinate and rotated by the corresponding angle
                        AffineTransform transform = new AffineTransform();
                        transform.rotate(angle - Math.PI / 2, xNew, yNew);
                        Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                        //Get Area
                        double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
//                        double areaTest = 2 * width * travelDistanceInFirstTimeBin;
//                        System.out.println("this area is: " + area + ", and this area should be: " + areaTest);
                        sumArea2 = sumArea2 + area;
                        //Add the rectangle that corresponds to the first time bin.
                        timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                    }
                    //Calculate the last time bin
                    if (timeBinNum == timeBinArrival){
                        //Length of the hypotenuse
                        double hypotenuse = travelDistanceInFirstTimeBin + travelDistanceInThisTimeBin * (timeBinArrival - timeBinDeparture - 1);
                        //Calculate the coordinates of the final upper-left corner
                        double xTopLift = xNew + hypotenuse * Math.cos(angle);
                        double yTopLift = yNew + hypotenuse * Math.sin(angle);
                        //Creating Rectangles
                        Rectangle2D rectangle = new Rectangle2D.Double(xTopLift, yTopLift, 2 * width, travelDistanceInLastTimeBin);
                        //Centered on the upper-left coordinate and rotated by the corresponding angle
                        AffineTransform transform = new AffineTransform();
                        transform.rotate(angle - Math.PI / 2, xTopLift, yTopLift);
                        Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                        //Get Area
                        double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
                        sumArea2 = sumArea2 + area;
                        //Add the rectangle corresponding to the last time bin
                        timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                    }
                    else {
                        //Length of the hypotenuse
                        double hypotenuse = travelDistanceInFirstTimeBin + travelDistanceInThisTimeBin * (timeBinNum - timeBinDeparture - 1);
                        //Calculate the coordinates of the upper left corner
                        double xTopLift = xNew + hypotenuse * Math.cos(angle);
                        double yTopLift = yNew + hypotenuse * Math.sin(angle);
                        //Creating Rectangles
                        Rectangle2D rectangle = new Rectangle2D.Double(xTopLift, yTopLift, 2 * width, travelDistanceInThisTimeBin);
                        //Centered on the upper-left coordinate and rotated by the corresponding angle
                        AffineTransform transform = new AffineTransform();
                        transform.rotate(angle - Math.PI / 2, xTopLift, yTopLift);
                        Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                        //Get Area
                        double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
                        sumArea2 = sumArea2 + area;
                        //Add the rectangle that corresponds to this time bin
                        timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                    }
                }
            }

            //The line segments are in the third and fourth quadrants
             if (angle < 0){
                 //Coordinates of the upper-left corner of the first time bin
                 double x0 = x1 + travelDistanceInFirstTimeBin * Math.cos(absAngle);
                 double y0 = y1 - travelDistanceInFirstTimeBin * Math.sin(absAngle);
                 double xNew = x0 - width * Math.sin(absAngle);
                 double yNew = y0 - width * Math.cos(absAngle);
                 for (int timeBinNum = timeBinDeparture; timeBinNum <= timeBinArrival; timeBinNum++) {
                     //Calculate the first time bin
                     if (timeBinNum == timeBinDeparture) {
                         //Creating Rectangles
                         Rectangle2D rectangle = new Rectangle2D.Double(xNew, yNew, 2 * width, travelDistanceInFirstTimeBin);
                         //Centered on the upper-left coordinate and rotated by the corresponding angle
                         AffineTransform transform = new AffineTransform();
                         transform.rotate(Math.PI / 2 - absAngle, xNew, yNew);
                         Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                         //Get Area
                         double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
                         sumArea2 = sumArea2 + area;
                         //Add the rectangle that corresponds to the first time bin.
                         timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                     }
                     //Calculate the last time bin
                     if (timeBinNum == timeBinArrival){
                         //Length of the hypotenuse
                         double hypotenuse = travelDistanceInLastTimeBin + travelDistanceInThisTimeBin * (timeBinArrival - timeBinDeparture - 1);
                         //Calculate the coordinates of the final upper-left corner
                         double xTopLift = xNew + hypotenuse * Math.cos(absAngle);
                         double yTopLift = yNew - hypotenuse * Math.sin(absAngle);
                         //Creating Rectangles
                         Rectangle2D rectangle = new Rectangle2D.Double(xTopLift, yTopLift, 2 * width, travelDistanceInLastTimeBin);
                         //Centered on the upper-left coordinate and rotated by the corresponding angle
                         AffineTransform transform = new AffineTransform();
                         transform.rotate(Math.PI / 2 - absAngle, xTopLift, yTopLift);
                         Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                         //Get Area
                         double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
                         sumArea2 = sumArea2 + area;
                         //Add the rectangle corresponding to the last time bin
                         timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                     }
                     else {
                         //Length of the hypotenuse
                         double hypotenuse = travelDistanceInThisTimeBin * (timeBinNum - timeBinDeparture);
                         //Calculate the coordinates of the upper left corner
                         double xTopLift = xNew + hypotenuse * Math.cos(absAngle);
                         double yTopLift = yNew - hypotenuse * Math.sin(absAngle);
                         //Creating Rectangles
                         Rectangle2D rectangle = new Rectangle2D.Double(xTopLift, yTopLift, 2 * width, travelDistanceInThisTimeBin);
                         //Centered on the upper-left coordinate and rotated by the corresponding angle
                         AffineTransform transform = new AffineTransform();
                         transform.rotate(Math.PI / 2 - absAngle, xTopLift, yTopLift);
                         Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                         //Get Area
                         double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
                         sumArea2 = sumArea2 + area;
                         //Add the rectangle that corresponds to this time bin
                         timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                     }
                 }
            }
            lineTimeBinMap.put(line, timeBinAreaMap);
        }
        System.out.println("number of all lines from this time bin map： number： " + lineTimeBinMap.size() + "， should be： " + drtTripSet.size());
        System.out.println("Check that the answers to the two area calculations are consistent: accuracy value: " + sumArea1/1000000 + " km2" + "， approximation: " + sumArea2/1000000 + " km2");

        //Obtain a conversion factor for the area to determine the relationship between the exact and approximate values
        double areaFactor = sumArea1 / sumArea2;
        System.out.println("area factor is: " + areaFactor);

        double sumIntersectionArea = 0;

        //Calculate the area of overlap between two lines in the same time bin.
        for (TripStructureUtils.Trip trip1 : drtTripSet){
            Line2D line1 = line2DMap.get(trip1);
            Map<Integer, Rectangle2D> timeBinAreaMap1 = lineTimeBinMap.get(line1);
//            int trip1Index = drtTripSet.indexOf(trip1);

            //Get the area of the line
            double x1 = line1.getX1();
            double y1 = line1.getY1();
            Point2D fromPoint = new Point2D.Double(x1, y1);
            double x2 = line1.getX2();
            double y2 = line1.getY2();
            Point2D toPoint = new Point2D.Double(x2, y2);

            double length = fromPoint.distance(toPoint);
            double areaOfLine1 = 2 * width * length;

            double intersectionAreaFromLine1 = 0;

            for (TripStructureUtils.Trip trip2 : drtTripSet){
                Line2D line2 = line2DMap.get(trip2);
                Map<Integer, Rectangle2D> timeBinAreaMap2 = lineTimeBinMap.get(line2);
//                int trip2Index = drtTripSet.indexOf(trip2);
                if (trip1 != trip2) {
                    //Calculate the angle between two line segments
                    double angle = getAngleBetweenLines(line1, line2);
                    //Screening two lines in the same direction (angle 0-90°)
                    if (angle <= Math.PI / 2) {

                        //Calculate the overlap area in the same time bin
                        for (int timeBinNum : timeBinAreaMap1.keySet()) {
                            if (timeBinAreaMap2.containsKey(timeBinNum)) {
                                Rectangle2D rectangle2D1 = timeBinAreaMap1.get(timeBinNum);
                                Rectangle2D rectangle2D2 = timeBinAreaMap2.get(timeBinNum);

                                //Calculate the intersection of two rectangles
                                Area area1 = new Area(rectangle2D1);
                                Area area2 = new Area(rectangle2D2);
                                area1.intersect(area2);

                                //Get the approximate area of the intersection
                                double intersectionArea = area1.isEmpty() ? 0 : area1.getBounds2D().getWidth() * area1.getBounds2D().getHeight();
                                //Determine if the sum of the overlapping areas is less than or equal to the area of line1
                                intersectionAreaFromLine1 += intersectionArea * areaFactor * Math.cos(angle);
                                if (intersectionAreaFromLine1 <= areaOfLine1) {
                                    //The approximate area is multiplied by the area factor to obtain the corresponding exact value
                                    sumIntersectionArea += intersectionArea * areaFactor * Math.cos(angle);
                                }
                                else break;

                            }
                        }
                    }
                }
            }
        }
        System.out.println("Intersection area of this scene is： " + sumIntersectionArea/2000000 + " km2");
        System.out.println("Length of all lines is: " + sumLength/1000 + " km");
    }

    //Calculate the angle between two lines
    public static double getAngleBetweenLines (Line2D line1, Line2D line2){
        double x1 = line1.getX2() - line1.getX1();
        double y1 = line1.getY2() - line1.getY1();
        double x2 = line2.getX2() - line2.getX1();
        double y2 = line2.getY2() - line2.getY1();

        double dotProduct = x1 * x2 + y1 * y2;
        double magnitude1 = Math.sqrt(x1 * x1 + y1 * y1);
        double magnitude2 = Math.sqrt(x2 * x2 + y2 *y2);

        double cosTheta = dotProduct / (magnitude1 * magnitude2);

        return  Math.acos(cosTheta);
    }
}
