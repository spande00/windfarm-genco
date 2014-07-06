/*
 * Copyright 2011 the original author or authors.
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
import java.util.List;
import org.apache.log4j.Logger;
import org.joda.time.Instant;

import org.powertac.common.Broker;
import org.powertac.common.IdGenerator;
import org.powertac.common.MarketPosition;
import org.powertac.common.Order;
import org.powertac.common.Timeslot;
import org.powertac.common.config.ConfigurableInstance;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.interfaces.BrokerProxy;
import org.powertac.common.state.Domain;
import org.powertac.common.state.StateChange;

/**
 * Represents a producer of power in the transmission domain. Individual models
 * are players on the wholesale side of the Power TAC day-ahead market.
 * 
 * @author jcollins
 */
@Domain
@ConfigurableInstance
public class WindfarmGenco extends Broker
{
  static private Logger log = Logger.getLogger(WindfarmGenco.class.getName());

  // id values are standardized
  @SuppressWarnings("unused")
  private long id = IdGenerator.createId();

  private boolean inOperation = true;

  /** True if this is a renewable source */
  @SuppressWarnings("unused")
  private boolean renewable = true;
  private double carbonEmissionRate = 0.0;

  protected BrokerProxy brokerProxyService;

  private WindForecast windForecast = null;
  private ForecastScenarios forecastScenarios = null;
  private WindFarmGencoPriceModel imbalancePriceModel = null;

  // configured parameters
  @ConfigurableValue(valueType = "String", description = "Location of weather data to be reported")
  private String location = "minneapolis";
  @ConfigurableValue(valueType = "Integer", description = "Number of turbines in the wind farm")
  private int numberOfTurbines = 100;

  @ConfigurableValue(valueType = "Double", description = "ask price for wind farm")
  private double askPrice = 1.0;

  private WindTurbine windTurbine = null;

  /**
   * Constructor to create instance of wind park genco (or windfarm genco)
   * 
   * @param username
   *          user name
   */
  public WindfarmGenco (String username)
  {
    super(username, true, true);
    
    this.windForecast = new WindForecast();
    this.imbalancePriceModel = new WindFarmGencoPriceModel();
    this.windTurbine = new WindTurbine();
  }

  /**
   * Initialize the wind park genco
   * 
   * @param proxy
   * @param randomSeedRepo
   */
  public void init (BrokerProxy proxy)
  {
    log.info("init " + getUsername());
    this.brokerProxyService = proxy;
    forecastScenarios = new ForecastScenarios(this);
  }

  /**
   * Updates this model for the current timeslot, by adjusting capacity,
   * checking for downtime, and creating exogenous commitments.
   */
  public void updateModel (Instant currentTime)
  {
    log.info("Update " + getUsername());

  }

  /**
   * True if plant is currently operating
   */
  public boolean isInOperation ()
  {
    return inOperation;
  }


  /**
   * Current capacity, varies by a mean-reverting random walk.
   */
  double getCurrentCapacity ()
  {
    return this.windTurbine.getNominalCapacity() * this.numberOfTurbines;
  }

  /**
   * Rate at which this plant emits carbon, relative to a coal-fired thermal
   * plant.
   */
  public double getCarbonEmissionRate ()
  {
    return carbonEmissionRate;
  }

  /**
   * Ask price for energy from this plant.
   */
  public double getAskPrice ()
  {
    return askPrice;
  }

  public WindForecast getWindForecast ()
  {
    return this.windForecast;
  }

  public String getLocation ()
  {
    return location;
  }

  /**
   * Generates Orders in the market to sell available capacity. No Orders are
   * submitted if the plant is not in operation.
   */
  public void generateOrders (Instant now, List<Timeslot> openSlots)
  {
    if (!inOperation) {
      log.info("not in operation - no orders");
      return;
    }
    if (openSlots.isEmpty()) {
      return;
    }
    // 1. get forecast error scenarios
    // this is done only once when forecastScenarios is instantiated
    // this happens in the init() function above.

    // 2. get wind speed forecast
    windForecast.refreshWeatherForecast();

    // 3. generate wind speed scenarios (wind forecast + forecast error)
    forecastScenarios.calcWindSpeedForecastScenarios();

    // 4. generate power output scenarios
    forecastScenarios.calcPowerOutputScenarios();
    
    // 5. update imbalance prices for last closed timeslot
    //TODO: get these prices from the powertac server
    double marketClearingPrice = 10 + 40 * Math.random(); //this must be changed
    double totalNetImabalance = (0.5 - Math.random()) * 1000;
    Timeslot prevTimeSlot = new Timeslot(0,now, openSlots.get(0));//??????
    this.imbalancePriceModel.updatePrices(prevTimeSlot, totalNetImabalance, marketClearingPrice);
    

    // 6. run optimization to determine bid quantity for all timeslots
    List<Double> askQuantities = calcAskQuantities(openSlots);

    // 6. generate orders - assume that we have 24 timeslots open
    for (int i = 0; i < openSlots.size(); i++) {
      Timeslot slot = openSlots.get(i);
      double askQuantity = askQuantities.get(i);
      Order offer = new Order(this, slot, -askQuantity, askPrice);
      brokerProxyService.routeMessage(offer);
    }

  } // generateOrders()

  private List<Double> calcAskQuantities (List<Timeslot> openSlots)
  {
    // instantiate a calculator
    double maxCap = this.windTurbine.getNominalCapacity() * this.numberOfTurbines;
    List<Scenario> wpScenarios =
      forecastScenarios.getWindPowerOutputScenarios();
    WindFarmOfferCalculator offerCalc =
      new WindFarmOfferCalculator(maxCap, wpScenarios, this.imbalancePriceModel);
    List<Double> optimalOffers = offerCalc.getOptimalOfferCapacities(openSlots);
    List<Double> askQuantities = new ArrayList<Double>();
    for (int i = 0; i < openSlots.size(); i++) 
    {
      Timeslot slot = openSlots.get(i);
      double desiredOffer = optimalOffers.get(i);
      MarketPosition posn = findMarketPositionByTimeslot(slot);
      double clearedCapacity =  0 ;
      if (posn != null)
      {
        clearedCapacity = posn.getOverallBalance(); //-ve for asks
      }
      desiredOffer += clearedCapacity;
      askQuantities.add(desiredOffer);
    }
    return askQuantities;
  }

  @StateChange
  private void setInOperation (boolean op)
  {
    inOperation = op;
  }

  /**
   * Estimate power output from given wind speed and air density
   * 
   * @param windSpeed
   *          wind speed in m/sec
   * @param airDensity
   *          air density in kg/m^3
   * @return estimated power output in MW
   */
  public double getEstimatedPowerOutput (double windSpeed, double airDensity)
  {
    return this.windTurbine.getEstimatedPowerOutput(windSpeed, airDensity) *
            this.numberOfTurbines;
  }


  /**
   * get air density from air pressure in Pa and temperature in centigrade
   * 
   * @param airPressure
   *          air pressure in Pa
   * @param tempInCentigrade
   *          temperature in centigrade
   * @return air density in kg/m^3
   */
  public static double getDryAirDensity (double airPressure,
                                         double tempInCentigrade)
  {
    double T = tempInCentigrade + 273.15; // temp in deg Kelvin
    double R = 287.05; // Specific gas constant for dry air J/kg.K

    double airDensity = airPressure / (R * T);
    return airDensity;
  }

}
