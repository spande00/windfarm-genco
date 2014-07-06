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

/**
 *  Represents a wind turbine and associated power generator as one unit
 *  @author Shashank Pande (spande00)
 */
package org.powertac.wpgenco;

//import org.apache.log4j.Logger;
import org.powertac.common.IdGenerator;
import org.powertac.common.config.ConfigurableInstance;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.state.Domain;

@Domain
@ConfigurableInstance
public class WindTurbine
{
  //static private Logger log = Logger.getLogger(WindTurbine.class.getName());
  
  // id values are standardized
  @SuppressWarnings("unused")
  private long id = IdGenerator.createId();
  
  private boolean inOperation = true;
  
  /** efficiency curve */
  private WindTurbineEfficiencyCurve efficiencyCurve = null;
  /** True if this is a renewable source */
  @SuppressWarnings("unused")
  private boolean renewable = true;
  private double carbonEmissionRate = 0.0;  

  @ConfigurableValue(valueType = "Double", description = "Rated Capacity of wind turbine in MW")
  private double turbineCapacity = 1.5;
  
  @ConfigurableValue(valueType = "Double", description = "minimum wind speed at which the windfarm produces power")
  private double cutInSpeed = 4.0; // meters/sec
  
  @ConfigurableValue(valueType = "Double", description = "maximum wind speed at which the windfarm produces power")
  private double cutOutSpeed = 25.0; // meters/sec
  
  @ConfigurableValue(valueType = "Double", description = "minimum wind speed at which the windfarm produces power at its high limit")
  private double maxPowerOutputspeed = 14.0; // meters/sec

  @ConfigurableValue(valueType = "Double", description = "sweep area of turbine in m^2")
  private double sweepAreaOfTurbine = 2391.2; // m^2
  
  public WindTurbine() {
    this.efficiencyCurve = new WindTurbineEfficiencyCurve();
  }
  /**
   * Nominal or mean capacity of plant.
   */
  public double getNominalCapacity ()
  {
    return this.turbineCapacity;
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
    if (windSpeed < cutInSpeed) {
      return 0;
    }
    else if ((windSpeed >= maxPowerOutputspeed) && (windSpeed < cutOutSpeed)) {
      return (this.turbineCapacity);
    }
    else if (windSpeed > this.cutOutSpeed) {
      return 0;
    }
    else {
      double powerOutput = 0;
      double efficiency = efficiencyCurve.getEfficiency(windSpeed);
      powerOutput =
        0.5 * efficiency * sweepAreaOfTurbine * airDensity
                * Math.pow(windSpeed, 3);
      return powerOutput / 1000000; // convert Watts to MW
    }
  }
  
  /**
   * True if turbine is currently operating
   */
  public boolean isInOperation ()
  {
    return inOperation;
  }
  
  /**
   * Rate at which this plant emits carbon, relative to a coal-fired thermal
   * plant.
   */
  public double getCarbonEmissionRate ()
  {
    return carbonEmissionRate;
  }

}
