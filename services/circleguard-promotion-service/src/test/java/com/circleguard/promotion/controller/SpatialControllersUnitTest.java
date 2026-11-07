package com.circleguard.promotion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.promotion.dto.AccessPointDTO;
import com.circleguard.promotion.dto.BuildingDTO;
import com.circleguard.promotion.dto.FloorDTO;
import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.service.AccessPointService;
import com.circleguard.promotion.service.BuildingService;
import com.circleguard.promotion.service.FloorService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpatialControllersUnitTest {

    private BuildingService buildingService;
    private FloorService floorService;
    private AccessPointService accessPointService;

    private final UUID buildingId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();
    private final UUID apId = UUID.randomUUID();
    private Building building;
    private Floor floor;
    private AccessPoint ap;

    @BeforeEach
    void setUp() {
        buildingService = mock(BuildingService.class);
        floorService = mock(FloorService.class);
        accessPointService = mock(AccessPointService.class);
        building = Building.builder().id(buildingId).name("Library").code("LIB").description("Main")
                .latitude(3.4).longitude(-76.5).address("Calle 5").build();
        floor = Floor.builder().id(floorId).building(building).floorNumber(1).name("Ground").floorPlanUrl("plan.png").build();
        building.setFloors(List.of(floor));
        ap = AccessPoint.builder().id(apId).floor(floor).macAddress("aa:bb").coordinateX(1.0).coordinateY(2.0).name("Hall").build();
    }

    @Test
    void buildingEndpointsMapEntitiesToDtos() {
        BuildingController controller = new BuildingController(buildingService, floorService);
        BuildingDTO request = BuildingDTO.builder().name("Library").code("LIB").description("Main")
                .latitude(3.4).longitude(-76.5).address("Calle 5").build();
        Building bare = Building.builder().id(UUID.randomUUID()).name("Annex").code("ANX").build();
        when(buildingService.createBuilding("Library", "LIB", "Main", 3.4, -76.5, "Calle 5")).thenReturn(building);
        when(buildingService.updateBuilding(buildingId, "Library", "LIB", "Main", 3.4, -76.5, "Calle 5")).thenReturn(building);
        when(buildingService.getAllBuildings()).thenReturn(List.of(building, bare));
        when(floorService.getFloorsByBuilding(buildingId)).thenReturn(List.of(floor, Floor.builder().floorNumber(2).build()));
        when(floorService.addFloor(buildingId, 1, "Ground")).thenReturn(floor);

        BuildingDTO created = controller.createBuilding(request).getBody();
        assertThat(created.getId()).isEqualTo(buildingId);
        assertThat(created.getAddress()).isEqualTo("Calle 5");
        assertThat(created.getFloors()).singleElement().satisfies(f -> {
            assertThat(f.getBuildingId()).isEqualTo(buildingId);
            assertThat(f.getFloorPlanUrl()).isEqualTo("plan.png");
        });
        assertThat(controller.updateBuilding(buildingId, request).getBody().getCode()).isEqualTo("LIB");

        List<BuildingDTO> listed = controller.listBuildings().getBody();
        assertThat(listed).hasSize(2);
        assertThat(listed.get(1).getFloors()).isNull();

        List<FloorDTO> floors = controller.getFloors(buildingId).getBody();
        assertThat(floors).hasSize(2);
        assertThat(floors.get(1).getBuildingId()).isNull();

        FloorDTO added = controller.addFloor(buildingId, FloorDTO.builder().floorNumber(1).name("Ground").build()).getBody();
        assertThat(added.getId()).isEqualTo(floorId);

        assertThat(controller.deleteBuilding(buildingId).getStatusCode().value()).isEqualTo(200);
        verify(buildingService).deleteBuilding(buildingId);
    }

    @Test
    void floorEndpointsMapEntitiesToDtos() {
        FloorController controller = new FloorController(floorService, accessPointService);
        AccessPointDTO apRequest = AccessPointDTO.builder().macAddress("aa:bb").coordinateX(1.0).coordinateY(2.0).name("Hall").build();
        when(accessPointService.registerAccessPoint(floorId, "aa:bb", 1.0, 2.0, "Hall")).thenReturn(ap);
        when(accessPointService.getAccessPointsByFloor(floorId)).thenReturn(List.of(ap, AccessPoint.builder().macAddress("cc").build()));
        when(floorService.updateFloor(floorId, 1, "Ground", "plan.png")).thenReturn(floor);
        when(floorService.updateFloor(floorId, 9, null, null)).thenReturn(Floor.builder().floorNumber(9).build());

        AccessPointDTO added = controller.addAccessPoint(floorId, apRequest).getBody();
        assertThat(added.getFloorId()).isEqualTo(floorId);
        assertThat(added.getCoordinateY()).isEqualTo(2.0);

        List<AccessPointDTO> listed = controller.getAccessPoints(floorId).getBody();
        assertThat(listed).hasSize(2);
        assertThat(listed.get(1).getFloorId()).isNull();

        FloorDTO updated = controller.updateFloor(floorId,
                FloorDTO.builder().floorNumber(1).name("Ground").floorPlanUrl("plan.png").build()).getBody();
        assertThat(updated.getBuildingId()).isEqualTo(buildingId);
        assertThat(controller.updateFloor(floorId, FloorDTO.builder().floorNumber(9).build()).getBody().getBuildingId()).isNull();

        assertThat(controller.deleteFloor(floorId).getStatusCode().value()).isEqualTo(200);
        verify(floorService).deleteFloor(floorId);
    }

    @Test
    void accessPointEndpointsMapEntitiesToDtos() {
        AccessPointController controller = new AccessPointController(accessPointService);
        UUID unknown = UUID.randomUUID();
        when(accessPointService.getAccessPoint(apId)).thenReturn(Optional.of(ap));
        when(accessPointService.getAccessPoint(unknown)).thenReturn(Optional.empty());
        when(accessPointService.updateAccessPoint(apId, "cc:dd", 3.0, 4.0, "Lab"))
                .thenReturn(AccessPoint.builder().id(apId).macAddress("cc:dd").coordinateX(3.0).coordinateY(4.0).name("Lab").build());

        assertThat(controller.getAccessPoint(apId).getBody().getMacAddress()).isEqualTo("aa:bb");
        assertThat(controller.getAccessPoint(unknown).getStatusCode().value()).isEqualTo(404);

        AccessPointDTO updated = controller.updateAccessPoint(apId,
                AccessPointDTO.builder().macAddress("cc:dd").coordinateX(3.0).coordinateY(4.0).name("Lab").build()).getBody();
        assertThat(updated.getName()).isEqualTo("Lab");
        assertThat(updated.getFloorId()).isNull();

        assertThat(controller.deleteAccessPoint(apId).getStatusCode().value()).isEqualTo(200);
        verify(accessPointService).deleteAccessPoint(apId);
    }
}
