package com.john.northgate.toll.repository.projection;

import java.math.BigDecimal;

public interface LaneTotals {

    Integer getLaneNumber();

    Long getVehicles();

    BigDecimal getRevenue();
}
