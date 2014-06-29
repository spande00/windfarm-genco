/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an
 * "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.powertac.wpgenco;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.powertac.common.Timeslot;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This class provides functionality to determine optimal offers from the wind
 * farm for each open timeslot.
 * It also holds data that is required for this computation.
 * 
 * @author spande00 (Shashank Pande)
 * 
 */
public class WindFarmOfferCalculator
{
  private static Logger log = Logger.getLogger(WindFarmOfferCalculator.class);
  private static double stepSize = 0.1; // must be > 0 and less than 0.5

  @Autowired
  TimeslotRepo timeslotRepo;

  private Map<Timeslot, Integer> mapTimeslotToLeadHour =
    new HashMap<Timeslot, Integer>();

  private double maxCapacity = 0; // maximum capacity of windfarm
  private List<Scenario> windfarmOutputScenarios = null;
  private WindFarmGencoPriceModel wfGencoPriceModel = null;

  /**
   * Constructor.
   */
  public WindFarmOfferCalculator (double maxCap, List<Scenario> wpScenarios, WindFarmGencoPriceModel pm)
  {
    this.maxCapacity = maxCap;
    this.windfarmOutputScenarios = wpScenarios;
    for (int i = 0; i < timeslotRepo.enabledTimeslots().size(); i++) {
      Timeslot ts = timeslotRepo.enabledTimeslots().get(i);
      mapTimeslotToLeadHour.put(ts, i + 1);
    }
  }


  public List<Double> getOptimalOfferCapacities (List<Timeslot> openSlots)
  {
    List<Double> offerCaps = new ArrayList<Double>();

    for (Timeslot ts: openSlots) {
      double oc = determineOfferCapacity(ts);
      offerCaps.add(oc);
    }

    return offerCaps;
  }

  /**
   * Determines optimal capacity to submit ask offer
   * 
   * @param ts
   *          timeslot for which the calculation is done.
   * @return optimal capacity
   */
  private double determineOfferCapacity (Timeslot ts)
  {

    Double cmcp = (double) 0; // market clearing price
    Double pimbPrice = (double) 0; // positive imbalance price
    Double nimbPrice = (double) 0; // negative imbalance price

    // get the prices
    boolean pricesAvailable = this.wfGencoPriceModel.getPrices(ts,cmcp, pimbPrice, nimbPrice);

    // at this point we know we have the prices
    double revenue = 0; // we need to maximize this
    double offerCap = 0;
    if (pricesAvailable) {
      for (double currCap = 0; currCap <= maxCapacity; currCap +=
        currCap * stepSize) {
        double currRev = getRevenue(currCap, cmcp, pimbPrice, nimbPrice, ts);
        if (currRev > revenue) {
          revenue = currRev;
          offerCap = currCap;
        }
      }
    } else {
      log.error("market prices are not available for timslot: " + ts);
      offerCap = Math.random() * maxCapacity;
    }

    return offerCap;
  } // calcOfferCapacity()

  private double getRevenue (double pbid, double mcp, double pimbPrice, double nimbPrice,
                             Timeslot ts)
  {
    double mcpRevenue = pbid * mcp;
    // get imbalance revenue - positive revenue indicate profit, -ve revenue
    // loss
    int tiIndex = mapTimeslotToLeadHour.get(ts) - 1;
    if ((tiIndex < 0) || (tiIndex > 23)) {
      return mcpRevenue; // no data to calculate imbalance revenue
    }
    double imbalanceRevenue = 0;
    double negativeImbalance = 0;
    double positiveImbalance = 0;
    for (Scenario powerScenario: windfarmOutputScenarios) {
      double pi = powerScenario.getValueList().get(tiIndex).getValue(); //power for ith scenario
      double prob = powerScenario.getProbability();
      if (pi > pbid) { // negative imbalance
        negativeImbalance += (pi - pbid) * prob;
      }
      else if (pbid > pi) { // positive imbalance
        positiveImbalance += (pbid - pi) * prob;
      }
    }
    imbalanceRevenue = (pimbPrice * positiveImbalance) + (-nimbPrice * negativeImbalance);
    double totalRevenue = mcpRevenue + imbalanceRevenue;
    return totalRevenue;
  } // getRevenue()


} // class WindFarmOfferCalculator
