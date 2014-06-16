/*
 * Copyright (c) 2014 by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.powertac.wpgenco;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.powertac.common.Timeslot;

/**
 * 
 * @author Shashank Pande
 * The following imbalance price model is used:
 * cimbp(t) = csp(t), where is a spot price at hour, 
 * if the actor is in positive imbalance in hour t and no downward regulation is undertaken;
 * 
 * cimbp(t) = cd(t), where cd(t) is a price for downward regulation at hour t, 
 * if the actor is in positive imbalance in hour t and downward regulation is undertaken with
 * cd(t) < csp(t);
 * 
 * cimbn(t) = -csp(t), where cup(t) is a price for upward
 * regulation at hour t, if the actor is in negative imbalance in
 * hour t and no upward regulation is undertaken or cup(t) < csp(t);
 * 
 * cimbn(t) = -cup(t), if the actor is in negative imbalance in hour t and 
 * upward regulation is undertaken with cup(t) > csp(t)
 * 
 * The negative sign means that the balance responsible player
 * is paying the price for imbalance, and the positive sign means
 * that the balance responsible player is getting paid.
 *
 */
public class WindFarmGencoPriceModel {
  
  public static final short NO_REG_EMPLOYED = 0;
  public static final short REG_UP_EMPLOYED = 1;
  public static final short REG_DN_EMPLOYED = -1;
  public static final double GREATER_THAN_ZERO_MULTIPLIER = 2.0;
  public static final double LESS_THAN_ZERO_MULTIPLIER = 0.5;
  public static final double ZERO_TOLERANCE = 0.001;
  
  private Map<Integer, ImbalancePrice> mapHourToImbPrice;
  
  public static class ImbalancePrice {
        
    private double clearingPrice = 0;
    private double regulationUpPrice = 0;
    private double regulationDnPrice = 0;
    private int regStatus = 0; //0 = no reg, 1 = reg up, -1 = reg down
    
    
    public ImbalancePrice(double csp, double cup, double cdn, int rst) {
      this.clearingPrice = csp;
      this.regulationUpPrice = cup;
      this.regulationDnPrice = cdn;
      this.regStatus = rst;
    }

    public int getRegStatus ()
    {
      return regStatus;
    }
    
    public double getRegUpPrice() {
      return this.regulationUpPrice;
    }
    
    public double getRegDnPrice() {
      return this.regulationDnPrice;
    }
    
    public double getClearingPrice() {
      return this.clearingPrice;
    }
    
    public void setPrices(double csp, double cup, double cdn, short rst) {
      this.clearingPrice = csp;
      this.regulationUpPrice = cup;
      this.regulationDnPrice = cdn;
      this.regStatus = rst;
      return;
    }
    
    /**
     * getImbalancePrice: implements the imbalance price model
     * @param imbalanceMW
     * @return imbalance price
     */
    public double getImbalancePrice(double imbalanceMW) {
      boolean overGeneration = (Math.signum(imbalanceMW) == 1);
      boolean underGeneration = (Math.signum(imbalanceMW) == -1);;
      double imbalancePrice = 0;
      if (overGeneration) {
        if (this.regStatus == REG_DN_EMPLOYED) {
          imbalancePrice = this.regulationDnPrice;
        } else {
          imbalancePrice = this.clearingPrice;
        }        
      } else if (underGeneration) {
        if (this.regStatus == REG_UP_EMPLOYED) {
          imbalancePrice = -this.regulationUpPrice;
        } else {
          imbalancePrice = -this.clearingPrice;
        }
      }
      
      return imbalancePrice;
    } //getImbalancePrice()

  } //class ImbalancePrice
  
  public WindFarmGencoPriceModel() {
    Random randomGen = new Random();
    //TODO: make this configurable
    double csp0max = 50.0; 
    double csp0min = 10.0;
    mapHourToImbPrice = new HashMap<Integer, ImbalancePrice>();
    //Initially randomly generate imbalance prices
    for (int i = 0; i < 24; i++) {
      double csp = Math.random() * (csp0max - csp0min) + csp0min;
      double cdn = csp;
      double cup = csp;
      short regStat = (short) (randomGen.nextInt(3) - 1); // -1, 0, or 1
      if (regStat == REG_UP_EMPLOYED) {
        cup = GREATER_THAN_ZERO_MULTIPLIER * csp;
      } else if (regStat == REG_DN_EMPLOYED) {
        cdn = LESS_THAN_ZERO_MULTIPLIER * csp;
      } 
      ImbalancePrice imbPrice = new ImbalancePrice(csp, cup, cdn, regStat);
      mapHourToImbPrice.put(i, imbPrice);
    } //for i.. (each hour of day)
  } //WindFarmGencoPriceModel()
  
  
  void updatePrices(Timeslot prevTimeSlot, double totalImbalance, double clearingPrice) {
    short regStat = NO_REG_EMPLOYED;
    if (Math.abs(totalImbalance) > ZERO_TOLERANCE) {
      regStat = (short) Math.signum(totalImbalance);
    }
    int hour = prevTimeSlot.getStartInstant().toDateTime().getHourOfDay();
    double cup = clearingPrice;
    double cdn = clearingPrice;
    if (regStat == REG_UP_EMPLOYED) {
      cup = clearingPrice * GREATER_THAN_ZERO_MULTIPLIER;
    } else if (regStat == REG_DN_EMPLOYED) {
      cdn = clearingPrice * LESS_THAN_ZERO_MULTIPLIER;
    } 
    
    //get the ImbalancePrice object
    ImbalancePrice imbP = this.mapHourToImbPrice.get(hour);
    if (imbP != null) {
      imbP.setPrices(clearingPrice, cup, cdn, regStat);
    }  
  } //updatePrices
}
