package com.circleguard.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BuildingService, FloorService, AccessPointService and the older SpatialService facade. */
class SpatialServicesTest {

    private BuildingRepository buildings;
    private FloorRepository floors;
    private AccessPointRepository accessPoints;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        buildings = mock(BuildingRepository.class);
        floors = mock(FloorRepository.class);
        accessPoints = mock(AccessPointRepository.class);
        when(buildings.save(any(Building.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(floors.save(any(Floor.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accessPoints.save(any(AccessPoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void buildingServiceCreatesListsAndUpdatesBuildings() {
        BuildingService service = new BuildingService(buildings, floors);
        Building existing = Building.builder().id(id).name("Old").code("OLD").build();
        when(buildings.findById(id)).thenReturn(Optional.of(existing));
        when(buildings.findAll()).thenReturn(List.of(existing));

        Building created = service.createBuilding("Library", "LIB", "Main library", 3.4, -76.5, "Calle 5");
        Building updated = service.updateBuilding(id, "Library", "LIB", "Renovated", 3.5, -76.6, "Calle 6");

        assertThat(created.getCode()).isEqualTo("LIB");
        assertThat(created.getLatitude()).isEqualTo(3.4);
        assertThat(created.getAddress()).isEqualTo("Calle 5");
        assertThat(updated.getDescription()).isEqualTo("Renovated");
        assertThat(updated.getLongitude()).isEqualTo(-76.6);
        assertThat(service.getAllBuildings()).containsExactly(existing);
    }

    @Test
    void buildingServiceProtectsBuildingsWithFloors() {
        BuildingService service = new BuildingService(buildings, floors);
        when(floors.findByBuildingId(id)).thenReturn(List.of(Floor.builder().build()));

        assertThatThrownBy(() -> service.deleteBuilding(id)).hasMessageContaining("existing floors");
        verify(buildings, never()).deleteById(id);

        when(floors.findByBuildingId(id)).thenReturn(List.of());
        service.deleteBuilding(id);
        verify(buildings).deleteById(id);

        when(buildings.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateBuilding(id, "n", "c", "d", null, null, null)).hasMessage("Building not found");
    }

    @Test
    void floorServiceAddsListsAndDeletesFloors() {
        FloorService service = new FloorService(buildings, floors, accessPoints);
        Building building = Building.builder().id(id).build();
        UUID floorId = UUID.randomUUID();
        when(buildings.findById(id)).thenReturn(Optional.of(building));
        when(floors.findByBuildingId(id)).thenReturn(List.of());

        Floor floor = service.addFloor(id, 2, "Second");

        assertThat(floor.getBuilding()).isSameAs(building);
        assertThat(floor.getFloorNumber()).isEqualTo(2);
        assertThat(service.getFloorsByBuilding(id)).isEmpty();

        when(accessPoints.findByFloorId(floorId)).thenReturn(List.of(AccessPoint.builder().build()));
        assertThatThrownBy(() -> service.deleteFloor(floorId)).hasMessageContaining("existing access points");
        when(accessPoints.findByFloorId(floorId)).thenReturn(List.of());
        service.deleteFloor(floorId);
        verify(floors).deleteById(floorId);

        when(buildings.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addFloor(id, 3, "Third")).hasMessage("Building not found");
        when(floors.findById(floorId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateFloor(floorId, 1, "x", null)).hasMessage("Floor not found");
    }

    @Test
    void floorServiceUpdatesOnlyTheGivenFields() {
        FloorService service = new FloorService(buildings, floors, accessPoints);
        Floor floor = Floor.builder().floorNumber(1).name("Ground").floorPlanUrl("old.png").build();
        when(floors.findById(id)).thenReturn(Optional.of(floor));

        service.updateFloor(id, 0, "Lobby", "new.png");

        assertThat(floor.getFloorNumber()).isZero();
        assertThat(floor.getName()).isEqualTo("Lobby");
        assertThat(floor.getFloorPlanUrl()).isEqualTo("new.png");
    }

    @Test
    void accessPointServiceManagesAccessPoints() {
        AccessPointService service = new AccessPointService(accessPoints, floors);
        Floor floor = Floor.builder().id(id).build();
        UUID apId = UUID.randomUUID();
        AccessPoint existing = AccessPoint.builder().id(apId).macAddress("old").build();
        when(floors.findById(id)).thenReturn(Optional.of(floor));
        when(accessPoints.findById(apId)).thenReturn(Optional.of(existing));
        when(accessPoints.findByFloorId(id)).thenReturn(List.of(existing));

        AccessPoint registered = service.registerAccessPoint(id, "aa:bb", 1.0, 2.0, "Hall");
        AccessPoint updated = service.updateAccessPoint(apId, "cc:dd", 3.0, 4.0, "Lab");
        service.deleteAccessPoint(apId);

        assertThat(registered.getFloor()).isSameAs(floor);
        assertThat(registered.getCoordinateY()).isEqualTo(2.0);
        assertThat(updated.getMacAddress()).isEqualTo("cc:dd");
        assertThat(updated.getName()).isEqualTo("Lab");
        assertThat(service.getAccessPoint(apId)).contains(existing);
        assertThat(service.getAccessPointsByFloor(id)).containsExactly(existing);
        verify(accessPoints).deleteById(apId);

        UUID unknown = UUID.randomUUID();
        when(floors.findById(unknown)).thenReturn(Optional.empty());
        when(accessPoints.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.registerAccessPoint(unknown, "m", 0.0, 0.0, "n")).hasMessage("Floor not found");
        assertThatThrownBy(() -> service.updateAccessPoint(unknown, "m", 0.0, 0.0, "n")).hasMessage("Access Point not found");
    }

    @Test
    void spatialServiceCoversBuildingsFloorsAndAccessPoints() {
        SpatialService service = new SpatialService(buildings, floors, accessPoints);
        Building building = Building.builder().id(id).build();
        Floor floor = Floor.builder().id(id).floorNumber(1).build();
        AccessPoint ap = AccessPoint.builder().id(id).build();
        when(buildings.findById(id)).thenReturn(Optional.of(building));
        when(floors.findById(id)).thenReturn(Optional.of(floor));
        when(accessPoints.findById(id)).thenReturn(Optional.of(ap));
        when(buildings.findAll()).thenReturn(List.of(building));
        when(floors.findByBuildingId(id)).thenReturn(List.of(floor));
        when(accessPoints.findByFloorId(id)).thenReturn(List.of(ap));

        assertThat(service.createBuilding("Library", "LIB", "d").getCode()).isEqualTo("LIB");
        assertThat(service.addFloor(id, 4, "Fourth").getBuilding()).isSameAs(building);
        assertThat(service.getAllBuildings()).containsExactly(building);
        assertThat(service.getFloorsByBuilding(id)).containsExactly(floor);
        assertThat(service.updateBuilding(id, "New", "NEW", "nd").getName()).isEqualTo("New");
        assertThat(service.updateFloor(id, 5, "Fifth").getFloorNumber()).isEqualTo(5);
        assertThat(service.registerAccessPoint(id, "aa", 1.0, 1.0, "Hall").getFloor()).isSameAs(floor);
        assertThat(service.getAccessPoint(id)).contains(ap);
        assertThat(service.getAccessPointsByFloor(id)).containsExactly(ap);
        assertThat(service.updateAccessPoint(id, "bb", 2.0, 2.0, "Lab").getMacAddress()).isEqualTo("bb");
        service.deleteAccessPoint(id);
        verify(accessPoints).deleteById(id);

        assertThatThrownBy(() -> service.deleteBuilding(id)).hasMessageContaining("existing floors");
        assertThatThrownBy(() -> service.deleteFloor(id)).hasMessageContaining("existing access points");
        when(floors.findByBuildingId(id)).thenReturn(List.of());
        when(accessPoints.findByFloorId(id)).thenReturn(List.of());
        service.deleteBuilding(id);
        service.deleteFloor(id);
        verify(buildings).deleteById(id);
        verify(floors).deleteById(id);

        UUID unknown = UUID.randomUUID();
        when(buildings.findById(unknown)).thenReturn(Optional.empty());
        when(floors.findById(unknown)).thenReturn(Optional.empty());
        when(accessPoints.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addFloor(unknown, 1, "x")).hasMessage("Building not found");
        assertThatThrownBy(() -> service.updateBuilding(unknown, "a", "b", "c")).hasMessage("Building not found");
        assertThatThrownBy(() -> service.updateFloor(unknown, 1, "x")).hasMessage("Floor not found");
        assertThatThrownBy(() -> service.registerAccessPoint(unknown, "m", 0.0, 0.0, "n")).hasMessage("Floor not found");
        assertThatThrownBy(() -> service.updateAccessPoint(unknown, "m", 0.0, 0.0, "n")).hasMessage("Access Point not found");
    }
}
