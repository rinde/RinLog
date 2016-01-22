/*
 * Copyright (C) 2013-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.logistics.pdptw.mas.comm;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel.AuctionEvent;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel.EventType;
import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.Model.AbstractModel;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.ui.renderers.PanelRenderer;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;

/**
 *
 * @author Rinde van Lon
 */
public class AuctionPanel
    extends AbstractModel<Parcel>
    implements PanelRenderer {
  static final String SPACE = " ";
  static final String TIME_SEPARATOR = ":";

  static final PeriodFormatter FORMATTER = new PeriodFormatterBuilder()
      .appendDays()
      .appendSeparator(SPACE)
      .minimumPrintedDigits(2)
      .printZeroAlways()
      .appendHours()
      .appendLiteral(TIME_SEPARATOR)
      .appendMinutes()
      .appendLiteral(TIME_SEPARATOR)
      .appendSeconds()
      .toFormatter();
  final AuctionCommModel<?> model;
  Optional<Tree> tree;

  Map<Parcel, TreeItem> parcelItems;

  Optional<Button> collapseButton;
  Optional<Button> scrollButton;
  Optional<Label> statusLabel;

  AuctionPanel(AuctionCommModel<?> m) {
    model = m;
    tree = Optional.absent();

    parcelItems = new LinkedHashMap<>();

    model.getEventAPI().addListener(new Listener() {
      @Override
      public void handleEvent(final Event e) {
        final AuctionEvent ae = (AuctionEvent) e;

        tree.get().getDisplay().asyncExec(new Runnable() {
          @Override
          public void run() {
            if (!parcelItems.containsKey(ae.getParcel())) {
              final TreeItem item = new TreeItem(tree.get(), 0);
              item.setText(ae.getParcel().toString());
              parcelItems.put(ae.getParcel(), item);
            }

            final TreeItem parent = parcelItems.get(ae.getParcel());
            final boolean finish = e.getEventType() == EventType.FINISH_AUCTION;

            final TreeItem item = new TreeItem(parent, 0);
            parent.setExpanded(true);
            item.setText(new String[] {
                ae.getEventType().toString(),
                FORMATTER.print(new Period(0, ae.getTime())),
                finish
                    ? ae.getWinner().get().toString() + " " + ae.getNumBids()
                    : ""
            });

            if (collapseButton.get().getSelection()) {
              parent.setExpanded(!finish);
            }
            if (scrollButton.get().getSelection()) {
              final TreeItem target = parent.getExpanded() ? item : parent;
              tree.get().showItem(target);
              tree.get().select(target);
            }

            final int reauctions =
              model.getNumAuctions() - model.getNumParcels();
            final int perc =
              (int) ((reauctions - model.getNumUnsuccesfulAuctions()
                  - model.getNumFailedAuctions()) / (double) reauctions
                  * 100d);

            statusLabel.get().setText(
              "# parcels: " + model.getNumParcels()
                  + " # ongoing auctions: " + model.getNumberOfOngoingAuctions()
                  + " reauctions: " + reauctions
                  + " (success: " + perc + "%)");

            statusLabel.get().setToolTipText(
              "unsuccessful: " + model.getNumUnsuccesfulAuctions()
                  + " failed: " + model.getNumFailedAuctions());

            statusLabel.get().pack(true);
            statusLabel.get().getParent().redraw();
            statusLabel.get().getParent().layout();
          }
        });

      }
    }, EventType.values());
  }

  @Override
  public void initializePanel(Composite parent) {
    final GridLayout layout = new GridLayout(4, false);
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    parent.setLayout(layout);

    statusLabel = Optional.of(new Label(parent, SWT.NONE));
    statusLabel.get().setText("# ongoing auctions: 0");

    final GridData statusLabelLayouData = new GridData();
    statusLabelLayouData.horizontalSpan = 4;
    statusLabelLayouData.grabExcessHorizontalSpace = true;
    statusLabel.get().setLayoutData(statusLabelLayouData);

    tree = Optional.of(
      new Tree(parent, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL));
    tree.get().setHeaderVisible(true);
    tree.get().setLinesVisible(true);

    final GridData treeLayoutData = new GridData();
    treeLayoutData.horizontalSpan = 4;
    treeLayoutData.grabExcessVerticalSpace = true;
    treeLayoutData.grabExcessHorizontalSpace = true;
    treeLayoutData.verticalAlignment = SWT.FILL;
    treeLayoutData.horizontalAlignment = SWT.FILL;
    tree.get().setLayoutData(treeLayoutData);

    final TreeColumn tc = new TreeColumn(tree.get(), 0);
    tc.setText("Parcel");
    tc.setWidth(150);
    final TreeColumn tc4 = new TreeColumn(tree.get(), 0);
    tc4.setText("Time");
    tc4.setWidth(60);

    final TreeColumn tc3 = new TreeColumn(tree.get(), 0);
    tc3.setText("Winner");
    tc3.setWidth(200);

    collapseButton = Optional.of(new Button(parent, SWT.CHECK));
    collapseButton.get().setText("Auto expand/collapse");
    collapseButton.get().setToolTipText(
      "Automatically expands parcels that are being auctioned, collapses "
          + "parcels for which the auction is over.");
    collapseButton.get().setSelection(true);
    scrollButton = Optional.of(new Button(parent, SWT.CHECK));
    scrollButton.get().setText("Auto scroll");
    scrollButton.get().setToolTipText(
      "Automatically scrolls the view such that the newly added event "
          + "is visible.");
    scrollButton.get().setSelection(true);

    final Button b = new Button(parent, SWT.PUSH);
    b.setText("Expand all");
    b.addSelectionListener(new SelectionListener() {
      @Override
      public void widgetSelected(@Nullable SelectionEvent e) {
        final TreeItem[] items = tree.get().getItems();
        for (final TreeItem item : items) {
          item.setExpanded(true);
        }
      }

      @Override
      public void widgetDefaultSelected(@Nullable SelectionEvent e) {}
    });
    final Button c = new Button(parent, SWT.PUSH);
    c.addSelectionListener(new SelectionListener() {
      @Override
      public void widgetSelected(@Nullable SelectionEvent e) {
        final TreeItem[] items = tree.get().getItems();
        for (final TreeItem item : items) {
          item.setExpanded(false);
        }
      }

      @Override
      public void widgetDefaultSelected(@Nullable SelectionEvent e) {}
    });
    c.setText("Collapse all");

  }

  @Override
  public int preferredSize() {
    return 300;
  }

  @Override
  public int getPreferredPosition() {
    return SWT.LEFT;
  }

  @Override
  public String getName() {
    return "AuctionPanel";
  }

  @Override
  public void render() {

  }

  @Override
  public boolean register(final Parcel element) {
    return true;
  }

  @Override
  public boolean unregister(Parcel element) {
    return false;
  }

  public static Builder builder() {
    return new AutoValue_AuctionPanel_Builder();
  }

  @AutoValue
  public static abstract class Builder
      extends AbstractModelBuilder<AuctionPanel, Parcel> {

    Builder() {
      setDependencies(AuctionCommModel.class);
    }

    @Override
    public AuctionPanel build(DependencyProvider dependencyProvider) {
      return new AuctionPanel(dependencyProvider.get(AuctionCommModel.class));
    }
  }
}
