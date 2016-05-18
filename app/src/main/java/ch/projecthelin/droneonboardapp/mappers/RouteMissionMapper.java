package ch.projecthelin.droneonboardapp.mappers;

import ch.helin.messages.dto.Action;
import ch.helin.messages.dto.way.Position;
import ch.helin.messages.dto.way.RouteDto;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.MissionItem;
import com.o3dr.services.android.lib.drone.mission.item.command.SetServo;
import com.o3dr.services.android.lib.drone.mission.item.command.Takeoff;
import com.o3dr.services.android.lib.drone.mission.item.spatial.DoLandStart;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Land;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;

import javax.inject.Inject;

public class RouteMissionMapper {

    @Inject
    public RouteMissionMapper() {
    }

    public Mission convertToMission(RouteDto route) {
        Mission mission = new Mission();

        Position lastPosition = null;

        for (ch.helin.messages.dto.way.Waypoint waypointDto : route.getWayPoints()) {
            addWayPointToMission(mission, waypointDto);
            AddActionToMissionIfNecessary(mission, waypointDto);
            lastPosition = waypointDto.getPosition();
        }

        addLandActionAtTheEndOfMission(mission, lastPosition);

        return mission;
    }

    private void addLandActionAtTheEndOfMission(Mission mission, Position lastPosition) {
        Land land = new Land();
        land.setCoordinate(new LatLongAlt(lastPosition.getLat(), lastPosition.getLon(), 0));
        mission.addMissionItem(land);
    }

    private void AddActionToMissionIfNecessary(Mission mission, ch.helin.messages.dto.way.Waypoint waypointDto) {
        MissionItem missionAction = null;

        if (waypointDto.getAction() == Action.DROP) {
            missionAction = new SetServo();
        } else if (waypointDto.getAction() == Action.LAND) {
            missionAction = new Land();
        }

        if (missionAction != null) {
            mission.addMissionItem(missionAction);
        }
    }

    private void addWayPointToMission(Mission mission, ch.helin.messages.dto.way.Waypoint waypointDto) {
        Waypoint waypoint = new Waypoint();

        Position position = waypointDto.getPosition();
        LatLongAlt coordinate = new LatLongAlt(position.getLat(), position.getLon(), position.getHeight());
        waypoint.setCoordinate(coordinate);


        mission.addMissionItem(waypoint);
    }

}