package com.saristore.service;
import com.saristore.config.DatabaseConfig;import java.math.BigDecimal;import java.sql.*;
public class ReportService {
 public BigDecimal cashCollected(String period)throws SQLException{return scalar("SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE payment_type='CASH' AND "+period);}
 public BigDecimal creditOutstanding()throws SQLException{return scalar("SELECT COALESCE(SUM(current_debt),0) FROM customers");}
 public BigDecimal grossProfit(String period)throws SQLException{return scalar("SELECT COALESCE(SUM((si.unit_price-si.unit_cost)*si.quantity),0) FROM sale_items si JOIN sales s ON s.id=si.sale_id WHERE "+period);}
 private BigDecimal scalar(String sql)throws SQLException{try(Connection c=DatabaseConfig.connect();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getBigDecimal(1);}}
 public static String periodCondition(String period){return switch(period){case "WEEK"->"sale_timestamp >= DATEADD('DAY', -7, CURRENT_TIMESTAMP)";case "MONTH"->"sale_timestamp >= DATEADD('MONTH', -1, CURRENT_TIMESTAMP)";default->"CAST(sale_timestamp AS DATE)=CURRENT_DATE";};}
}