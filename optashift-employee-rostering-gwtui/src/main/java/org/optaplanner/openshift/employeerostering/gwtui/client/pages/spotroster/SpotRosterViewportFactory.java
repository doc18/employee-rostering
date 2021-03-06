/*
 * Copyright (C) 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.openshift.employeerostering.gwtui.client.pages.spotroster;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.jboss.errai.ioc.client.api.ManagedInstance;
import org.optaplanner.openshift.employeerostering.gwtui.client.pages.Positive2HoursScale;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.grid.CssGridLinesFactory;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.grid.TicksFactory;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.list.ListElementViewPool;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.model.Blob;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.model.Lane;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.model.LinearScale;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.model.SubLane;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.powers.CollisionFreeSubLaneFactory;
import org.optaplanner.openshift.employeerostering.gwtui.client.tenant.TenantStore;
import org.optaplanner.openshift.employeerostering.gwtui.client.util.TimingUtils;
import org.optaplanner.openshift.employeerostering.shared.common.AbstractPersistable;
import org.optaplanner.openshift.employeerostering.shared.employee.Employee;
import org.optaplanner.openshift.employeerostering.shared.roster.view.SpotRosterView;
import org.optaplanner.openshift.employeerostering.shared.shift.view.ShiftView;
import org.optaplanner.openshift.employeerostering.shared.spot.Spot;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Dependent
public class SpotRosterViewportFactory {

    @Inject
    private ListElementViewPool<ShiftBlobView> shiftBlobViewPool;

    @Inject
    private ManagedInstance<ShiftBlobView> shiftBlobViewInstances;

    @Inject
    private TenantStore tenantStore;

    @Inject
    private CssGridLinesFactory cssGridLinesFactory;

    @Inject
    private TicksFactory<LocalDateTime> ticksFactory;

    @Inject
    private TimingUtils timingUtils;

    @Inject
    private CollisionFreeSubLaneFactory conflictFreeSubLanesFactory;

    private Map<Spot, List<ShiftView>> spotRosterModel;

    private LinearScale<LocalDateTime> scale;

    public SpotRosterViewport getViewport(final SpotRosterView spotRosterView) {
        return timingUtils.time("Spot Roster viewport instantiation", () -> {
            shiftBlobViewPool.init(1500L, shiftBlobViewInstances::get); //FIXME: Make maxSize variable

            spotRosterModel = buildSpotRosterModel(spotRosterView);

            scale = new Positive2HoursScale(spotRosterView.getStartDate().atTime(0, 0),
                    spotRosterView.getEndDate().atTime(0, 0));

            final Map<Long, Spot> spotsById = indexById(spotRosterView.getSpotList());
            final Map<Long, Employee> employeesById = indexById(spotRosterView.getEmployeeList());
            final List<Lane<LocalDateTime>> lanes = buildLanes(spotRosterView, spotsById, employeesById);

            return new SpotRosterViewport(tenantStore.getCurrentTenantId(),
                    shiftBlobViewPool::get,
                    scale,
                    cssGridLinesFactory.newWithSteps(2L, 12L),
                    ticksFactory.newTicks(scale, "date-tick", 12L),
                    ticksFactory.newTicks(scale, "time-tick", 2L),
                    lanes,
                    spotsById,
                    employeesById,
                    spotRosterView.getRosterState());
        });
    }

    private Map<Spot, List<ShiftView>> buildSpotRosterModel(final SpotRosterView spotRosterView) {
        return spotRosterView.getSpotList()
                .stream().collect(Collectors.toMap(spot -> spot,
                        spot -> spotRosterView.getSpotIdToShiftViewListMap().getOrDefault(spot.getId(), new ArrayList<>())));
    }

    private List<Lane<LocalDateTime>> buildLanes(final SpotRosterView spotRosterView, Map<Long, Spot> spotsById, Map<Long, Employee> employeesById) {

        return spotRosterModel
                .entrySet()
                .stream()
                .map(e -> new SpotLane(e.getKey(), buildSubLanes(e.getKey(), e.getValue(), spotsById, employeesById)))
                .collect(toList());
    }

    private List<SubLane<LocalDateTime>> buildSubLanes(final Spot spot,
                                                       final List<ShiftView> timeSlotsByShift,
                                                       final Map<Long, Spot> spotsById,
                                                       final Map<Long, Employee> employeesById) {

        //FIXME: Handle overlapping blobs and discover why some TimeSlots are null

        if (timeSlotsByShift.isEmpty()) {
            return new ArrayList<>(singletonList(new SubLane<>()));
        }

        final Stream<Blob<LocalDateTime>> blobs = timeSlotsByShift
                .stream()
                .filter(e -> e != null) //FIXME: Why are there null Time Slots?
                .map(e -> {
                    final ShiftView shiftView = e;
                    final Employee employee = employeesById.get(shiftView.getEmployeeId());
                    return buildShiftBlob(spot, spotsById, employeesById, shiftView, employee);
                });

        return conflictFreeSubLanesFactory.createSubLanes(blobs);
    }

    private ShiftBlob buildShiftBlob(final Spot spot,
                                     final Map<Long, Spot> spotsById,
                                     final Map<Long, Employee> employeesById,
                                     final ShiftView shiftView,
                                     final Employee employee) {
        return new ShiftBlob(scale, spotsById, employeesById, shiftView);
    }

    private <T extends AbstractPersistable> Map<Long, T> indexById(final List<T> abstractPersistables) {
        return abstractPersistables.stream().collect(toMap(AbstractPersistable::getId, identity()));
    }
}
