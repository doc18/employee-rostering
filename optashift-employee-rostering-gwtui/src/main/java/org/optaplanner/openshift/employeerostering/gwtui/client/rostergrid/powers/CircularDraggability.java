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

package org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.powers;

import java.util.function.BiConsumer;

import elemental2.dom.HTMLElement;
import org.jboss.errai.common.client.api.elemental2.IsElement;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.list.ListView;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.model.Blob;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.model.CollisionDetector;
import org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.model.Viewport;

public class CircularDraggability<T, Y extends BlobWithTwin<T, Y>> {

    private Y blob;
    private ListView<Y> blobViews;
    private Viewport<T> viewport;
    private CircularBlobChangeHandler<T, Y> changeHandler;
    private Long blobPositionDisplacementInScreenPixels;

    public void applyFor(final Y blob,
                         final Long blobPositionDisplacementInScreenPixels,
                         final ListView<Y> blobViews,
                         final CollisionDetector<Blob<T>> collisionDetector,
                         final Viewport<T> viewport,
                         final IsElement blobView) {

        this.blob = blob;
        this.blobPositionDisplacementInScreenPixels = blobPositionDisplacementInScreenPixels;
        this.blobViews = blobViews;
        this.viewport = viewport;
        this.changeHandler = new CircularBlobChangeHandler<>(blob, blobViews, collisionDetector, viewport);

        makeDraggable(blobView.getElement(),
                viewport.getGridPixelSizeInScreenPixels().intValue(),
                viewport.decideBasedOnOrientation("y", "x"));
    }

    private native void makeDraggable(final HTMLElement blob,
                                      final int pixelSize,
                                      final String orientation) /*-{

        var that = this;
        var $blob = $wnd.jQuery(blob);

        $blob.draggable({
            addClasses: false,
            cancel: '.blob div',
            axis: orientation,
            grid: [pixelSize, pixelSize],
            stop: function (e, ui) {
                that.@org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.powers.CircularDraggability::onDragEnd(II)(ui.position.top, ui.position.left);
            },
            drag: function (e, ui) {
                that.@org.optaplanner.openshift.employeerostering.gwtui.client.rostergrid.powers.CircularDraggability::onDrag(II)(ui.position.top, ui.position.left);
            },
            scroll: false
        });
    }-*/;

    private boolean onDragEnd(final int top, final int left) {

        final Boolean isCompletelyOffTheGrid =
                blob.getPositionInGridPixels() >= viewport.getSizeInGridPixels() ||
                        blob.getEndPositionInGridPixels() <= 0;

        if (isCompletelyOffTheGrid) {
            blob.getTwin().ifPresent(twin -> twin.setTwin(null));
            blobViews.remove(blob);
        }

        return true;
    }

    private boolean onDrag(final int top, final int left) {
        final Long newPositionInScreenPixels = viewport.decideBasedOnOrientation(top, left).longValue() - blobPositionDisplacementInScreenPixels;
        final Long newPositionInGridPixels = viewport.toGridPixels(newPositionInScreenPixels);

        if (!newPositionInGridPixels.equals(blob.getPositionInGridPixels())) {
            changeHandler.handle(newPositionInGridPixels, blob.getSizeInGridPixels());
        }

        return true;
    }

    public void onDrag(final BiConsumer<Double, CollisionState> onDrag) {
        changeHandler.onChange(onDrag);
    }
}
